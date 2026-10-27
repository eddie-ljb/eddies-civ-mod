package de.etiennebader.civ.fabric;

import de.etiennebader.civ.EddiesCivMod;
import de.etiennebader.civ.application.persistence.FlushReport;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.save.SaveListener;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Hält die {@link CivServer}-Instanz der gerade laufenden Welt und hängt sie an die Ereignisse
 * des Minecraft-Servers: Laden beim Start, ein Tick pro Servertick, Speichern zusammen mit der
 * Welt und Herunterfahren beim Stoppen.
 *
 * <p>Im Einzelspieler startet und stoppt der integrierte Server bei jedem Betreten und Verlassen
 * einer Welt; jede Welt hat ihre eigene Datenbank im Weltordner.
 */
public final class CivServerHost {

    private static final Logger LOGGER = EddiesCivMod.LOGGER;

    private CivServer civ;

    public void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::start);
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> save());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> playerJoined(handler.getPlayer()));
    }

    /** Die Zivilisation der laufenden Welt; leer, solange kein Server läuft. */
    public Optional<CivServer> current() {
        return Optional.ofNullable(civ);
    }

    /** {@code <Welt>/data/eddies-civ-mod/civ.sqlite} */
    static Path databaseFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(EddiesCivMod.MOD_ID).resolve("civ.sqlite");
    }

    private void start(MinecraftServer server) {
        Path file = databaseFile(server);
        // Schlägt das Laden fehl, soll der Server nicht ohne Speicherung weiterlaufen: Fehler bricht den Start ab.
        civ = CivServer.open(file, ServerSettings.defaults(), new LoggingSaveListener());
        LOGGER.info("Zivilisation geladen: {} Gemeinden, Tag {} ({})", civ.queries().polities().size(),
                civ.calendar().today().value(), file);
    }

    private void tick(MinecraftServer server) {
        if (civ == null) {
            return;
        }
        try {
            civ.tick().ifPresent(report -> Components.broadcast(server, civ.commands().newDay(report)));
        } catch (RuntimeException e) {
            LOGGER.error("Fehler beim Tagesabschluss", e);
        }
    }

    private void save() {
        if (civ == null) {
            return;
        }
        try {
            civ.save();
        } catch (RuntimeException e) {
            LOGGER.error("Zivilisation konnte nicht zum Speichern erfasst werden", e);
        }
    }

    private void stop() {
        if (civ == null) {
            return;
        }
        CivServer stopping = civ;
        civ = null;
        try {
            FlushReport report = stopping.stop();
            LOGGER.info("Zivilisation gespeichert und geschlossen ({} Events)", report.events());
        } catch (RuntimeException e) {
            LOGGER.error("Zivilisation konnte beim Beenden nicht vollständig gespeichert werden", e);
        }
    }

    private void playerJoined(ServerPlayer player) {
        if (civ != null) {
            civ.playerJoined(Players.ref(player), Players.name(player));
        }
    }

    /** Protokolliert Speichervorgänge; wird aus dem Speicher-Thread aufgerufen. */
    private static final class LoggingSaveListener implements SaveListener {

        @Override
        public void saved(FlushReport report, Duration took) {
            LOGGER.debug("Zivilisation gespeichert in {} ms: {}", took.toMillis(), report);
        }

        @Override
        public void failed(RuntimeException error) {
            LOGGER.error("Zivilisation konnte nicht gespeichert werden; nächster Versuch beim nächsten Speichern", error);
        }
    }
}
