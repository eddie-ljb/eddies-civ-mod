package de.etiennebader.civ.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Lädt Chunks, die Planung und Bau der Gemeinden brauchen, auch wenn kein Spieler in der Nähe ist: Das Bauamt prüft
 * Straßen und Grundstücke, die Baufirma setzt Blöcke, der Wegebau legt Wege. Ohne das passierte dort nichts, solange
 * niemand hinschaut.
 *
 * <p>Ein Chunk, der noch nicht geladen ist, wird (auch generiert, falls es ihn noch nicht gibt) geladen, dann sofort
 * benutzt und danach vom Spiel wie üblich wieder entladen. Je Tick darf nur eine begrenzte Zahl Chunks neu geladen
 * werden ({@value #PER_TICK}), damit der Server nicht stockt; was darüber hinaus fehlt, wird kurz danach erneut versucht.
 */
final class ChunkLoader {

    /** Höchstens so viele Chunks je Tick laden (außer erzwungen). */
    static final int PER_TICK = 24;

    private static int budget = PER_TICK;

    private ChunkLoader() {
    }

    /** Am Anfang jedes Serverticks aufrufen. */
    static void startTick() {
        budget = PER_TICK;
    }

    /** Ob der Chunk an dieser Stelle jetzt geladen ist; lädt ihn, wenn das Budget des Ticks es erlaubt. */
    static boolean ensure(ServerLevel level, int blockX, int blockZ) {
        return ensure(level, blockX, blockZ, false);
    }

    /**
     * Wie {@link #ensure(ServerLevel, int, int)}; {@code force} lädt auch über das Budget hinaus, für Arbeiten, die
     * sonst Fortschritt verlören (ein Wegfeld, das als gelegt gezählt, aber nie gesetzt würde).
     */
    static boolean ensure(ServerLevel level, int blockX, int blockZ, boolean force) {
        if (level.isLoaded(new BlockPos(blockX, 64, blockZ))) {
            return true;
        }
        if (!force && budget <= 0) {
            return false;
        }
        budget--;
        return level.getChunk(blockX >> 4, blockZ >> 4, ChunkStatus.FULL, true) != null;
    }

    /** Ob alle Chunks des Rechtecks geladen sind (und lädt sie, soweit das Budget reicht). */
    static boolean ensureArea(ServerLevel level, int minX, int minZ, int maxX, int maxZ, boolean force) {
        boolean all = true;
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                all &= ensure(level, x << 4, z << 4, force);
            }
        }
        return all;
    }
}
