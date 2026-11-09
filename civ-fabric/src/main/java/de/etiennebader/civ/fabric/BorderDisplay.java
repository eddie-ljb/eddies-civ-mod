package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.domain.territory.TerritoryView;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.territory.Relation;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /civ grenzen}: Zeigt einige Sekunden lang Partikel an den Gebietsgrenzen rund um den Spieler.
 * Nur der Spieler selbst sieht sie. Grün umrandet die eigene Gemeinde, rot fremde, weiß den eigenen Chunk
 * in der Wildnis.
 */
final class BorderDisplay {

    private static final int DURATION = 200;
    private static final int INTERVAL = 10;
    private static final int RADIUS = 3;
    private static final int STEP = 2;

    private static final DustParticleOptions OWN = new DustParticleOptions(0x5BE36B, 1.4F);
    private static final DustParticleOptions FOREIGN = new DustParticleOptions(0xFF5040, 1.4F);
    private static final DustParticleOptions NEUTRAL = new DustParticleOptions(0xFFFFFF, 1.0F);

    private static final DustParticleOptions SELECTION = new DustParticleOptions(0xF2C14E, 1.2F);

    /** Was gerade angezeigt wird: Gebietsgrenzen oder, falls gesetzt, der Umriss einer Fläche. */
    private record Display(int remaining, Optional<Area> area) {
    }

    private final Map<UUID, Display> displays = new HashMap<>();
    private int ticks;

    void show(ServerPlayer player) {
        displays.put(player.getUUID(), new Display(DURATION, Optional.empty()));
    }

    /** Zeigt einige Sekunden lang den Umriss einer Fläche, z. B. eines Grundstücks oder einer Auswahl. */
    void showArea(ServerPlayer player, Area area) {
        displays.put(player.getUUID(), new Display(DURATION, Optional.of(area)));
    }

    void tick(MinecraftServer server, CivServer civ) {
        if (displays.isEmpty() || ++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        displays.replaceAll((id, display) -> new Display(display.remaining() - INTERVAL, display.area()));
        displays.values().removeIf(display -> display.remaining() <= 0);
        displays.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        displays.forEach((id, display) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            display.area().ifPresentOrElse(area -> drawArea(player, area), () -> draw(civ, player));
        });
    }

    private static void drawArea(ServerPlayer player, Area area) {
        if (!area.dimension().value().equals(player.level().dimension().identifier().toString())) {
            return;
        }
        double y = player.getY() + 1.2;
        double minX = area.minX();
        double minZ = area.minZ();
        double maxX = area.maxX() + 1.0;
        double maxZ = area.maxZ() + 1.0;
        int stepsX = (int) Math.ceil((maxX - minX) / STEP);
        int stepsZ = (int) Math.ceil((maxZ - minZ) / STEP);
        for (int i = 0; i <= stepsX; i++) {
            double x = Math.min(maxX, minX + i * STEP);
            particle(player, SELECTION, x, y, minZ);
            particle(player, SELECTION, x, y, maxZ);
        }
        for (int i = 0; i <= stepsZ; i++) {
            double z = Math.min(maxZ, minZ + i * STEP);
            particle(player, SELECTION, minX, y, z);
            particle(player, SELECTION, maxX, y, z);
        }
    }

    private static void draw(CivServer civ, ServerPlayer player) {
        TerritoryView territory = civ.queries().territory();
        ChunkCoord center = TerritoryNotifier.chunkOf(player);
        boolean anyClaim = false;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                ChunkCoord chunk = new ChunkCoord(center.dimension(), center.x() + dx, center.z() + dz);
                Optional<PolityId> owner = territory.claimAt(chunk).map(Claim::polity);
                if (owner.isEmpty()) {
                    continue;
                }
                anyClaim = true;
                DustParticleOptions color = civ.territory().relation(Players.ref(player), owner) == Relation.OWN
                        ? OWN : FOREIGN;
                drawEdges(player, territory, chunk, owner, color);
            }
        }
        if (!anyClaim) {
            outline(player, center, NEUTRAL, true, true, true, true);
        }
    }

    /** Zeichnet die Kanten, an denen das Gebiet endet. */
    private static void drawEdges(ServerPlayer player, TerritoryView territory, ChunkCoord chunk,
                                  Optional<PolityId> owner, DustParticleOptions color) {
        boolean north = !ownerAt(territory, chunk, 0, -1).equals(owner);
        boolean south = !ownerAt(territory, chunk, 0, 1).equals(owner);
        boolean west = !ownerAt(territory, chunk, -1, 0).equals(owner);
        boolean east = !ownerAt(territory, chunk, 1, 0).equals(owner);
        outline(player, chunk, color, north, south, west, east);
    }

    private static Optional<PolityId> ownerAt(TerritoryView territory, ChunkCoord chunk, int dx, int dz) {
        return territory.claimAt(new ChunkCoord(chunk.dimension(), chunk.x() + dx, chunk.z() + dz)).map(Claim::polity);
    }

    private static void outline(ServerPlayer player, ChunkCoord chunk, DustParticleOptions color,
                                boolean north, boolean south, boolean west, boolean east) {
        double minX = chunk.x() * 16.0;
        double minZ = chunk.z() * 16.0;
        double y = player.getY() + 1.2;
        for (int i = 0; i <= 16; i += STEP) {
            if (north) {
                particle(player, color, minX + i, y, minZ);
            }
            if (south) {
                particle(player, color, minX + i, y, minZ + 16);
            }
            if (west) {
                particle(player, color, minX, y, minZ + i);
            }
            if (east) {
                particle(player, color, minX + 16, y, minZ + i);
            }
        }
    }

    private static void particle(ServerPlayer player, DustParticleOptions color, double x, double y, double z) {
        player.level().sendParticles(player, color, true, false, x, y, z, 1, 0.0, 0.25, 0.0, 0.0);
    }
}
