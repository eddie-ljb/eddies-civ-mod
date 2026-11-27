package de.etiennebader.civ.fabric;

import com.mojang.math.Transformation;
import de.etiennebader.civ.domain.civic.Emblem;
import de.etiennebader.civ.domain.civic.ImageKind;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.fabric.content.CivContent;
import de.etiennebader.civ.fabric.content.EmblemBlock;
import de.etiennebader.civ.fabric.content.PosterDisplays;
import de.etiennebader.civ.server.CivServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

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

    private static final String TAG = "eddies_civ_emblem";
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
    ItemStack stack(EmblemBlock.Kind kind, String style) {
        ItemStack stack = new ItemStack(itemOf(kind));
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, style);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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
            show(level, pos, level.getBlockState(pos), kind, styleOf(stack), civ.get());
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
     * Wo das Bild am Block hängt, im Modell nach Süden (Vorderseite +z) in Pixeln vom Blockursprung, und wie groß es
     * ist (in Blöcken): Flaggentuch am Mast, Wappenschild vor dem Pfosten, Plakat im Rahmen.
     */
    private record Layout(double x, double y, double z, float width, float height) {
    }

    private static Layout layout(EmblemBlock.Kind kind) {
        return switch (kind) {
            case FLAG -> new Layout(9, 10, 8, 0.75F, 0.5F);
            case COAT_OF_ARMS -> new Layout(8, 7.5, 3.2, 0.625F, 0.8125F);
            default -> new Layout(8, 9.5, 9.05, 0.5F, 0.6875F);
        };
    }

    private void show(ServerLevel level, BlockPos pos, BlockState state, EmblemBlock.Kind kind, String style,
                      CivServer civ) {
        remove(level, pos);
        Direction facing = state.getValue(EmblemBlock.FACING);
        Layout layout = layout(kind);
        // Vom Modell (nach Süden) in die Blickrichtung drehen: je Vierteldrehung im Uhrzeigersinn (dx, dz) -> (-dz, dx).
        double dx = layout.x() - 8;
        double dz = layout.z() - 8;
        for (int turns = Direction.SOUTH.get2DDataValue(); turns != facing.get2DDataValue(); turns = (turns + 1) % 4) {
            double rotated = -dz;
            dz = dx;
            dx = rotated;
        }
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        display.setPos(pos.getX() + 0.5 + dx / 16, pos.getY() + layout.y() / 16, pos.getZ() + 0.5 + dz / 16);
        display.setYRot(facing.toYRot());
        ItemStack shown = displayStack(level, kind, style, civ);
        shown.set(DataComponents.CUSTOM_DATA, CustomData.of(tagOf(style)));
        display.getSlot(0).set(shown);
        display.setTransformation(new Transformation(null, null,
                new Vector3f(layout.width(), layout.height(), 1.0F), null));
        display.setViewRange(1.5F);
        display.addTag(TAG);
        display.addTag(tagAt(pos));
        level.addFreshEntity(display);
    }

    private static String tagAt(BlockPos pos) {
        return TAG + "_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    private static CompoundTag tagOf(String style) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, style);
        return tag;
    }

    /**
     * Das Bild des Zeichens als Karte, die ihre Fläche ganz ausfüllt: das hochgeladene PNG (gestreckt) oder eine
     * einfarbige Fläche in der gewählten Farbe bzw. dem Weiß der leeren Art.
     */
    private ItemStack displayStack(ServerLevel level, EmblemBlock.Kind kind, String style, CivServer civ) {
        Optional<Path> images = civ.imagesDirectory();
        if (style.startsWith("image:") && images.isPresent()) {
            Optional<ItemStack> map = host.logoMaps().filled(level, images.get(), style.substring("image:".length()));
            if (map.isPresent()) {
                return map.get();
            }
            // Die Bilddatei ist fort (Upload ersetzt): wie ein leeres Zeichen zeigen statt nichts.
        }
        int rgb = switch (kind) {
            case COAT_OF_ARMS -> 0xC8C8D0;
            case POSTER -> 0xF0EBD8;
            default -> 0xF5F5F5;
        };
        if (style.startsWith("color:")) {
            rgb = DyeColor.byName(style.substring("color:".length()), DyeColor.WHITE).getMapColor().col;
        }
        return host.logoMaps().solid(level, images, rgb);
    }

    private List<Display.ItemDisplay> displaysAt(ServerLevel level, BlockPos pos) {
        String tag = tagAt(pos);
        return level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos).inflate(1.0),
                display -> display.entityTags().contains(tag));
    }

    private void remove(ServerLevel level, BlockPos pos) {
        displaysAt(level, pos).forEach(Display.ItemDisplay::discard);
    }

    // ---- Abbauen und Aufräumen ----

    /** Ein Spieler hat den Block abgebaut: Anzeige weg, Register bereinigen, das Item mit dem Bild fallen lassen. */
    void broken(ServerLevel level, BlockPos pos, Block block, boolean dropItem) {
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
        List<Display.ItemDisplay> shown = displaysAt(level, pos);
        String style = shown.isEmpty() ? "blank" : styleOf(shown.getFirst().getSlot(0).get() == null
                ? ItemStack.EMPTY : shown.getFirst().getSlot(0).get());
        shown.forEach(Display.ItemDisplay::discard);
        if (dropItem) {
            Block.popResource(level, pos, stack(kind, style));
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
                remove(level, pos);
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
