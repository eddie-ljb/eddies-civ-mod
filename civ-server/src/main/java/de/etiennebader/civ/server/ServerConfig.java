package de.etiennebader.civ.server;

import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.federation.Levels;

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
    static final String EMIGRATION = "villager_emigration";
    static final String VILLAGER_POLITICS = "villager_politics_enabled";
    static final String AMBITIONS = "villager_ambitions";
    static final String LAW_DRAFTS = "law_drafts_persist";
    static final String WAR_PORT_LOCK = "war_port_lock";
    static final String LEVELS = "federation_levels";
    static final String VILLAGER_PARTIES = "villager_parties";
    static final String PARTY_FOUNDERS = "villager_party_founders";
    static final String COST_MUNICIPALITY = "founding_cost_municipality";
    static final String COST_COMPANY = "founding_cost_company";
    static final String COST_LEVELS = "founding_cost_levels";
    static final String COST_LEVEL_DEFAULT = "founding_cost_level_default";
    static final String REQUIRE_PREVIOUS = "founding_require_previous_level";
    static final String WEB_ENABLED = "web_enabled";
    static final String WEB_BIND = "web_bind";
    static final String WEB_PORT = "web_port";
    static final String WEB_URL = "web_public_url";
    static final String WEB_TRUST_PROXY = "web_trust_proxy";
    static final String LIVE_COUNT = "live_count";
    static final String LIVE_COUNT_SECONDS = "live_count_seconds";
    static final String PROJECTIONS = "live_count_projections";
    static final String DEBATE_MINUTES = "debate_minutes";
    static final String ANTHEM_ON_ENTER = "anthem_on_enter";
    static final String WORLD_MAP = "world_map";
    static final String MAP_RADIUS = "world_map_radius_chunks";
    static final String COMPANY_PLOT_REQUIRED = "company_plot_required";
    static final String PRICE_LEVEL_MIN = "price_level_min";
    static final String PRICE_LEVEL_MAX = "price_level_max";
    static final String PRICE_LEVEL_DAMPING = "price_level_damping";
    private static final java.util.List<String> KEYS = java.util.List.of(LIVE_COUNT, LIVE_COUNT_SECONDS, PROJECTIONS,
            DEBATE_MINUTES, ANTHEM_ON_ENTER, WORLD_MAP, MAP_RADIUS, WEB_TRUST_PROXY, VILLAGER_POLITICS,
            AMBITIONS, LAW_DRAFTS, WAR_PORT_LOCK, COMPANY_PLOT_REQUIRED,
            PRICE_LEVEL_MIN, PRICE_LEVEL_MAX, PRICE_LEVEL_DAMPING, COST_MUNICIPALITY, COST_COMPANY, COST_LEVELS,
            COST_LEVEL_DEFAULT, REQUIRE_PREVIOUS);

    private static final String HEADER = """
            Eddies Civ Mod
            ticks_per_day: Laenge eines Zivilisationstags in Ticks (24000 = ein Minecraft-Tag)
            claim_base: Chunks, die jede Gemeinde beanspruchen darf; claim_per_member: zusaetzlich je Mitglied
            price_reference_emeralds_per_player: Geldmenge je Spieler, bei der Marktpreise ihrem Grundpreis entsprechen
            villager_income_hundredths_per_day: Einnahmen der Gemeindekasse je Dorfbewohner und Tag (100 = 1 Smaragd)
            market_town_inhabitants, city_inhabitants, metropolis_inhabitants: Einwohner fuer die Siedlungsstufen
            villager_emigration: ob sehr unzufriedene Dorfbewohner ihre Gemeinde verlassen (true/false)
            villager_politics_enabled: ob Dorfbewohner ohne Spieler-Buergermeister kommissarisch regieren,
              Buenden beitreten/gruenden und Firmen gruenden duerfen (true/false)
            villager_ambitions: ob ehrgeizige Dorfbewohner Firmen, Parteien und Buende gruenden, kandidieren, Gebiet
              beanspruchen, Firmen uebernehmen und fuehren (true/false)
            law_drafts_persist: Gesetzesvorlagen des Web-Editors dauerhaft speichern (true/false, Standard false:
              sie gehen sonst beim Neustart verloren)
            war_port_lock: wer sich im Krieg nicht mehr zu den Heimat-Haefen einer kriegfuehrenden Polity porten darf:
              all-outsiders (alle Nicht-Buerger, Standard), enemies (nur Buerger der Kriegsgegner) oder none (keine Sperre)
            federation_levels: eigene Ebenen fuer Buende als id:Name:Rang, z. B. kreis:Kreis:10,bund:Bund:40
              (eingebaut: gemeinde 0, land 20, nation 30; ein Bund steht immer hoeher als seine Glieder)
            villager_parties: ob Dorfbewohner Parteien beitreten und gruenden (true/false)
            villager_party_founders: wie viele parteilose Gleichgesinnte einer Gemeinde eine Partei gruenden (ab 2)
            founding_cost_municipality, founding_cost_company: Gruendungskosten in Smaragden (0 = kostenlos)
            founding_cost_levels: Gruendungskosten je Bundebene als id:Smaragde, z. B. land:200,nation:1000
            founding_cost_level_default: Kosten einer Ebene, die in founding_cost_levels fehlt
            founding_require_previous_level: Bund nur aus der Ebene direkt darunter (Land aus Gemeinde, Nation aus Land)
            web_enabled, web_bind, web_port: eingebettete Webseite; web_public_url: Adresse fuer Spieler (optional)
            web_bind=127.0.0.1 macht die Webseite nur auf diesem Rechner erreichbar (fuer einen Reverse-Proxy mit HTTPS)
            web_trust_proxy: true nur hinter einem Reverse-Proxy (nginx, Caddy): dann gelten X-Forwarded-For/-Proto
            live_count: Wahlergebnisse als Live-Auszaehlung mit Zwischenstaenden (true/false)
            live_count_seconds: Dauer der Live-Auszaehlung (5-600); live_count_projections: Hochrechnungen (true/false)
            debate_minutes: Laenge einer Wahldebatte in Minuten (0 = keine Debatten)
            anthem_on_enter: Hymne beim Betreten einer Gemeinde abspielen (true/false)
            world_map: gerenderte Weltkarte auf der Webseite (true/false); world_map_radius_chunks: Umkreis um Spieler
            price_level_min, price_level_max: Grenzen des Marktpreisfaktors (Standard 0.5 .. 4.0)
            price_level_damping: Daempfung der Preisreaktion auf die Geldmenge je Kopf (Standard 0.5 = Quadratwurzel;
              1.0 liesse Preise proportional zur Geldmenge je Kopf schwanken, staerkere Inflation/Deflation)""";

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
        ServerSettings settings = of(properties);
        if (KEYS.stream().anyMatch(key -> !properties.containsKey(key))) {
            // Neue Einstellungen einer neueren Version mit ihren Standardwerten ergänzen.
            write(file, settings);
        }
        return settings;
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
                properties.getProperty(WEB_URL, defaults.web().publicUrl()).strip(),
                Boolean.parseBoolean(properties.getProperty(WEB_TRUST_PROXY,
                        Boolean.toString(defaults.web().trustProxy())).strip()));
        return new ServerSettings(ticks, claimBase, claimPerMember, defaults.currency(),
                Money.of(MoneyFormat.emeralds(reference), defaults.currency()), Money.of(income, defaults.currency()),
                tiers, web, Boolean.parseBoolean(properties.getProperty(EMIGRATION,
                        Boolean.toString(defaults.emigration())).strip()),
                flag(properties, VILLAGER_POLITICS, defaults.villagerPolitics()),
                Levels.parse(properties.getProperty(LEVELS, defaults.levels().spec())),
                new ServerSettings.PartySettings(
                        Boolean.parseBoolean(properties.getProperty(VILLAGER_PARTIES,
                                Boolean.toString(defaults.parties().villagers())).strip()),
                        (int) number(properties, PARTY_FOUNDERS, defaults.parties().founders(), 2)),
                show(properties, defaults.show()), economy(properties, defaults.economy()),
                flag(properties, AMBITIONS, defaults.ambitions()),
                ServerSettings.WarPortLock.parse(properties.getProperty(WAR_PORT_LOCK,
                        defaults.warPortLock().key()), defaults.warPortLock()),
                founding(properties, defaults.founding()), flag(properties, LAW_DRAFTS, defaults.persistLawDrafts()));
    }

    private static de.etiennebader.civ.server.founding.FoundingFees founding(Properties properties,
            de.etiennebader.civ.server.founding.FoundingFees defaults) {
        java.util.Map<String, Long> byLevel = new java.util.LinkedHashMap<>();
        String spec = properties.getProperty(COST_LEVELS, levelCosts(defaults));
        for (String entry : spec.split(",")) {
            String[] parts = entry.strip().split(":");
            if (parts.length == 2) {
                try {
                    long emeralds = Long.parseLong(parts[1].strip());
                    if (emeralds >= 0) {
                        byLevel.put(parts[0].strip().toLowerCase(java.util.Locale.ROOT),
                                MoneyFormat.emeralds(emeralds));
                    }
                } catch (NumberFormatException e) {
                    // fehlerhafter Eintrag: überspringen
                }
            }
        }
        return new de.etiennebader.civ.server.founding.FoundingFees(
                MoneyFormat.emeralds(number(properties, COST_MUNICIPALITY,
                        defaults.municipality() / MoneyFormat.UNITS_PER_EMERALD, 0)),
                MoneyFormat.emeralds(number(properties, COST_COMPANY,
                        defaults.company() / MoneyFormat.UNITS_PER_EMERALD, 0)),
                byLevel,
                MoneyFormat.emeralds(number(properties, COST_LEVEL_DEFAULT,
                        defaults.federationDefault() / MoneyFormat.UNITS_PER_EMERALD, 0)),
                flag(properties, REQUIRE_PREVIOUS, defaults.requirePreviousLevel()));
    }

    private static String levelCosts(de.etiennebader.civ.server.founding.FoundingFees fees) {
        return String.join(",", fees.federationByLevel().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + entry.getValue() / MoneyFormat.UNITS_PER_EMERALD).toList());
    }

    private static ServerSettings.EconomySettings economy(Properties properties, ServerSettings.EconomySettings defaults) {
        try {
            return new ServerSettings.EconomySettings(
                    decimal(properties, PRICE_LEVEL_MIN, defaults.priceLevelMin()),
                    decimal(properties, PRICE_LEVEL_MAX, defaults.priceLevelMax()),
                    decimal(properties, PRICE_LEVEL_DAMPING, defaults.priceLevelDamping()),
                    flag(properties, COMPANY_PLOT_REQUIRED, defaults.companyPlotRequired()));
        } catch (IllegalArgumentException e) {
            return defaults;
        }
    }

    private static ServerSettings.ShowSettings show(Properties properties, ServerSettings.ShowSettings defaults) {
        try {
            return new ServerSettings.ShowSettings(
                    flag(properties, LIVE_COUNT, defaults.liveCount()),
                    (int) Math.min(600, number(properties, LIVE_COUNT_SECONDS, defaults.liveCountSeconds(), 5)),
                    flag(properties, PROJECTIONS, defaults.projections()),
                    (int) Math.min(60, number(properties, DEBATE_MINUTES, defaults.debateMinutes(), 0)),
                    flag(properties, ANTHEM_ON_ENTER, defaults.anthemOnEnter()),
                    flag(properties, WORLD_MAP, defaults.worldMap()),
                    (int) Math.min(32, number(properties, MAP_RADIUS, defaults.mapRadius(), 1)));
        } catch (IllegalArgumentException e) {
            return defaults;
        }
    }

    private static boolean flag(Properties properties, String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)).strip());
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
        properties.setProperty(EMIGRATION, Boolean.toString(settings.emigration()));
        properties.setProperty(VILLAGER_POLITICS, Boolean.toString(settings.villagerPolitics()));
        properties.setProperty(AMBITIONS, Boolean.toString(settings.ambitions()));
        properties.setProperty(LAW_DRAFTS, Boolean.toString(settings.persistLawDrafts()));
        properties.setProperty(WAR_PORT_LOCK, settings.warPortLock().key());
        properties.setProperty(LEVELS, settings.levels().spec());
        properties.setProperty(VILLAGER_PARTIES, Boolean.toString(settings.parties().villagers()));
        properties.setProperty(PARTY_FOUNDERS, Integer.toString(settings.parties().founders()));
        properties.setProperty(COST_MUNICIPALITY, Long.toString(
                settings.founding().municipality() / MoneyFormat.UNITS_PER_EMERALD));
        properties.setProperty(COST_COMPANY, Long.toString(settings.founding().company() / MoneyFormat.UNITS_PER_EMERALD));
        properties.setProperty(COST_LEVELS, levelCosts(settings.founding()));
        properties.setProperty(COST_LEVEL_DEFAULT, Long.toString(
                settings.founding().federationDefault() / MoneyFormat.UNITS_PER_EMERALD));
        properties.setProperty(REQUIRE_PREVIOUS, Boolean.toString(settings.founding().requirePreviousLevel()));
        properties.setProperty(WEB_ENABLED, Boolean.toString(settings.web().enabled()));
        properties.setProperty(WEB_BIND, settings.web().bind());
        properties.setProperty(WEB_PORT, Integer.toString(settings.web().port()));
        properties.setProperty(WEB_URL, settings.web().publicUrl());
        properties.setProperty(WEB_TRUST_PROXY, Boolean.toString(settings.web().trustProxy()));
        properties.setProperty(LIVE_COUNT, Boolean.toString(settings.show().liveCount()));
        properties.setProperty(LIVE_COUNT_SECONDS, Integer.toString(settings.show().liveCountSeconds()));
        properties.setProperty(PROJECTIONS, Boolean.toString(settings.show().projections()));
        properties.setProperty(DEBATE_MINUTES, Integer.toString(settings.show().debateMinutes()));
        properties.setProperty(ANTHEM_ON_ENTER, Boolean.toString(settings.show().anthemOnEnter()));
        properties.setProperty(WORLD_MAP, Boolean.toString(settings.show().worldMap()));
        properties.setProperty(MAP_RADIUS, Integer.toString(settings.show().mapRadius()));
        properties.setProperty(COMPANY_PLOT_REQUIRED, Boolean.toString(settings.economy().companyPlotRequired()));
        properties.setProperty(PRICE_LEVEL_MIN, Double.toString(settings.economy().priceLevelMin()));
        properties.setProperty(PRICE_LEVEL_MAX, Double.toString(settings.economy().priceLevelMax()));
        properties.setProperty(PRICE_LEVEL_DAMPING, Double.toString(settings.economy().priceLevelDamping()));
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

    private static double decimal(Properties properties, String key, double fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
