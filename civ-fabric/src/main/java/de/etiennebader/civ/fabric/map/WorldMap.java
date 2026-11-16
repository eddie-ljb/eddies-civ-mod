package de.etiennebader.civ.fabric.map;

import de.etiennebader.civ.EddiesCivMod;
import de.etiennebader.civ.fabric.CivServerHost;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.web.WebApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Zeichnet geladene Chunks rund um die Spieler in Kacheln von 32 × 32 Chunks (ein Pixel je Block) für die Weltkarte der
 * Webseite, ähnlich Dynmap. Gezeichnet wird nur, was ohnehin geladen ist; jeder Chunk höchstens alle fünf Minuten neu.
 * Die Kacheln liegen unter {@code <Welt>/data/eddies-civ-mod/map/<Dimension>/<x>_<z>.png}.
 */
public final class WorldMap {

    /** Chunks je Kachelkante. */
    public static final int TILE_CHUNKS = 32;
    private static final int TILE_PIXELS = TILE_CHUNKS * 16;
    private static final int SCAN_INTERVAL = 100;
    private static final int CHUNKS_PER_TICK = 12;
    private static final long REDRAW_MILLIS = 5 * 60_000L;
    private static final int FLUSH_INTERVAL = 200;

    private record ChunkKey(String dimension, int x, int z) {
    }

    private record TileKey(String dimension, int x, int z) {
    }

    private final CivServerHost host;
    private final Deque<ChunkKey> queue = new ArrayDeque<>();
    private final Set<ChunkKey> queued = new HashSet<>();
    private final Map<ChunkKey, Long> drawn = new HashMap<>();
    private final Map<TileKey, int[]> tiles = new HashMap<>();
    private final Set<TileKey> dirty = new HashSet<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "eddies-civ-map");
        thread.setDaemon(true);
        return thread;
    });
    private Path root;
    private long ticks;

    public WorldMap(CivServerHost host) {
        this.host = host;
    }

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        ticks++;
        CivServer civ = host.current().orElse(null);
        if (civ == null || !civ.settings().show().worldMap()) {
            return;
        }
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(EddiesCivMod.MOD_ID).resolve("map");
        if (!folder.equals(root)) {
            root = folder;
            tiles.clear();
            drawn.clear();
            civ.mapDirectory(folder);
        } else if (civ.mapDirectory().isEmpty()) {
            civ.mapDirectory(folder);
        }
        try {
            if (ticks % SCAN_INTERVAL == 0) {
                scan(server, civ.settings().show().mapRadius());
            }
            draw(server);
            if (ticks % FLUSH_INTERVAL == 0) {
                flush();
            }
        } catch (RuntimeException e) {
            EddiesCivMod.LOGGER.warn("Weltkarte: {}", e.getMessage());
        }
    }

    private void scan(MinecraftServer server, int radius) {
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String dimension = player.level().dimension().identifier().toString();
            int cx = player.chunkPosition().x();
            int cz = player.chunkPosition().z();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    ChunkKey key = new ChunkKey(dimension, cx + dx, cz + dz);
                    if (now - drawn.getOrDefault(key, 0L) >= REDRAW_MILLIS && queued.add(key)) {
                        queue.add(key);
                    }
                }
            }
        }
    }

    private void draw(MinecraftServer server) {
        Map<String, ServerLevel> levels = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            levels.put(level.dimension().identifier().toString(), level);
        }
        for (int done = 0; done < CHUNKS_PER_TICK && !queue.isEmpty(); done++) {
            ChunkKey key = queue.poll();
            queued.remove(key);
            ServerLevel level = levels.get(key.dimension());
            LevelChunk chunk = level == null ? null : level.getChunkSource().getChunkNow(key.x(), key.z());
            if (chunk == null) {
                continue;
            }
            render(level, chunk, key);
            drawn.put(key, System.currentTimeMillis());
        }
    }

    private void render(ServerLevel level, LevelChunk chunk, ChunkKey key) {
        TileKey tile = new TileKey(key.dimension(), Math.floorDiv(key.x(), TILE_CHUNKS), Math.floorDiv(key.z(), TILE_CHUNKS));
        int[] pixels = tiles.computeIfAbsent(tile, this::load);
        int offsetX = Math.floorMod(key.x(), TILE_CHUNKS) * 16;
        int offsetZ = Math.floorMod(key.z(), TILE_CHUNKS) * 16;
        boolean nether = level.dimensionType().hasCeiling();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = key.x() * 16;
        int baseZ = key.z() * 16;
        int[] previous = new int[16];
        LevelChunk north = level.getChunkSource().getChunkNow(key.x(), key.z() - 1);
        for (int x = 0; x < 16; x++) {
            previous[x] = north == null ? Integer.MIN_VALUE : surface(level, north, baseX + x, baseZ - 1, nether, pos);
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int height = surface(level, chunk, baseX + x, baseZ + z, nether, pos);
                pos.set(baseX + x, height, baseZ + z);
                BlockState state = chunk.getBlockState(pos);
                MapColor color = state.getMapColor(level, pos);
                int argb;
                if (color == MapColor.NONE) {
                    argb = 0;
                } else {
                    MapColor.Brightness brightness = previous[x] == Integer.MIN_VALUE ? MapColor.Brightness.NORMAL
                            : height > previous[x] ? MapColor.Brightness.HIGH
                            : height < previous[x] ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    if (color == MapColor.WATER) {
                        brightness = MapColor.Brightness.NORMAL;
                    }
                    argb = opaque(color.calculateARGBColor(brightness));
                }
                pixels[(offsetZ + z) * TILE_PIXELS + offsetX + x] = argb;
                previous[x] = height;
            }
        }
        dirty.add(tile);
    }

    /** Höhe des obersten sichtbaren Blocks; im Nether unterhalb der Decke. */
    private static int surface(ServerLevel level, LevelChunk chunk, int x, int z, boolean nether, BlockPos.MutableBlockPos pos) {
        if (!nether) {
            return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        }
        int y = Math.min(level.getMaxY(), 100);
        while (y > level.getMinY()) {
            pos.set(x, y, z);
            if (chunk.getBlockState(pos).isAir()) {
                pos.set(x, y - 1, z);
                if (!chunk.getBlockState(pos).isAir()) {
                    return y - 1;
                }
            }
            y--;
        }
        return level.getMinY();
    }

    /** Deckende Farbe aus {@link MapColor#calculateARGBColor}. */
    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    private int[] load(TileKey tile) {
        Path file = file(tile);
        if (Files.exists(file)) {
            try {
                int[] pixels = Png.decode(Files.readAllBytes(file), TILE_PIXELS, TILE_PIXELS);
                if (pixels != null) {
                    return pixels;
                }
            } catch (IOException e) {
                // neu zeichnen
            }
        }
        return new int[TILE_PIXELS * TILE_PIXELS];
    }

    private Path file(TileKey tile) {
        return root.resolve(WebApi.mapFolder(tile.dimension())).resolve(tile.x() + "_" + tile.z() + ".png");
    }

    private void flush() {
        for (TileKey tile : dirty) {
            int[] copy = tiles.get(tile).clone();
            Path file = file(tile);
            writer.execute(() -> {
                try {
                    Files.createDirectories(file.getParent());
                    Path temp = file.resolveSibling(file.getFileName() + ".tmp");
                    Files.write(temp, Png.encode(TILE_PIXELS, TILE_PIXELS, copy));
                    Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    EddiesCivMod.LOGGER.warn("Kartenkachel {} nicht geschrieben: {}", file, e.getMessage());
                }
            });
        }
        dirty.clear();
        if (tiles.size() > 64) {
            tiles.clear();
        }
    }
}
