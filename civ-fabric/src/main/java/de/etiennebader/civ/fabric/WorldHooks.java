package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.protection.Hazard;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.CivServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Brücke von den Mixins in die Zivilisation. Mixins können keine Instanzen übergeben bekommen, deshalb
 * hält diese Klasse den laufenden {@link CivServerHost}. Alle Aufrufe kommen vom Serverthread.
 */
public final class WorldHooks {

    private static CivServerHost host;

    private WorldHooks() {
    }

    public static void install(CivServerHost civHost) {
        host = civHost;
    }

    /** Entfernt alle Blöcke aus der Liste, die eine Explosion wegen des Gebietsschutzes nicht zerstören darf. */
    public static void filterExplosion(Level level, List<BlockPos> blocks) {
        Optional<CivServer> civ = current();
        if (civ.isEmpty() || blocks.isEmpty()) {
            return;
        }
        Map<Long, Boolean> allowedByChunk = new HashMap<>();
        blocks.removeIf(pos -> !allowedByChunk.computeIfAbsent(chunkKey(pos),
                key -> civ.get().protection().allows(Hazard.EXPLOSION, Players.position(level, pos))));
    }

    /** Ob Feuer den Block an dieser Stelle verbrennen darf. */
    public static boolean fireMayBurn(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return true;
        }
        return current().map(civ -> civ.protection().allows(Hazard.FIRE, Players.position(level, pos))).orElse(true);
    }

    /**
     * Ob ein Kolben Blöcke über eine Gebietsgrenze bewegen oder dort zerstören würde. Grenze heißt: anderer
     * Eigentümer als am Kolben, Wildnis zählt als eigener Eigentümer.
     */
    public static boolean pistonCrossesBorder(Level level, BlockPos piston, Direction direction, List<BlockPos> moved,
                                              List<BlockPos> destroyed) {
        Optional<CivServer> civ = current();
        if (civ.isEmpty() || level.isClientSide()) {
            return false;
        }
        Map<Long, Optional<PolityId>> owners = new HashMap<>();
        Optional<PolityId> home = ownerAt(civ.get(), level, piston, owners);
        for (BlockPos pos : moved) {
            if (!ownerAt(civ.get(), level, pos, owners).equals(home)
                    || !ownerAt(civ.get(), level, pos.relative(direction), owners).equals(home)) {
                return true;
            }
        }
        for (BlockPos pos : destroyed) {
            if (!ownerAt(civ.get(), level, pos, owners).equals(home)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<PolityId> ownerAt(CivServer civ, Level level, BlockPos pos, Map<Long, Optional<PolityId>> cache) {
        return cache.computeIfAbsent(chunkKey(pos),
                key -> civ.queries().territory().sovereignOf(Players.position(level, pos).chunk()));
    }

    /** Ob die Stelle zum Gebiet einer Gemeinde gehört. */
    public static boolean isClaimed(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return false;
        }
        return current().map(civ -> civ.queries().territory().claimAt(Players.position(level, pos).chunk()).isPresent())
                .orElse(false);
    }

    private static Optional<CivServer> current() {
        return host == null ? Optional.empty() : host.current();
    }

    private static long chunkKey(BlockPos pos) {
        return ((long) (pos.getX() >> 4) << 32) ^ ((pos.getZ() >> 4) & 0xFFFFFFFFL);
    }

}
