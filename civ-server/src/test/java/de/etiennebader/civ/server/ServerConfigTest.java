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
    @DisplayName("Ehrgeizige Dorfbewohner sind standardmäßig an und lassen sich abschalten")
    void ambitionsSwitch() {
        assertTrue(ServerConfig.of(new Properties()).ambitions());
        Properties properties = new Properties();
        properties.setProperty(ServerConfig.AMBITIONS, "false");
        assertEquals(false, ServerConfig.of(properties).ambitions());
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

    @Test
    @DisplayName("Wirtschaftssimulation ist per Config fein einstellbar, Standardwerte bleiben sonst erhalten")
    void readsEconomyTuning() {
        Properties properties = new Properties();
        properties.setProperty(ServerConfig.PRICE_LEVEL_MIN, "0.8");
        properties.setProperty(ServerConfig.PRICE_LEVEL_MAX, "2.5");
        properties.setProperty(ServerConfig.PRICE_LEVEL_DAMPING, "1.0");

        ServerSettings settings = ServerConfig.of(properties);

        assertEquals(0.8, settings.economy().priceLevelMin());
        assertEquals(2.5, settings.economy().priceLevelMax());
        assertEquals(1.0, settings.economy().priceLevelDamping());
    }

    @Test
    @DisplayName("Ungueltige Wirtschafts-Werte (min >= max) fallen komplett auf den Standard zurueck")
    void invalidEconomyTuningFallsBackToDefaults() {
        Properties properties = new Properties();
        properties.setProperty(ServerConfig.PRICE_LEVEL_MIN, "3.0");
        properties.setProperty(ServerConfig.PRICE_LEVEL_MAX, "2.0");

        ServerSettings settings = ServerConfig.of(properties);

        assertEquals(ServerSettings.EconomySettings.DEFAULT, settings.economy());
    }

    @Test
    @DisplayName("Wirtschafts-Werte ueberleben einen Schreib-/Lese-Durchlauf")
    void economyTuningRoundTrips() throws IOException {
        Path dir = Files.createTempDirectory("civ-config-economy");
        Path file = dir.resolve("eddies-civ-mod.properties");
        ServerSettings written = new ServerSettings(ServerSettings.defaults().ticksPerDay(),
                ServerSettings.defaults().claimBase(), ServerSettings.defaults().claimPerMember(),
                ServerSettings.defaults().currency(), ServerSettings.defaults().priceReference(),
                ServerSettings.defaults().villagerIncome(), ServerSettings.defaults().tiers(),
                ServerSettings.defaults().web(), ServerSettings.defaults().emigration(),
                ServerSettings.defaults().villagerPolitics(), ServerSettings.defaults().levels(),
                ServerSettings.defaults().parties(), ServerSettings.defaults().show(),
                new ServerSettings.EconomySettings(0.75, 3.5, 0.65));
        ServerConfig.write(file, written);

        assertEquals(written.economy(), ServerConfig.load(file).economy());
    }
}
