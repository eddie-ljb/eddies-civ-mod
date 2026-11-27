package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.civic.Anthem;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.fabric.content.CivContent;
import de.etiennebader.civ.fabric.content.PosterDisplays;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.election.LiveCountService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Was nebenher im Spiel läuft: Aufschriften der Litfaßsäulen auffrischen, Bossbars der Live-Auszählung zeigen und
 * Hymnen abspielen (auf Wunsch beim Betreten einer Gemeinde).
 */
public final class Ambience {

    private static final int POSTER_INTERVAL = 600;
    private static final int BAR_INTERVAL = 10;
    private static final int ENTER_INTERVAL = 20;
    private static Ambience instance;

    private record Playback(UUID player, List<Anthem.Note> melody, Holder<SoundEvent> sound, int index, long nextTick) {
    }

    private final CivServerHost host;
    private final CivContent content;
    private final List<Playback> playing = new ArrayList<>();
    private final Map<UUID, ServerBossEvent> bars = new HashMap<>();
    private final Map<UUID, Optional<de.etiennebader.civ.domain.shared.PolityId>> lastTown = new HashMap<>();
    private long ticks;

    public Ambience(CivServerHost host, CivContent content) {
        this.host = host;
        this.content = content;
        instance = this;
    }

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    /** Spielt eine Hymne für einen Spieler ab; eine laufende Hymne für ihn wird ersetzt. */
    public static void play(ServerPlayer player, Anthem anthem) {
        if (instance == null) {
            return;
        }
        instance.playing.removeIf(playback -> playback.player().equals(player.getUUID()));
        NoteBlockInstrument instrument = NoteBlockInstrument.HARP;
        for (NoteBlockInstrument candidate : NoteBlockInstrument.values()) {
            if (candidate.getSerializedName().equals(anthem.instrument().toLowerCase(Locale.ROOT))) {
                instrument = candidate;
            }
        }
        instance.playing.add(new Playback(player.getUUID(), anthem.melody(), instrument.getSoundEvent(), 0,
                instance.ticks));
    }

    private void tick(MinecraftServer server) {
        ticks++;
        Optional<CivServer> civ = host.current();
        playAnthems(server);
        if (civ.isEmpty()) {
            return;
        }
        try {
            if (ticks % POSTER_INTERVAL == 0) {
                PosterDisplays.refresh(server, civ.get(), level -> true,
                        (level, pos) -> level.getBlockState(pos).is(content.electionPoster())
                                || level.getBlockState(pos).is(content.emblems().poster()));
                Emblems.instance().refresh(server, civ.get());
            }
            if (ticks % BAR_INTERVAL == 0) {
                showLiveCounts(server, civ.get());
            }
            if (ticks % ENTER_INTERVAL == 0 && civ.get().settings().show().anthemOnEnter()) {
                anthemsOnEnter(server, civ.get());
            }
        } catch (RuntimeException e) {
            de.etiennebader.civ.EddiesCivMod.LOGGER.warn("Nebenher-Aufgabe fehlgeschlagen: {}", e.getMessage());
        }
    }

    private void playAnthems(MinecraftServer server) {
        List<Playback> next = new ArrayList<>();
        Iterator<Playback> iterator = playing.iterator();
        while (iterator.hasNext()) {
            Playback playback = iterator.next();
            if (playback.nextTick() > ticks) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(playback.player());
            if (player == null || playback.index() >= playback.melody().size()) {
                continue;
            }
            Anthem.Note note = playback.melody().get(playback.index());
            if (!note.rest()) {
                player.connection.send(new ClientboundSoundPacket(playback.sound(), SoundSource.RECORDS,
                        player.getX(), player.getY(), player.getZ(), 1.0F, note.pitch(), player.getRandom().nextLong()));
            }
            next.add(new Playback(playback.player(), playback.melody(), playback.sound(), playback.index() + 1,
                    ticks + note.ticks()));
        }
        playing.addAll(next);
    }

    /** Eine Bossbar je laufender Auszählung für alle Spieler, die zu der Polity gehören. */
    private void showLiveCounts(MinecraftServer server, CivServer civ) {
        List<LiveCountService.LiveView> running = civ.liveCount().running();
        java.util.Set<UUID> active = new java.util.HashSet<>();
        for (LiveCountService.LiveView view : running) {
            UUID id = view.polity().value();
            active.add(id);
            ServerBossEvent bar = bars.computeIfAbsent(id, ignored -> new ServerBossEvent(UUID.randomUUID(),
                    Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
            String leaders = view.shares().entrySet().stream().limit(3)
                    .map(entry -> entry.getKey() + " " + Math.round(entry.getValue()) + " %")
                    .collect(Collectors.joining(" · "));
            bar.setName(Component.literal("Auszählung " + view.polityName() + " – " + Math.round(view.counted() * 100)
                    + " %" + (leaders.isEmpty() ? "" : ": " + leaders)));
            bar.setProgress((float) Math.max(0.02, view.counted()));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                boolean member = civ.levels().chainOf(Players.ref(player)).stream()
                        .anyMatch(polity -> polity.id().equals(view.polity()));
                if (member && !bar.getPlayers().contains(player)) {
                    bar.addPlayer(player);
                }
            }
        }
        bars.entrySet().removeIf(entry -> {
            if (!active.contains(entry.getKey())) {
                entry.getValue().removeAllPlayers();
                return true;
            }
            return false;
        });
    }

    /** Hymne der Gemeinde, deren Gebiet ein Spieler gerade betreten hat. */
    private void anthemsOnEnter(MinecraftServer server, CivServer civ) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChunkCoord chunk = new ChunkCoord(new DimensionId(player.level().dimension().identifier().toString()),
                    player.chunkPosition().x(), player.chunkPosition().z());
            Optional<de.etiennebader.civ.domain.shared.PolityId> town = civ.queries().territory().claimAt(chunk)
                    .map(claim -> claim.polity());
            Optional<de.etiennebader.civ.domain.shared.PolityId> before = lastTown.put(player.getUUID(), town);
            if (town.isPresent() && before != null && !before.equals(town)) {
                civ.civic().anthemOf(town.get()).ifPresent(anthem -> {
                    play(player, anthem);
                    Components.showHint(player, Message.of(MessageKey.ANTHEM_PLAYING,
                            civ.texts().polityName(town.get()), anthem.title()), true);
                });
            }
        }
    }
}
