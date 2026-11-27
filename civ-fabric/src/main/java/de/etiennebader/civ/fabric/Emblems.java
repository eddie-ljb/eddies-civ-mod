package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.civic.Emblem;
import de.etiennebader.civ.domain.civic.ImageKind;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.fabric.content.CivContent;
import de.etiennebader.civ.fabric.content.EmblemBlock;
import de.etiennebader.civ.fabric.content.PosterDisplays;
import de.etiennebader.civ.server.CivServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import de.etiennebader.civ.fabric.content.EmblemBlockEntity;
import de.etiennebader.civ.fabric.content.EmblemSize;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Flagge, Wappen und Wahlplakat als Blöcke (Issue #82): Was darauf zu sehen ist, steht als Kennung am Item, und zwar
 * {@code blank}, {@code color:<farbe>} oder {@code image:<hash>} eines hochgeladenen PNG. Nach dem Setzen zeigt eine
 * Item-Anzeige über dem Block das Banner bzw. die Bildkarte; sie trägt dieselbe Kennung, damit der abgebaute Block
 * wieder dasselbe Item ergibt.
 *
 * <p>Flaggen und Wappen werden im Register der Polity vermerkt (sie stärken die Treue ihrer Dorfbewohner), Wahlplakate
 * und Litfaßsäulen im Plakat-Register des Wahlkampfs (sie werben bei den Wählern).
 */
public final class Emblems {

    private static final String KEY = "emblem";

    private final CivServerHost host;
    private final CivContent content;

    private static Emblems instance;

    public Emblems(CivServerHost host, CivContent content) {
        this.host = host;
        this.content = content;
        instance = this;
    }

    /** Die einzige Instanz, sobald der Mod gestartet ist. */
    static Emblems instance() {
        return instance;
    }

    /** Hängt sich ans Setzen von Blöcken, Abbauen und das regelmäßige Aufräumen. */
    public void register() {
        EmblemBlock.onPlaced(this::placed);
    }

    // ---- Items ----

    /** Ein Item dieser Art mit dieser Kennung (leer, {@code color:<farbe>} oder {@code image:<hash>}). */
    ItemStack stack(EmblemBlock.Kind kind, String style, EmblemSize size) {
        ItemStack stack = new ItemStack(itemOf(kind));
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, style);
        tag.putString("size", size.getSerializedName());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                Component.translatable("eddies-civ-mod.emblem.size", size.name()))));
        return stack;
    }

    private Item itemOf(EmblemBlock.Kind kind) {
        return switch (kind) {
            case FLAG -> content.emblems().flagItem();
            case COAT_OF_ARMS -> content.emblems().coatOfArmsItem();
            default -> content.emblems().posterItem();
        };
    }

    static String styleOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return "blank";
        }
        return data.copyTag().getString(KEY).orElse("blank");
    }

    // ---- Setzen ----

    private void placed(ServerLevel level, BlockPos pos, EmblemBlock.Kind kind, LivingEntity placer, ItemStack stack) {
        Optional<CivServer> civ = host.current();
        if (civ.isEmpty()) {
            return;
        }
        BlockPosition position = Players.position(level, pos);
        if (kind != EmblemBlock.Kind.COLUMN) {
            fill(level, pos, styleOf(stack), civ.get());
        }
        ServerPlayer player = placer instanceof ServerPlayer found ? found : null;
        switch (kind) {
            case FLAG, COAT_OF_ARMS -> {
                var reply = civ.get().civic().emblemPlaced(position, kind == EmblemBlock.Kind.FLAG
                        ? Emblem.Kind.FLAG : Emblem.Kind.COAT_OF_ARMS);
                if (player != null) {
                    Feedback.reply(player, reply);
                }
            }
            case POSTER, COLUMN -> {
                if (player != null) {
                    registerPoster(civ.get(), level, pos, player);
                }
            }
        }
    }

    /** Meldet ein Plakat an und zeigt seine Aufschrift; auch beim Rechtsklick auf ein noch nicht angemeldetes. */
    public void registerPoster(CivServer civ, ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockPosition position = Players.position(level, pos);
        Feedback.reply(player, civ.campaigns().posterPlaced(Players.caller(player), position));
        civ.campaigns().posterLabel(position).ifPresent(label -> PosterDisplays.show(level, pos, label));
    }

    // ---- Anzeige ----

    /**
     * Gibt dem frisch gesetzten Block sein Bild mit: die Kennung und bei einem Bild dessen PNG-Bytes. Den Rest malt der
     * Client auf das Modell.
     */
    private void fill(ServerLevel level, BlockPos pos, String style, CivServer civ) {
        if (!(level.getBlockEntity(pos) instanceof EmblemBlockEntity entity)) {
            return;
        }
        byte[] png = new byte[0];
        if (style.startsWith("image:")) {
            Optional<Path> images = civ.imagesDirectory();
            if (images.isPresent()) {
                try {
                    png = Files.readAllBytes(images.get().resolve(style.substring("image:".length()) + ".png"));
                } catch (IOException | RuntimeException e) {
                    // Die Bilddatei ist fort (Upload ersetzt): wie ein leeres Zeichen zeigen statt nichts.
                    style = "blank";
                }
            } else {
                style = "blank";
            }
        }
        entity.set(style, png);
    }

    // ---- Abbauen und Aufräumen ----

    /** Ein Spieler hat den Block abgebaut: Anzeige weg, Register bereinigen, das Item mit dem Bild fallen lassen. */
    void broken(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity, boolean dropItem) {
        Block block = state.getBlock();
        EmblemBlock.Kind kind = block instanceof EmblemBlock emblem ? emblem.kind() : null;
        if (kind == null) {
            return;
        }
        Optional<CivServer> civ = host.current();
        BlockPosition position = Players.position(level, pos);
        if (kind == EmblemBlock.Kind.FLAG || kind == EmblemBlock.Kind.COAT_OF_ARMS) {
            civ.ifPresent(found -> found.civic().emblemRemoved(position));
        } else {
            civ.ifPresent(found -> found.campaigns().posterRemoved(position));
            PosterDisplays.remove(level, pos);
        }
        if (kind == EmblemBlock.Kind.COLUMN) {
            return;
        }
        String style = blockEntity instanceof EmblemBlockEntity entity ? entity.style() : "blank";
        if (dropItem) {
            Block.popResource(level, pos, stack(kind, style, state.getValue(EmblemBlock.SIZE)));
        }
    }

    /**
     * Räumt Hoheitszeichen auf, deren Block nicht mehr steht (Explosion, Kolben, Welt-Bearbeitung): Eintrag und Anzeige
     * verschwinden, damit tote Flaggen keine Treue mehr bringen.
     */
    void refresh(MinecraftServer server, CivServer civ) {
        for (Emblem emblem : civ.civic().emblems()) {
            BlockPosition position = emblem.position();
            Identifier dimension = Identifier.tryParse(position.dimension().value());
            ServerLevel level = dimension == null ? null
                    : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
            if (level == null || !level.isLoaded(pos)) {
                continue;
            }
            Block expected = emblem.kind() == Emblem.Kind.FLAG ? content.emblems().flag() : content.emblems().coatOfArms();
            if (!level.getBlockState(pos).is(expected)) {
                civ.civic().emblemRemoved(position);
            }
        }
    }

    /** Welches hochgeladene Bild zu dieser Art gehört: Flagge, Wappen und Wahlplakat haben je eine eigene Art. */
    static ImageKind imageKind(EmblemBlock.Kind kind) {
        return switch (kind) {
            case FLAG -> ImageKind.FLAGGE;
            case COAT_OF_ARMS -> ImageKind.WAPPEN;
            default -> ImageKind.WAHLPLAKAT;
        };
    }
}
