package de.etiennebader.civ.sim.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Die Optionen eines Simulationslaufs.
 *
 * @param scenario  Name eines mitgelieferten Szenarios oder Pfad zu einer Properties-Datei
 * @param seed      überschreibt den Seed des Szenarios
 * @param days      überschreibt die Anzahl der Tage
 * @param output    Verzeichnis für {@code metrics.csv} und {@code events.log}
 * @param eventLog  ob alle Domain Events protokolliert werden
 * @param help      ob nur die Hilfe gezeigt wird
 */
public record CommandLineOptions(String scenario, OptionalLong seed, OptionalInt days, Path output,
                                 boolean eventLog, boolean help) {

    public static final String DEFAULT_SCENARIO = "medium";
    public static final Path DEFAULT_OUTPUT = Path.of("build", "simulation");

    public static final String USAGE = """
            Aufruf: civ-sim [Optionen]

              --scenario <name|datei>  small, medium, stress oder Pfad zu einer .properties-Datei (Standard: medium)
              --seed <zahl>            Seed überschreiben
              --days <zahl>            Anzahl Tage überschreiben
              --out <verzeichnis>      Ausgabeverzeichnis (Standard: build/simulation)
              --event-log              zusätzlich alle Domain Events nach events.log schreiben
              --help                   diese Hilfe

            Beispiel: ./gradlew :civ-sim:run --args="--scenario small --seed 7 --event-log"
            """;

    public CommandLineOptions {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(days, "days");
        Objects.requireNonNull(output, "output");
    }

    /**
     * @throws UsageException bei unbekannten Optionen oder fehlenden bzw. ungültigen Werten
     */
    public static CommandLineOptions parse(String... args) {
        String scenario = DEFAULT_SCENARIO;
        OptionalLong seed = OptionalLong.empty();
        OptionalInt days = OptionalInt.empty();
        Path output = DEFAULT_OUTPUT;
        boolean eventLog = false;
        boolean help = false;
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            switch (option) {
                case "--scenario" -> scenario = valueAfter(args, i++);
                case "--seed" -> seed = OptionalLong.of(parseLong(option, valueAfter(args, i++)));
                case "--days" -> days = OptionalInt.of(parsePositiveInt(option, valueAfter(args, i++)));
                case "--out" -> output = Path.of(valueAfter(args, i++));
                case "--event-log" -> eventLog = true;
                case "--help", "-h" -> help = true;
                default -> throw new UsageException("Unbekannte Option: " + option);
            }
        }
        return new CommandLineOptions(scenario, seed, days, output, eventLog, help);
    }

    private static String valueAfter(String[] args, int index) {
        if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
            throw new UsageException("Option " + args[index] + " braucht einen Wert");
        }
        return args[index + 1];
    }

    private static long parseLong(String option, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new UsageException(option + ": '" + value + "' ist keine ganze Zahl");
        }
    }

    private static int parsePositiveInt(String option, String value) {
        long parsed = parseLong(option, value);
        if (parsed < 1 || parsed > Integer.MAX_VALUE) {
            throw new UsageException(option + ": muss zwischen 1 und " + Integer.MAX_VALUE + " liegen");
        }
        return (int) parsed;
    }
}
