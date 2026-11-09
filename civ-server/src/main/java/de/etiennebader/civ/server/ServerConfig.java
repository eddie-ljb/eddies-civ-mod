package de.etiennebader.civ.server;

import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.server.command.MoneyFormat;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Liest die {@link ServerSettings} aus einer Properties-Datei, z. B. {@code config/eddies-civ-mod.properties}.
 * Fehlt die Datei, wird sie mit den Standardwerten angelegt. Fehlende oder ungültige Einträge fallen auf
 * den Standardwert zurück.
 */
public final class ServerConfig {

    static final String TICKS_PER_DAY = "ticks_per_day";
    static final String CLAIM_BASE = "claim_base";
    static final String CLAIM_PER_MEMBER = "claim_per_member";
    static final String PRICE_REFERENCE = "price_reference_emeralds_per_player";
    static final String VILLAGER_INCOME = "villager_income_hundredths_per_day";
    static final String MARKET_TOWN_AT = "market_town_inhabitants";
    static final String CITY_AT = "city_inhabitants";
    static final String METROPOLIS_AT = "metropolis_inhabitants";
    static final String WEB_ENABLED = "web_enabled";
    static final String WEB_BIND = "web_bind";
    static final String WEB_PORT = "web_port";
    static final String WEB_URL = "web_public_url";

    private static final String HEADER = """
            Eddies Civ Mod
            ticks_per_day: Laenge eines Zivilisationstags in Ticks (24000 = ein Minecraft-Tag)
            claim_base: Chunks, die jede Gemeinde beanspruchen darf; claim_per_member: zusaetzlich je Mitglied
            price_reference_emeralds_per_player: Geldmenge je Spieler, bei der Marktpreise ihrem Grundpreis entsprechen
            villager_income_hundredths_per_day: Einnahmen der Gemeindekasse je Dorfbewohner und Tag (100 = 1 Smaragd)
            market_town_inhabitants, city_inhabitants, metropolis_inhabitants: Einwohner fuer die Siedlungsstufen
            web_enabled, web_bind, web_port: eingebettete Webseite; web_public_url: Adresse fuer Spieler (optional)""";

    private ServerConfig() {
    }

    /** Lädt die Einstellungen; legt die Datei mit Standardwerten an, wenn es sie nicht gibt. */
    public static ServerSettings load(Path file) {
        if (!Files.exists(file)) {
            write(file, ServerSettings.defaults());
            return ServerSettings.defaults();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Konfiguration nicht lesbar: " + file, e);
        }
        return of(properties);
    }

    static ServerSettings of(Properties properties) {
        ServerSettings defaults = ServerSettings.defaults();
        long ticks = number(properties, TICKS_PER_DAY, defaults.ticksPerDay(), 1);
        int claimBase = (int) number(properties, CLAIM_BASE, defaults.claimBase(), 1);
        int claimPerMember = (int) number(properties, CLAIM_PER_MEMBER, defaults.claimPerMember(), 0);
        long reference = number(properties, PRICE_REFERENCE,
                defaults.priceReference().amount() / MoneyFormat.UNITS_PER_EMERALD, 1);
        long income = number(properties, VILLAGER_INCOME, defaults.villagerIncome().amount(), 0);
        ServerSettings.TierThresholds tiers = defaults.tiers();
        try {
            tiers = new ServerSettings.TierThresholds(
                    (int) number(properties, MARKET_TOWN_AT, tiers.marketTown(), 2),
                    (int) number(properties, CITY_AT, tiers.city(), 3),
                    (int) number(properties, METROPOLIS_AT, tiers.metropolis(), 4));
        } catch (IllegalArgumentException e) {
            tiers = defaults.tiers();
        }
        ServerSettings.WebSettings web = new ServerSettings.WebSettings(
                Boolean.parseBoolean(properties.getProperty(WEB_ENABLED, Boolean.toString(defaults.web().enabled())).strip()),
                properties.getProperty(WEB_BIND, defaults.web().bind()).strip(),
                (int) Math.min(65_535, number(properties, WEB_PORT, defaults.web().port(), 0)),
                properties.getProperty(WEB_URL, defaults.web().publicUrl()).strip());
        return new ServerSettings(ticks, claimBase, claimPerMember, defaults.currency(),
                Money.of(MoneyFormat.emeralds(reference), defaults.currency()), Money.of(income, defaults.currency()),
                tiers, web);
    }

    static void write(Path file, ServerSettings settings) {
        Properties properties = new Properties();
        properties.setProperty(TICKS_PER_DAY, Long.toString(settings.ticksPerDay()));
        properties.setProperty(CLAIM_BASE, Integer.toString(settings.claimBase()));
        properties.setProperty(CLAIM_PER_MEMBER, Integer.toString(settings.claimPerMember()));
        properties.setProperty(PRICE_REFERENCE, Long.toString(
                settings.priceReference().amount() / MoneyFormat.UNITS_PER_EMERALD));
        properties.setProperty(VILLAGER_INCOME, Long.toString(settings.villagerIncome().amount()));
        properties.setProperty(MARKET_TOWN_AT, Integer.toString(settings.tiers().marketTown()));
        properties.setProperty(CITY_AT, Integer.toString(settings.tiers().city()));
        properties.setProperty(METROPOLIS_AT, Integer.toString(settings.tiers().metropolis()));
        properties.setProperty(WEB_ENABLED, Boolean.toString(settings.web().enabled()));
        properties.setProperty(WEB_BIND, settings.web().bind());
        properties.setProperty(WEB_PORT, Integer.toString(settings.web().port()));
        properties.setProperty(WEB_URL, settings.web().publicUrl());
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, HEADER);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Konfiguration nicht schreibbar: " + file, e);
        }
    }

    private static long number(Properties properties, String key, long fallback, long minimum) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(value.strip());
            return parsed >= minimum ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
