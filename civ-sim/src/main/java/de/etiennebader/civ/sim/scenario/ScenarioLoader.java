package de.etiennebader.civ.sim.scenario;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lädt Szenarien aus Properties-Dateien. Jedes Szenario erbt von {@code defaults.properties}
 * und überschreibt nur einzelne Werte. Unbekannte Schlüssel gelten als Tippfehler und werden
 * abgelehnt; alle Werte werden auf Typ und Wertebereich geprüft.
 *
 * <p>Ein Szenario wird entweder über seinen Namen ({@code small}, {@code medium}, {@code stress})
 * aus dem Klassenpfad oder über einen Dateipfad geladen.
 */
public final class ScenarioLoader {

    private static final String RESOURCE_FOLDER = "/scenarios/";
    private static final String DEFAULTS = "defaults";

    /** Lädt ein mitgeliefertes Szenario oder, wenn es keines mit dem Namen gibt, eine Datei. */
    public Scenario load(String nameOrPath) {
        if (resourceExists(nameOrPath)) {
            return fromProperties(nameOrPath, readResource(nameOrPath));
        }
        Path file = Path.of(nameOrPath);
        if (!Files.isRegularFile(file)) {
            throw new InvalidScenarioException(nameOrPath,
                    List.of("weder mitgeliefertes Szenario (small, medium, stress) noch lesbare Datei"));
        }
        return fromProperties(file.getFileName().toString().replaceFirst("\\.properties$", ""), readFile(file));
    }

    /** Baut ein Szenario aus Werten, die die Standardwerte überschreiben. */
    public Scenario fromProperties(String name, Properties overrides) {
        Properties merged = new Properties();
        merged.putAll(readResource(DEFAULTS));
        List<String> problems = new ArrayList<>();
        Set<String> unknown = new TreeSet<>(overrides.stringPropertyNames());
        unknown.removeAll(merged.stringPropertyNames());
        unknown.forEach(key -> problems.add("unbekannter Schlüssel '" + key + "'"));
        merged.putAll(overrides);

        Values values = new Values(merged, problems);
        Scenario.Population population = new Scenario.Population(
                values.integer("population.players", 0, 100_000),
                values.integer("population.villagers", 0, 1_000_000));
        Scenario.World world = new Scenario.World(values.integer("world.radius.chunks", 1, 100_000));
        Scenario.Economy economy = new Scenario.Economy(
                values.amount("economy.starting.wealth.max"),
                values.amount("economy.income.per.day"),
                values.fraction("economy.income.spread"),
                values.amount("economy.living.cost.per.day"),
                values.amount("economy.savings.target"),
                values.amount("economy.founding.capital"),
                values.fraction("economy.cash.share"));
        Scenario.Land land = new Scenario.Land(
                values.integer("land.plot.size.min", 1, 16),
                values.integer("land.plot.size.max", 1, 16),
                values.amount("land.plot.price.per.block"),
                values.integer("land.plot.reserve", 0, 100));
        Scenario.Territory territory = new Scenario.Territory(
                values.integer("territory.claim.base", 1, 1_000_000),
                values.integer("territory.claim.per.member", 0, 1_000_000),
                values.bool("territory.require.adjacency"),
                values.integer("territory.outpost.member.threshold", 1, 1_000_000),
                values.integer("territory.max.outposts", 0, 1_000),
                values.fraction("territory.outpost.release.chance.per.day"));
        Scenario.Governance governance = new Scenario.Governance(
                values.integer("governance.tax.review.interval.days", 1, 10_000),
                values.amount("governance.tax.max.rate"),
                values.amount("governance.treasury.reserve.per.member"),
                values.amount("governance.upkeep.per.chunk.per.day"),
                values.fraction("governance.embassy.chance.per.day"));
        Scenario.Behavior behavior = new Scenario.Behavior(
                values.fraction("behavior.decision.noise"),
                values.fraction("behavior.belonging.growth.per.day"),
                values.fraction("behavior.housing.growth.per.day"),
                values.fraction("behavior.leave.threshold"),
                values.positiveFraction("behavior.tax.tolerance"));
        long seed = values.longValue("seed");
        int days = values.integer("days", 1, 1_000_000);

        if (land.plotSizeMin() > land.plotSizeMax()) {
            problems.add("land.plot.size.min darf nicht größer als land.plot.size.max sein");
        }
        if (population.total() == 0) {
            problems.add("die Bevölkerung darf nicht leer sein");
        }
        if (!problems.isEmpty()) {
            throw new InvalidScenarioException(name, problems);
        }
        return new Scenario(name, seed, days, population, world, economy, land, territory, governance, behavior);
    }

    private static boolean resourceExists(String name) {
        return name.matches("[a-z0-9_-]+") && ScenarioLoader.class.getResource(RESOURCE_FOLDER + name + ".properties") != null;
    }

    private static Properties readResource(String name) {
        String path = RESOURCE_FOLDER + name + ".properties";
        try (InputStream stream = ScenarioLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Ressource fehlt: " + path);
            }
            return read(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Properties readFile(Path file) {
        try {
            return read(Files.newBufferedReader(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Properties read(Reader reader) throws IOException {
        try (reader) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        }
    }

    /** Liest typisierte Werte und sammelt Fehler, statt beim ersten abzubrechen. */
    private record Values(Properties properties, List<String> problems) {

        long longValue(String key) {
            return parseLong(key).orElse(0);
        }

        int integer(String key, int min, int max) {
            OptionalLong value = parseLong(key);
            if (value.isEmpty()) {
                return min;
            }
            if (value.getAsLong() < min || value.getAsLong() > max) {
                problems.add(key + ": " + value.getAsLong() + " liegt nicht zwischen " + min + " und " + max);
                return min;
            }
            return (int) value.getAsLong();
        }

        long amount(String key) {
            OptionalLong value = parseLong(key);
            if (value.isPresent() && value.getAsLong() < 0) {
                problems.add(key + ": Betrag darf nicht negativ sein");
                return 0;
            }
            return value.orElse(0);
        }

        /** Leer, wenn der Wert fehlt oder keine ganze Zahl ist; der Fehler ist dann bereits vermerkt. */
        private OptionalLong parseLong(String key) {
            Optional<String> raw = raw(key);
            if (raw.isEmpty()) {
                return OptionalLong.empty();
            }
            try {
                return OptionalLong.of(Long.parseLong(raw.get()));
            } catch (NumberFormatException e) {
                problems.add(key + ": '" + raw.get() + "' ist keine ganze Zahl");
                return OptionalLong.empty();
            }
        }

        double fraction(String key) {
            Optional<String> raw = raw(key);
            if (raw.isEmpty()) {
                return 0;
            }
            try {
                double value = Double.parseDouble(raw.get());
                if (value < 0 || value > 1 || Double.isNaN(value)) {
                    problems.add(key + ": " + raw.get() + " liegt nicht zwischen 0 und 1");
                    return 0;
                }
                return value;
            } catch (NumberFormatException e) {
                problems.add(key + ": '" + raw.get() + "' ist keine Zahl");
                return 0;
            }
        }

        double positiveFraction(String key) {
            int knownProblems = problems.size();
            double value = fraction(key);
            if (value == 0 && problems.size() == knownProblems) {
                problems.add(key + ": muss größer als 0 sein");
            }
            return value == 0 ? 1 : value;
        }

        boolean bool(String key) {
            Optional<String> raw = raw(key).map(value -> value.toLowerCase(Locale.ROOT));
            if (raw.isPresent() && !raw.get().equals("true") && !raw.get().equals("false")) {
                problems.add(key + ": '" + raw.get() + "' ist weder true noch false");
            }
            return raw.map("true"::equals).orElse(false);
        }

        /** Der getrimmte Wert; fehlt der Schlüssel, wird das vermerkt. */
        private Optional<String> raw(String key) {
            String value = properties.getProperty(key);
            if (value == null) {
                problems.add("Schlüssel '" + key + "' fehlt");
                return Optional.empty();
            }
            return Optional.of(value.strip());
        }
    }
}
