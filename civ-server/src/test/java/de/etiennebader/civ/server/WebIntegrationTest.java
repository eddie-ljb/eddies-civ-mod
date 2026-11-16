package de.etiennebader.civ.server;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.save.SaveListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebIntegrationTest {

    private record Response(int status, String body, String cookie) {
    }

    @Test
    @DisplayName("Pausierter Einzelspieler: ohne Ticks beantwortet die Webseite Anfragen über den Executor des Servers")
    void answersWhileServerIsPaused() throws Exception {
        SeededIdGenerator ids = new SeededIdGenerator(62);
        PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
        CivServer civ = CivServer.start(new MemoryStateStore(), withWeb(ServerSettings.withDayLength(1_200)), ids,
                SaveListener.NONE);
        civ.playerJoined(alice, "Alice");
        civ.commands().found(new Caller(alice, "Alice", new ChunkCoord(DimensionId.OVERWORLD, 0, 0).center(64)),
                "Nordhain");
        ExecutorService mainThread = Executors.newSingleThreadExecutor();
        try {
            int port = civ.startWeb(id -> Optional.empty(), mainThread).orElseThrow();
            Response world = request("http://127.0.0.1:" + port + "/api/world", null, null);
            assertEquals(200, world.status());
            assertTrue(world.body().contains("\"name\":\"Nordhain\""));
        } finally {
            civ.stop();
            mainThread.shutdownNow();
        }
    }

    @Test
    @DisplayName("Webseite: Welt lesen, mit Code anmelden, Kontostand sehen und überweisen")
    void loginAndTransfer() throws Exception {
        SeededIdGenerator ids = new SeededIdGenerator(61);
        PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
        PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
        ServerSettings settings = withWeb(ServerSettings.withDayLength(1_200));
        CivServer civ = CivServer.start(new MemoryStateStore(), settings, ids, SaveListener.NONE);
        Caller aliceCaller = new Caller(alice, "Alice", new ChunkCoord(DimensionId.OVERWORLD, 0, 0).center(64));
        civ.playerJoined(alice, "Alice");
        civ.playerJoined(bob, "Bob");
        civ.commands().found(aliceCaller, "Nordhain");
        civ.bank().deposit(aliceCaller, 10);
        int port = civ.startWeb(id -> Optional.empty(), command -> { }).orElseThrow();
        String code = String.valueOf(civ.webLogin(aliceCaller).lines().getFirst().args().getFirst());

        AtomicBoolean running = new AtomicBoolean(true);
        Thread gameThread = new Thread(() -> {
            while (running.get()) {
                civ.tick();
                Thread.onSpinWait();
            }
        });
        gameThread.start();
        try {
            String base = "http://127.0.0.1:" + port;
            Response world = request(base + "/api/world", null, null);
            assertEquals(200, world.status());
            assertTrue(world.body().contains("\"name\":\"Nordhain\""));
            assertTrue(request(base + "/", null, null).body().contains("Eddies"));
            Response elections = request(base + "/api/elections", null, null);
            assertEquals(200, elections.status());
            assertTrue(elections.body().contains("\"results\":[") && elections.body().contains("\"polls\":["));

            assertEquals(401, request(base + "/api/me", null, null).status());
            assertEquals(401, request(base + "/api/login", "{\"code\":\"FALSCH\"}", null).status());
            Response login = request(base + "/api/login", "{\"code\":\"" + code + "\"}", null);
            assertEquals(200, login.status());
            String cookie = login.cookie().split(";")[0];
            assertEquals(401, request(base + "/api/login", "{\"code\":\"" + code + "\"}", null).status());

            Response me = request(base + "/api/me", null, cookie);
            assertTrue(me.body().contains("\"balance\":\"10,00\""), me.body());
            assertTrue(me.body().contains("\"town\":\"Nordhain\""));
            assertTrue(me.body().contains("\"office\":\"MAYOR\""), me.body());
            assertEquals(422, request(base + "/api/stand", "{\"office\":\"MAYOR\"}", cookie).status());
            assertEquals(400, request(base + "/api/stand", "{\"office\":\"KAISER\"}", cookie).status());

            Response transfer = request(base + "/api/transfer", "{\"to\":\"Bob\",\"amount\":\"2,5\"}", cookie);
            assertEquals(200, transfer.status());
            assertTrue(request(base + "/api/me", null, cookie).body().contains("\"balance\":\"7,50\""));
            assertEquals(422, request(base + "/api/transfer", "{\"to\":\"Niemand\",\"amount\":\"1\"}", cookie).status());
            assertEquals(404, request(base + "/api/unbekannt", null, null).status());
        } finally {
            running.set(false);
            gameThread.join();
            civ.stop();
        }
    }

    @Test
    @DisplayName("Webseite: Startseiten-Kennzahlen, Freunde, Chat, Briefe und Verträge")
    void communityEndpoints() throws Exception {
        SeededIdGenerator ids = new SeededIdGenerator(62);
        PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
        PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
        CivServer civ = CivServer.start(new MemoryStateStore(), withWeb(ServerSettings.withDayLength(1_200)), ids,
                SaveListener.NONE);
        Caller aliceCaller = new Caller(alice, "Alice", new ChunkCoord(DimensionId.OVERWORLD, 0, 0).center(64));
        civ.playerJoined(alice, "Alice");
        civ.playerJoined(bob, "Bob");
        civ.commands().found(aliceCaller, "Nordhain");
        civ.bank().deposit(aliceCaller, 10);
        int port = civ.startWeb(id -> Optional.empty(), command -> { }).orElseThrow();
        String code = String.valueOf(civ.webLogin(aliceCaller).lines().getFirst().args().getFirst());
        civ.bank().deposit(aliceCaller, 20);
        assertTrue(civ.companies().found(aliceCaller, "10", "Holz AG").success());
        assertTrue(civ.press().found(aliceCaller, "Bote").success());
        AtomicBoolean running = new AtomicBoolean(true);
        Thread gameThread = new Thread(() -> {
            while (running.get()) {
                civ.tick();
                Thread.onSpinWait();
            }
        });
        gameThread.start();
        try {
            String base = "http://127.0.0.1:" + port;
            Response stats = request(base + "/api/stats", null, null);
            assertTrue(stats.body().contains("\"towns\":1") && stats.body().contains("\"online\":2"), stats.body());
            assertTrue(request(base + "/api/streets", null, null).body().contains("\"postalCode\""));
            assertEquals(401, request(base + "/api/chat", null, null).status());
            String cookie = request(base + "/api/login", "{\"code\":\"" + code + "\"}", null).cookie().split(";")[0];

            assertEquals(200, request(base + "/api/friends", "{\"action\":\"request\",\"name\":\"Bob\"}", cookie).status());
            Response friends = request(base + "/api/friends", null, cookie);
            assertTrue(friends.body().contains("\"state\":\"outgoing\"") && friends.body().contains("\"players\":[\"Bob\"]"),
                    friends.body());
            assertEquals(422, request(base + "/api/chat", "{\"text\":\"psst\",\"to\":\"Bob\"}", cookie).status(),
                    "Direktnachricht nur an Freunde");
            Response pub = request(base + "/api/chat", "{\"text\":\"Hallo aus dem Browser\"}", cookie);
            assertEquals(200, pub.status(), pub.body());
            Response chat = request(base + "/api/chat?after=0", null, cookie);
            assertTrue(chat.body().contains("Hallo aus dem Browser") && chat.body().contains("\"origin\":\"web\""), chat.body());
            assertTrue(request(base + "/api/chat?after=999", null, cookie).body().contains("\"messages\":[]"));

            Response letter = request(base + "/api/letters",
                    "{\"to\":\"Bob\",\"subject\":\"Einladung\",\"body\":\"Zeile 1\\nZeile 2\"}", cookie);
            assertEquals(200, letter.status(), letter.body());
            assertTrue(request(base + "/api/letters", null, cookie).body().contains("\"subject\":\"Einladung\""));
            assertEquals(422, request(base + "/api/letters", "{\"to\":\"Bob\",\"subject\":\"\"}", cookie).status());

            Response offer = request(base + "/api/contracts", "{\"action\":\"custom\",\"to\":\"Bob\",\"title\":\"Holz\","
                    + "\"text\":\"64 Stämme\",\"once\":\"1\",\"perDay\":\"0\",\"days\":\"3\"}", cookie);
            assertEquals(200, offer.status(), offer.body());
            Response contracts = request(base + "/api/contracts", null, cookie);
            assertTrue(contracts.body().contains("\"title\":\"Holz\"") && contracts.body().contains("\"canDecline\":true"),
                    contracts.body());
            assertEquals(200, request(base + "/api/contracts", "{\"action\":\"decline\",\"number\":\"1\"}", cookie).status());
            assertEquals(400, request(base + "/api/contracts", "{\"action\":\"zaubern\"}", cookie).status());
            Response me = request(base + "/api/me", null, cookie);
            assertTrue(me.body().contains("\"unread\":0") && me.body().contains("\"friendRequests\":0"), me.body());

            Response exchange = request(base + "/api/exchange", null, null);
            assertTrue(exchange.body().contains("\"name\":\"Holz AG\"") && exchange.body().contains("\"quotes\":[{"),
                    exchange.body());
            Response sell = request(base + "/api/exchange",
                    "{\"action\":\"sell\",\"company\":\"Holz AG\",\"quantity\":\"10\",\"limit\":\"0,01\"}", cookie);
            // Text hängt von den Sprachdateien im Klassenpfad ab, deshalb nur der Status.
            assertTrue(sell.status() == 200 && sell.body().contains("\"ok\":true"), sell.body());
            Response depot = request(base + "/api/depot", null, cookie);
            assertTrue(depot.body().contains("\"shares\":1000") && depot.body().contains("\"papers\":[\"Bote\"]"),
                    depot.body());
            int idStart = exchange.body().indexOf("\"id\":\"") + 6;
            String companyId = exchange.body().substring(idStart, exchange.body().indexOf('"', idStart));
            Response book = request(base + "/api/exchange/book?company=" + companyId, null, null);
            assertTrue(book.status() == 200 && book.body().startsWith("{\"orders\":["), book.body());
            assertEquals(400, request(base + "/api/exchange", "{\"action\":\"zaubern\"}", cookie).status());
            Response preview = request(base + "/api/press/preview",
                    "{\"title\":\"Holz AG ist großartig\",\"body\":\"Ein Erfolg.\"}", cookie);
            assertTrue(preview.body().contains("\"subject\":\"Holz AG\"") && preview.body().contains("wohlwollend"),
                    preview.body());
            assertEquals(200, request(base + "/api/press",
                    "{\"paper\":\"Bote\",\"title\":\"Holz AG ist großartig\",\"body\":\"Ein Erfolg.\"}", cookie).status());
            Response courts = request(base + "/api/justice", null, cookie);
            assertTrue(courts.status() == 200 && courts.body().contains("\"courts\":[{"), courts.body());
            int courtStart = courts.body().indexOf("\"courts\":[{\"id\":\"") + 17;
            String courtId = courts.body().substring(courtStart, courts.body().indexOf('"', courtStart));
            Response sue = request(base + "/api/justice", "{\"action\":\"file\",\"court\":\"" + courtId
                    + "\",\"kind\":\"verfassung\",\"text\":\"tax_rate | zu hoch\"}", cookie);
            assertTrue(sue.body().contains("\"ok\""), sue.body());
            assertEquals(400, request(base + "/api/justice", "{\"action\":\"zaubern\"}", cookie).status());
            assertTrue(request(base + "/api/show", null, null).body().contains("\"running\":["));
            assertTrue(request(base + "/api/civic", null, null).body().contains("\"name\":\"Nordhain\""));
            Response press = request(base + "/api/press", null, null);
            assertTrue(press.body().contains("\"title\":\"Holz AG ist großartig\"")
                    && press.body().contains("\"tendencyWord\":\"wohlwollend\""), press.body());
            Response stats2 = request(base + "/api/stats", null, null);
            assertTrue(stats2.body().contains("\"companies\":1"), stats2.body());
        } finally {
            running.set(false);
            gameThread.join();
            civ.stop();
        }
    }

    private static ServerSettings withWeb(ServerSettings settings) {
        return new ServerSettings(settings.ticksPerDay(), settings.claimBase(), settings.claimPerMember(),
                settings.currency(), settings.priceReference(), settings.villagerIncome(), settings.tiers(),
                new ServerSettings.WebSettings(true, "127.0.0.1", 0, ""));
    }

    private static Response request(String url, String json, String cookie) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(10_000);
        if (cookie != null) {
            connection.setRequestProperty("Cookie", cookie);
        }
        if (json != null) {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream in = status < 400 ? connection.getInputStream() : connection.getErrorStream();
        String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return new Response(status, body, connection.getHeaderField("Set-Cookie"));
    }
}
