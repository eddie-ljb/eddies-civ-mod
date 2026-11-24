package de.etiennebader.civ.fabric;

import de.etiennebader.civ.EddiesCivMod;
import de.etiennebader.civ.application.persistence.FlushReport;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.ServerConfig;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.save.SaveListener;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
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
    private final TerritoryNotifier territoryNotifier = new TerritoryNotifier();
    private final BorderDisplay borders = new BorderDisplay();
    private final VillagerCensus villagers = new VillagerCensus(this);
    private final ConstructionSites constructionSites = new ConstructionSites();
    private final RoadSites roadSites = new RoadSites();

    public void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::start);
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> save());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        villagers.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> playerJoined(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> playerLeft(handler.getPlayer()));
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> chatted(sender,
                message.signedContent()));
    }

    /** Schreibt den allgemeinen Chat mit, damit die Webseite ihn zeigt. */
    private void chatted(ServerPlayer sender, String text) {
        if (civ == null) {
            return;
        }
        try {
            civ.community().gameChat(Players.ref(sender), text);
        } catch (RuntimeException e) {
            LOGGER.warn("Chatnachricht konnte nicht mitgeschrieben werden: {}", e.getMessage());
        }
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
        // Ab hier bauen die Dienste ihre Gesetzeskataloge; fremde Mods melden ihre Gesetze vorher an (#51).
        de.etiennebader.civ.domain.law.ExtensionParameters.freeze();
        Path file = databaseFile(server);
        // Schlägt das Laden fehl, soll der Server nicht ohne Speicherung weiterlaufen: Fehler bricht den Start ab.
        ServerSettings settings = loadSettings(server);
        civ = CivServer.open(file, settings, new LoggingSaveListener());
        LOGGER.info("Zivilisation geladen: {} Gemeinden, Tag {} ({})", civ.queries().polities().size(),
                civ.calendar().today().value(), file);
        try {
            civ.startWeb(id -> Goods.item(id).map(item -> Goods.traits(new ItemStack(item))), server::execute)
                    .ifPresent(port -> LOGGER.info("Webseite läuft auf Port {}", port));
        } catch (IOException e) {
            LOGGER.warn("Webseite konnte nicht starten (Port belegt?): {}", e.getMessage());
        }
    }

    /** {@code config/eddies-civ-mod.properties}; bei Fehlern gelten die Standardwerte. */
    private static ServerSettings loadSettings(MinecraftServer server) {
        Path config = server.getServerDirectory().resolve("config").resolve(EddiesCivMod.MOD_ID + ".properties");
        try {
            return ServerConfig.load(config);
        } catch (RuntimeException e) {
            LOGGER.error("Konfiguration {} konnte nicht gelesen werden, verwende Standardwerte", config, e);
            return ServerSettings.defaults();
        }
    }

    private void tick(MinecraftServer server) {
        Screens.runPending();
        if (civ == null) {
            return;
        }
        try {
            civ.tick().ifPresent(report -> Components.broadcast(server, civ.commands().newDay(report)));
            civ.takeAnnouncements().forEach(announcement -> announce(server, civ, announcement));
            territoryNotifier.tick(server, civ);
            villagers.tick(server, civ);
            borders.tick(server, civ);
            constructionSites.tick(server, civ);
            roadSites.tick(server, civ);
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

    private static void announce(MinecraftServer server, CivServer civ, Announcement announcement) {
        if (announcement.recipient().isPresent()) {
            ServerPlayer player = server.getPlayerList().getPlayer(announcement.recipient().get().uuid());
            if (player != null) {
                player.sendSystemMessage(Components.prefixed(Components.of(announcement.message()), true));
            }
            return;
        }
        if (announcement.town().isEmpty()) {
            Components.broadcast(server, announcement.message());
            return;
        }
        civ.queries().polity(announcement.town().get()).ifPresent(town -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (town.isMember(Players.ref(player)) || civ.levels().chainOf(Players.ref(player)).stream()
                        .anyMatch(level -> level.id().equals(town.id()))) {
                    player.sendSystemMessage(Components.prefixed(Components.of(announcement.message()), true));
                    Feedback.success(player);
                }
            }
        });
    }

    /** Zeigt dem Spieler einige Sekunden lang die Gebietsgrenzen in seiner Nähe. */
    void showBorders(ServerPlayer player) {
        borders.show(player);
    }

    /** Zeigt dem Spieler einige Sekunden lang den Umriss einer Fläche. */
    void showArea(ServerPlayer player, Area area) {
        borders.showArea(player, area);
    }

    private void playerJoined(ServerPlayer player) {
        if (civ != null) {
            civ.playerJoined(Players.ref(player), Players.name(player));
            player.sendSystemMessage(Components.prefixed(Components.of(civ.commands().welcome(Players.caller(player))), true));
            de.etiennebader.civ.fabric.content.Handbook.giveOnFirstJoin(player);
        }
    }

    private void playerLeft(ServerPlayer player) {
        if (civ != null) {
            civ.playerLeft(Players.ref(player));
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
