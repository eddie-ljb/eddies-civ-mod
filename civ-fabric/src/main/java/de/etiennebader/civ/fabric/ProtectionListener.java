package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.fabric.content.CivContent;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.fabric.screen.ShopScreen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.shop.ShopService;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Setzt den Gebietsschutz im Spiel durch: Abbauen, Setzen, Benutzen von Türen und Behältern sowie
 * Eimer, Feuerzeug, Hacke und ähnliche Werkzeuge werden vor der Ausführung geprüft. Ob erlaubt
 * ist, entscheidet {@link de.etiennebader.civ.server.protection.ProtectionService}.
 *
 * <p>Nur auf dem Server: Der Client kennt keine Gemeinden und lässt alles durch; verweigert der
 * Server, schickt er dem Client den echten Zustand der betroffenen Blöcke zurück.
 *
 * <p>Noch nicht abgedeckt: Explosionen, Feuerausbreitung, Kolben, Mobs und Angriffe auf
 * Rahmen, Rüstungsständer oder Tiere.
 */
public final class ProtectionListener {

    private final CivServerHost host;
    private final CivContent content;

    public ProtectionListener(CivServerHost host, CivContent content) {
        this.host = Objects.requireNonNull(host, "host");
        this.content = Objects.requireNonNull(content, "content");
    }

    public void register() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayer serverPlayer) || allowed(serverPlayer, ProtectedAction.BUILD, level, pos));
        UseBlockCallback.EVENT.register(this::onUseBlock);
        UseItemCallback.EVENT.register(this::onUseItem);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            return host.current()
                    .flatMap(civ -> civ.shops().shopAt(Players.position(level, pos)))
                    .map(shop -> shop.shop().owner().equals(Players.ref(serverPlayer)))
                    .orElse(true);
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
                host.current().ifPresent(civ -> civ.shops().removeAt(Players.position(level, pos))));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> onEntity(player, level, entity, true));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> onEntity(player, level, entity, false));
    }

    private InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = player.getItemInHand(hand);
        // Wer schleicht und etwas in der Hand hält, benutzt nicht den Block, sondern setzt oder verwendet das Item.
        boolean usesBlock = !(player.isSecondaryUseActive() && !stack.isEmpty());

        if (usesBlock && openShop(serverPlayer, level, pos)) {
            return InteractionResult.SUCCESS;
        }
        if (usesBlock && state.is(content.townHall())) {
            host.current().ifPresent(civ -> MenuOpener.townHall(civ, serverPlayer, Players.position(level, pos)));
            return InteractionResult.SUCCESS;
        }
        if (usesBlock && state.is(content.bank())) {
            host.current().ifPresent(civ -> MenuOpener.bank(civ, serverPlayer));
            return InteractionResult.SUCCESS;
        }
        if (usesBlock && state.is(content.market())) {
            host.current().ifPresent(civ -> MenuOpener.market(civ, serverPlayer));
            return InteractionResult.SUCCESS;
        }
        if (usesBlock && level.getBlockEntity(pos) instanceof Container) {
            return result(allowed(serverPlayer, ProtectedAction.OPEN_CONTAINER, level, pos));
        }
        if (usesBlock && isSwitch(state)) {
            return result(allowed(serverPlayer, ProtectedAction.INTERACT, level, pos));
        }
        if (usesBlock && isAdjustable(state)) {
            return result(allowed(serverPlayer, ProtectedAction.BUILD, level, pos));
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem || item instanceof BucketItem) {
            BlockPos target = state.canBeReplaced() ? pos : pos.relative(hit.getDirection());
            if (stack.is(Items.HOPPER) && underForeignShop(serverPlayer, level, target)) {
                return InteractionResult.FAIL;
            }
            return result(allowed(serverPlayer, ProtectedAction.BUILD, level, target));
        }
        if (changesBlocks(stack)) {
            return result(allowed(serverPlayer, ProtectedAction.BUILD, level, pos));
        }
        return InteractionResult.PASS;
    }

    /**
     * Ein Rechtsklick auf die Kiste eines fremden Ladens öffnet den Laden statt der Kiste. Fehlt die Kiste,
     * wird der Laden entfernt.
     */
    private boolean openShop(ServerPlayer player, Level level, BlockPos pos) {
        Optional<CivServer> civ = host.current();
        if (civ.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockPosition position = Players.position(level, pos);
        Optional<ShopService.ShopView> shop = civ.get().shops().shopAt(position);
        if (shop.isEmpty()) {
            return false;
        }
        if (ShopChests.containerAt(serverLevel, pos).isEmpty()) {
            civ.get().shops().removeAt(position);
            return false;
        }
        if (shop.get().shop().owner().equals(Players.ref(player))) {
            return false;
        }
        Feedback.click(player);
        Screens.open(player, new ShopScreen(civ.get(), player, pos, shop.get()));
        return true;
    }

    /**
     * Rüstungsständer, Rahmen, Bilder, Nutztiere und Dorfbewohner sind im fremden Gebiet geschützt: Angreifen gilt
     * als Bauen; Benutzen von Rahmen und Rüstungsständern ebenso, von Tieren als Interagieren. Handeln mit
     * Dorfbewohnern bleibt erlaubt.
     */
    private InteractionResult onEntity(Player player, Level level, Entity entity, boolean attack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        boolean fixture = entity instanceof ArmorStand || entity instanceof HangingEntity;
        boolean creature = entity instanceof Animal || entity instanceof AbstractVillager;
        ProtectedAction action;
        if (fixture) {
            action = ProtectedAction.BUILD;
        } else if (creature && attack) {
            action = ProtectedAction.BUILD;
        } else if (entity instanceof Animal) {
            action = ProtectedAction.INTERACT;
        } else {
            return InteractionResult.PASS;
        }
        return result(allowed(serverPlayer, action, level, entity.blockPosition()));
    }

    /** Eimer wirken auf Flüssigkeiten und lösen dabei kein {@link UseBlockCallback} aus. */
    private InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.getItemInHand(hand).getItem() instanceof BucketItem)) {
            return InteractionResult.PASS;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, true);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return InteractionResult.PASS;
        }
        List<BlockPos> targets = List.of(blockHit.getBlockPos(), blockHit.getBlockPos().relative(blockHit.getDirection()));
        for (BlockPos target : targets) {
            if (!allowed(serverPlayer, ProtectedAction.BUILD, level, target)) {
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * Ein Trichter unter der Kiste eines fremden Ladens würde den Laden leeren – auch in der Wildnis, wo kein
     * Gebietsschutz gilt. Bei Doppelkisten zählen beide Hälften.
     */
    private boolean underForeignShop(ServerPlayer player, Level level, BlockPos target) {
        Optional<CivServer> civ = host.current();
        if (civ.isEmpty()) {
            return false;
        }
        BlockPos above = target.above();
        List<BlockPos> chests = new ArrayList<>(List.of(above));
        BlockState chest = level.getBlockState(above);
        if (chest.getBlock() instanceof ChestBlock && chest.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            chests.add(above.relative(ChestBlock.getConnectedDirection(chest)));
        }
        PlayerRef self = Players.ref(player);
        for (BlockPos candidate : chests) {
            Optional<ShopService.ShopView> shop = civ.get().shops().shopAt(Players.position(level, candidate));
            if (shop.isPresent() && !shop.get().shop().owner().equals(self)) {
                Components.showHint(player, Message.of(MessageKey.SHOP_HOPPER_DENIED), false);
                resync(player, level, target);
                return true;
            }
        }
        return false;
    }

    private boolean allowed(ServerPlayer player, ProtectedAction action, Level level, BlockPos pos) {
        Optional<CivServer> civ = host.current();
        if (civ.isEmpty()) {
            return true;
        }
        Optional<Message> denial = civ.get().protection().check(Players.ref(player), action, Players.position(level, pos));
        if (denial.isEmpty()) {
            return true;
        }
        Components.showHint(player, denial.get(), false);
        resync(player, level, pos);
        return false;
    }

    /** Der Client hat die Handlung womöglich schon vorweggenommen; er bekommt den echten Zustand zurück. */
    private static void resync(ServerPlayer player, Level level, BlockPos pos) {
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        for (BlockPos neighbour : List.of(pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west())) {
            player.connection.send(new ClientboundBlockUpdatePacket(level, neighbour));
        }
        player.containerMenu.sendAllDataToRemote();
    }

    private static InteractionResult result(boolean allowed) {
        return allowed ? InteractionResult.PASS : InteractionResult.FAIL;
    }

    /** Türen, Tore, Knöpfe, Hebel, Betten: Benutzen ändert nichts dauerhaft. */
    private static boolean isSwitch(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof BedBlock;
    }

    /** Blöcke, deren Einstellung beim Benutzen verändert wird, z. B. Verstärker oder Schilder. */
    private static boolean isAdjustable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DiodeBlock || block instanceof NoteBlock || block instanceof SignBlock;
    }

    /**
     * Werkzeuge, die den angeklickten Block verändern (Pfad, Ackerboden, entrindetes Holz) oder etwas
     * daneben entstehen lassen. Werkzeuge sind seit 1.21.5 keine eigenen Klassen mehr, daher die Tags.
     */
    private static boolean changesBlocks(ItemStack stack) {
        Item item = stack.getItem();
        return stack.is(ItemTags.HOES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.AXES)
                || item instanceof FlintAndSteelItem || item instanceof FireChargeItem || item instanceof BoneMealItem;
    }
}
