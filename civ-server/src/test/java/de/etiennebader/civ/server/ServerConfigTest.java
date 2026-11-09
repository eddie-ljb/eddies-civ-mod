package de.etiennebader.civ.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigTest {

    @Test
    @DisplayName("Fehlende Datei wird mit Standardwerten angelegt und wieder gelesen")
    void createsDefaults() throws IOException {
        Path dir = Files.createTempDirectory("civ-config");
        Path file = dir.resolve("config").resolve("eddies-civ-mod.properties");

        assertEquals(ServerSettings.defaults(), ServerConfig.load(file));
        assertTrue(Files.exists(file));
        assertEquals(ServerSettings.defaults(), ServerConfig.load(file));
    }

    @Test
    @DisplayName("Eigene Werte werden übernommen, ungültige fallen auf den Standard zurück")
    void readsValues() {
        Properties properties = new Properties();
        properties.setProperty(ServerConfig.TICKS_PER_DAY, "12000");
        properties.setProperty(ServerConfig.CLAIM_BASE, "abc");
        properties.setProperty(ServerConfig.CLAIM_PER_MEMBER, "-3");
        properties.setProperty(ServerConfig.PRICE_REFERENCE, "80");

        ServerSettings settings = ServerConfig.of(properties);

        assertEquals(12_000, settings.ticksPerDay());
        assertEquals(ServerSettings.defaults().claimBase(), settings.claimBase());
        assertEquals(ServerSettings.defaults().claimPerMember(), settings.claimPerMember());
        assertEquals(8_000, settings.priceReference().amount());
    }
}
