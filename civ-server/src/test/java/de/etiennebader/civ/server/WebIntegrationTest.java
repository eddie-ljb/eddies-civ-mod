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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebIntegrationTest {

    private record Response(int status, String body, String cookie) {
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
        int port = civ.startWeb(id -> Optional.empty()).orElseThrow();
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

            assertEquals(401, request(base + "/api/me", null, null).status());
            assertEquals(401, request(base + "/api/login", "{\"code\":\"FALSCH\"}", null).status());
            Response login = request(base + "/api/login", "{\"code\":\"" + code + "\"}", null);
            assertEquals(200, login.status());
            String cookie = login.cookie().split(";")[0];
            assertEquals(401, request(base + "/api/login", "{\"code\":\"" + code + "\"}", null).status());

            Response me = request(base + "/api/me", null, cookie);
            assertTrue(me.body().contains("\"balance\":\"10,00\""), me.body());
            assertTrue(me.body().contains("\"town\":\"Nordhain\""));

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
