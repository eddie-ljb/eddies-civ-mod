package de.etiennebader.civ.fabric;

import com.mojang.math.Transformation;
import de.etiennebader.civ.domain.civic.CustomImage;
import de.etiennebader.civ.domain.civic.ImageKind;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.CivServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Zeigt die über die Webseite hochgeladenen Wappen im Spiel: Über jedem Gemeindehaus schwebt das Wappen der Gemeinde als
 * Karte. Die Anzeige dreht sich dem Betrachter zu, wechselt mit einem neuen Upload von selbst und verschwindet, wenn das
 * Wappen entfällt. Die Karten selbst erzeugt {@link LogoMaps}; wer das Bild in die Hand nehmen will, nutzt
 * {@code /civ logo}.
 */
final class LogoDisplays {

    private static final String TAG = "eddies_civ_logo";
    private static final int INTERVAL = 200;
    private static final double HEIGHT = 2.4;
    private static final float SCALE = 1.6F;

    private final LogoMaps maps;
    private int ticks;

    LogoDisplays(LogoMaps maps) {
        this.maps = maps;
    }

    void tick(MinecraftServer server, CivServer civ) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        Optional<Path> images = civ.imagesDirectory();
        if (images.isEmpty()) {
            return;
        }
        for (PolityView town : civ.queries().polities()) {
            if (town.kind() != PolityKind.TERRITORIAL || town.seat().isEmpty()) {
                continue;
            }
            BlockPosition seat = town.seat().get();
            Identifier dimension = Identifier.tryParse(seat.dimension().value());
            ServerLevel level = dimension == null ? null
                    : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            BlockPos pos = new BlockPos(seat.x(), seat.y(), seat.z());
            if (level == null || !level.isLoaded(pos)) {
                continue;
            }
            Optional<CustomImage> crest = civ.civic().imagesOf(town.id(), ImageKind.WAPPEN).stream().findFirst();
            List<Display.ItemDisplay> shown = level.getEntitiesOfClass(Display.ItemDisplay.class,
                    new AABB(pos).inflate(1.0, 4.0, 1.0), display -> display.entityTags().contains(TAG));
            if (crest.isEmpty()) {
                shown.forEach(Display.ItemDisplay::discard);
                continue;
            }
            Optional<ItemStack> map = maps.map(level, images.get(), crest.get().hash());
            if (map.isEmpty()) {
                continue;
            }
            if (shown.size() == 1 && sameMap(shown.getFirst(), map.get())) {
                continue;
            }
            shown.forEach(Display.ItemDisplay::discard);
            Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
            display.setPos(pos.getX() + 0.5, pos.getY() + HEIGHT, pos.getZ() + 0.5);
            display.getSlot(0).set(map.get());
            display.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
            display.setTransformation(new Transformation(null, null, new Vector3f(SCALE, SCALE, SCALE), null));
            display.setViewRange(1.5F);
            display.addTag(TAG);
            level.addFreshEntity(display);
        }
    }

    private static boolean sameMap(Display.ItemDisplay display, ItemStack wanted) {
        ItemStack current = display.getSlot(0).get();
        return current != null && ItemStack.isSameItemSameComponents(current, wanted);
    }
}
