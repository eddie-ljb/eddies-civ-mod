package de.etiennebader.civ.sim;

import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.sim.cli.CommandLineOptions;
import de.etiennebader.civ.sim.cli.UsageException;
import de.etiennebader.civ.sim.invariant.InvariantViolationException;
import de.etiennebader.civ.sim.metrics.DailyMetrics;
import de.etiennebader.civ.sim.metrics.EventLogWriter;
import de.etiennebader.civ.sim.metrics.MetricsCsvWriter;
import de.etiennebader.civ.sim.scenario.InvalidScenarioException;
import de.etiennebader.civ.sim.scenario.Scenario;
import de.etiennebader.civ.sim.scenario.ScenarioLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * Kommandozeilen-Einstieg der Headless-Simulation.
 *
 * <p>Exit-Codes: 0 Erfolg, 1 Invariante verletzt, 2 fehlerhafte Kommandozeile oder fehlerhaftes Szenario.
 */
public final class SimulationMain {

    static final int SUCCESS = 0;
    static final int INVARIANT_VIOLATED = 1;
    static final int INVALID_INPUT = 2;

    static final String METRICS_FILE = "metrics.csv";
    static final String EVENT_LOG_FILE = "events.log";

    private SimulationMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            CommandLineOptions options = CommandLineOptions.parse(args);
            if (options.help()) {
                out.print(CommandLineOptions.USAGE);
                return SUCCESS;
            }
            Scenario scenario = applyOverrides(new ScenarioLoader().load(options.scenario()), options);
            return simulate(scenario, options, out, err);
        } catch (UsageException e) {
            err.println(e.getMessage());
            err.print(CommandLineOptions.USAGE);
            return INVALID_INPUT;
        } catch (InvalidScenarioException e) {
            err.println(e.getMessage());
            return INVALID_INPUT;
        }
    }

    private static Scenario applyOverrides(Scenario scenario, CommandLineOptions options) {
        Scenario result = scenario;
        if (options.seed().isPresent()) {
            result = result.withSeed(options.seed().getAsLong());
        }
        if (options.days().isPresent()) {
            result = result.withDays(options.days().getAsInt());
        }
        return result;
    }

    private static int simulate(Scenario scenario, CommandLineOptions options, PrintStream out, PrintStream err) {
        Path outputDirectory = options.output().toAbsolutePath();
        out.printf(Locale.ROOT, "Szenario %s, Seed %d, %d Tage, %d Akteure%n",
                scenario.name(), scenario.seed(), scenario.days(), scenario.population().total());
        try {
            Files.createDirectories(outputDirectory);
            try (Writer metricsFile = writer(outputDirectory.resolve(METRICS_FILE));
                 Writer eventFile = options.eventLog() ? writer(outputDirectory.resolve(EVENT_LOG_FILE)) : Writer.nullWriter()) {
                DomainEventPublisher eventLog = options.eventLog() ? new EventLogWriter(eventFile) : DomainEventPublisher.NONE;
                try (SaveTarget saveTarget = SaveTarget.open(options)) {
                    long start = System.nanoTime();
                    SimulationResult result = new Simulation(scenario, eventLog, saveTarget.autosave())
                            .run(new MetricsCsvWriter(metricsFile));
                    long millis = (System.nanoTime() - start) / 1_000_000;
                    printSummary(result, millis, outputDirectory, options, out);
                    return SUCCESS;
                }
            }
        } catch (InvariantViolationException e) {
            err.println(e.getMessage());
            err.println("Kennzahlen bis zum Vortag stehen in " + outputDirectory.resolve(METRICS_FILE));
            return INVARIANT_VIOLATED;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Writer writer(Path file) throws IOException {
        return new BufferedWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
    }

    private static void printSummary(SimulationResult result, long millis, Path outputDirectory,
                                     CommandLineOptions options, PrintStream out) {
        DailyMetrics last = result.finalDay();
        double daysPerSecond = millis == 0 ? 0 : result.scenario().days() * 1000.0 / millis;
        out.printf(Locale.ROOT, "Fertig in %d ms (%.0f Tage/s), alle Invarianten erfüllt.%n", millis, daysPerSecond);
        out.printf(Locale.ROOT, "Gemeinden: %d, Mitglieder: %d, ohne Gemeinde: %d, ohne Grundstück: %d%n",
                last.polities(), last.members(), last.unaffiliated(), last.homelessMembers());
        out.printf(Locale.ROOT, "Chunks: %d (Außenposten %d), Grundstücke: %d (privat %d, Botschaften %d)%n",
                last.chunks(), last.outposts(), last.plots(), last.privatePlots(), last.embassies());
        out.printf(Locale.ROOT, "Geldmenge: %d, davon Bargeld %d, Gemeindekassen %d%n",
                last.moneySupply(), last.cash(), last.treasuries());
        out.printf(Locale.ROOT, "Events: %d%n", result.totalEvents());
        if (!result.rejections().isEmpty()) {
            out.println("Abgelehnte Vorhaben:");
            result.rejections().entrySet().stream()
                    .sorted(Map.Entry.<ErrorCode, Long>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(Map.Entry.comparingByKey()))
                    .forEach(entry -> out.printf(Locale.ROOT, "  %-24s %d%n", entry.getKey(), entry.getValue()));
        }
        out.println("Kennzahlen: " + outputDirectory.resolve(METRICS_FILE));
        if (options.eventLog()) {
            out.println("Event-Log:  " + outputDirectory.resolve(EVENT_LOG_FILE));
        }
        options.save().ifPresent(file -> out.printf(Locale.ROOT, "Gespeichert: %s (%d Speichervorgänge, %d ms, %d Events im Audit-Log)%n",
                file.toAbsolutePath(), result.saves().saves(), result.saves().totalMillis(), result.saves().events()));
    }
}
