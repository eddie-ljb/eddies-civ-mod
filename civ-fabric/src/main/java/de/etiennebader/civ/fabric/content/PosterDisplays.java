package de.etiennebader.civ.fabric.content;

import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.campaign.CampaignService;
import de.etiennebader.civ.server.command.Message;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.AABB;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Die Aufschrift über einer Litfaßsäule: ein Text-Display mit Kandidat bzw. Partei, Amt, Programm und Versprechen.
 * Displays tragen ein Kennzeichen, damit sie beim Abbau und beim Auffrischen wiedergefunden werden.
 */
public final class PosterDisplays {

    private static final String TAG = "eddies_civ_poster";
    private static final int ACTIVE_BACKGROUND = 0xC0204020;
    private static final int WAITING_BACKGROUND = 0xA0303030;

    private PosterDisplays() {
    }

    /** Zeigt die Aufschrift an (ersetzt eine alte). */
    public static void show(ServerLevel level, BlockPos pos, CampaignService.PosterLabel label) {
        remove(level, pos);
        Display.TextDisplay display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
        display.setPos(pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5);
        display.setText(text(label));
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.setLineWidth(160);
        display.setBackgroundColor(label.active() ? ACTIVE_BACKGROUND : WAITING_BACKGROUND);
        display.addTag(TAG);
        level.addFreshEntity(display);
    }

    /** Entfernt die Aufschrift an dieser Litfaßsäule. */
    public static void remove(ServerLevel level, BlockPos pos) {
        displaysAt(level, pos).forEach(Display.TextDisplay::discard);
    }

    /**
     * Frischt alle Plakate in geladenen Chunks auf: steht dort keine Litfaßsäule mehr (z. B. nach einer Explosion),
     * wird das Plakat aus dem Register genommen, sonst die Aufschrift erneuert.
     */
    public static void refresh(MinecraftServer server, CivServer civ, Predicate<ServerLevel> stillPoster,
                               java.util.function.BiPredicate<ServerLevel, BlockPos> isPoster) {
        for (CampaignService.PosterLabel label : civ.campaigns().posters()) {
            Optional<ServerLevel> level = level(server, label.position());
            if (level.isEmpty()) {
                continue;
            }
            BlockPos pos = new BlockPos(label.position().x(), label.position().y(), label.position().z());
            if (!level.get().isLoaded(pos) || !stillPoster.test(level.get())) {
                continue;
            }
            if (!isPoster.test(level.get(), pos)) {
                civ.campaigns().posterRemoved(label.position());
                remove(level.get(), pos);
                continue;
            }
            Component text = text(label);
            java.util.List<Display.TextDisplay> shown = displaysAt(level.get(), pos);
            if (shown.isEmpty()) {
                show(level.get(), pos, label);
            } else {
                shown.forEach(display -> {
                    display.setText(text);
                    display.setBackgroundColor(label.active() ? ACTIVE_BACKGROUND : WAITING_BACKGROUND);
                });
            }
        }
    }

    private static java.util.List<Display.TextDisplay> displaysAt(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(pos).inflate(0.5, 1.5, 0.5),
                display -> display.entityTags().contains(TAG));
    }

    private static Component text(CampaignService.PosterLabel label) {
        MutableComponent text = Component.empty();
        boolean first = true;
        for (Message line : label.lines()) {
            if (!first) {
                text.append("\n");
            }
            text.append(Components.of(line));
            first = false;
        }
        return text;
    }

    private static Optional<ServerLevel> level(MinecraftServer server, BlockPosition position) {
        Identifier dimension = Identifier.tryParse(position.dimension().value());
        return dimension == null ? Optional.empty()
                : Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)));
    }
}
