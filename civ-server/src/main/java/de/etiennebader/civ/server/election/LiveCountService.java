package de.etiennebader.civ.server.election;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Simulierte Live-Auszählung: Das Ergebnis steht nach der Auszählung schon fest, wird aber in Schritten enthüllt.
 * Frühe Zwischenstände weichen deutlich vom Endergebnis ab, Hochrechnungen weniger; am Ende kommt das amtliche
 * Ergebnis. Die Abweichungen sind je Wahl fest (aus ihrer Polity und ihrem Amt), damit ein Neustart nichts ändert.
 */
public final class LiveCountService {

    /** Anteile, bei denen ein Zwischenstand gemeldet wird; der letzte ist das Endergebnis. */
    static final double[] STEPS = {0.1, 0.25, 0.45, 0.65, 0.85, 1.0};
    private static final double EARLY_NOISE = 0.30;
    private static final double PROJECTION_NOISE = 0.08;

    /**
     * Ein Zwischenstand für Anzeigen (Bossbar, Webseite).
     *
     * @param counted     ausgezählter Anteil von 0 bis 1
     * @param shares      Anteil je Kandidat bzw. Liste in Prozent, nach Stimmen geordnet
     * @param projection  Hochrechnung in Prozent; leer ohne Hochrechnung
     * @param seats       Sitzprojektion bzw. Sitze je Liste (nur Parlamentswahl)
     */
    public record LiveView(PolityId polity, String polityName, Message office, double counted,
                           Map<String, Double> shares, Map<String, Double> projection, Map<String, Integer> seats,
                           boolean finished) {
    }

    private static final class Tally {
        final PolityId polity;
        final String polityName;
        final Message office;
        final Map<String, Long> totals;
        final Map<String, Integer> seats;
        final List<Message> finale;
        final long start;
        final long duration;
        final long seed;
        int step = -1;
        LiveView view;

        Tally(PolityId polity, String polityName, Message office, Map<String, Long> totals, Map<String, Integer> seats,
              List<Message> finale, long start, long duration) {
            this.polity = polity;
            this.polityName = polityName;
            this.office = office;
            this.totals = totals;
            this.seats = seats;
            this.finale = finale;
            this.start = start;
            this.duration = duration;
            this.seed = polity.value().getLeastSignificantBits() ^ office.key().hashCode() * 31L;
        }
    }

    private final Announcements announcements;
    private final boolean enabled;
    private final long durationMillis;
    private final boolean projections;
    private final List<Tally> running = new ArrayList<>();
    private final List<LiveView> recent = new ArrayList<>();

    public LiveCountService(Announcements announcements, boolean enabled, int seconds, boolean projections) {
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.enabled = enabled;
        this.durationMillis = seconds * 1000L;
        this.projections = projections;
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * Startet eine Live-Auszählung. Ist sie abgeschaltet, erscheint gleich das Endergebnis.
     *
     * @param totals Stimmen je Kandidat bzw. Liste (Anzeigename)
     * @param seats  Sitze je Liste bei Parlamentswahlen, sonst leer
     * @param finale die Meldungen des Endergebnisses
     */
    public synchronized void start(PolityId polity, String polityName, Message office, Map<String, Long> totals,
                                   Map<String, Integer> seats, List<Message> finale, long now) {
        if (!enabled || totals.values().stream().mapToLong(Long::longValue).sum() == 0) {
            finale.forEach(message -> announcements.add(Announcement.toTown(polity, message)));
            return;
        }
        running.add(new Tally(polity, polityName, office, new LinkedHashMap<>(totals), new LinkedHashMap<>(seats),
                List.copyOf(finale), now, durationMillis));
        announcements.add(Announcement.toTown(polity, Message.of(MessageKey.LIVE_COUNT_STARTED, polityName, office)));
    }

    /** Meldet fällige Zwischenstände; am Ende das Endergebnis. */
    public synchronized void tick(long now) {
        List<Tally> done = new ArrayList<>();
        for (Tally tally : running) {
            double elapsed = Math.min(1, (now - tally.start) / (double) tally.duration);
            int due = -1;
            for (int index = 0; index < STEPS.length; index++) {
                if (elapsed >= STEPS[index]) {
                    due = index;
                }
            }
            if (due <= tally.step) {
                continue;
            }
            tally.step = due;
            double counted = STEPS[due];
            boolean last = due == STEPS.length - 1;
            Map<String, Double> shares = shares(tally, counted, EARLY_NOISE);
            Map<String, Double> projection = projections && counted >= 0.25 && !last
                    ? shares(tally, counted, PROJECTION_NOISE) : Map.of();
            Map<String, Integer> seats = tally.seats.isEmpty() || counted < 0.45 ? Map.of() : tally.seats;
            tally.view = new LiveView(tally.polity, tally.polityName, tally.office, counted, shares, projection, seats,
                    last);
            if (last) {
                tally.finale.forEach(message -> announcements.add(Announcement.toTown(tally.polity, message)));
                done.add(tally);
                continue;
            }
            announcements.add(Announcement.toTown(tally.polity, Message.of(MessageKey.LIVE_COUNT_STEP,
                    tally.polityName, Math.round(counted * 100), format(shares))));
            if (!projection.isEmpty()) {
                String leader = projection.keySet().iterator().next();
                announcements.add(Announcement.toTown(tally.polity, seats.isEmpty()
                        ? Message.of(MessageKey.LIVE_COUNT_PROJECTION, format(projection), leader)
                        : Message.of(MessageKey.LIVE_COUNT_SEATS, format(projection), seats.entrySet().stream()
                        .filter(entry -> entry.getValue() > 0)
                        .map(entry -> entry.getKey() + " " + entry.getValue()).collect(Collectors.joining(", ")))));
            }
        }
        running.removeAll(done);
        done.forEach(tally -> {
            recent.add(tally.view);
            while (recent.size() > 10) {
                recent.removeFirst();
            }
        });
    }

    /** Laufende Auszählungen mit ihrem letzten Zwischenstand; vor dem ersten Schritt noch ohne Zahlen. */
    public synchronized List<LiveView> running() {
        List<LiveView> views = new ArrayList<>();
        for (Tally tally : running) {
            views.add(tally.view != null ? tally.view : new LiveView(tally.polity, tally.polityName, tally.office, 0,
                    Map.of(), Map.of(), Map.of(), false));
        }
        return views;
    }

    /** Zuletzt beendete Auszählungen, die neueste zuletzt. */
    public synchronized List<LiveView> recent() {
        return List.copyOf(recent);
    }

    /** Anteile in Prozent mit fester, zum Ende hin schwindender Abweichung. */
    private static Map<String, Double> shares(Tally tally, double counted, double noise) {
        Map<String, Double> skewed = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, Long> entry : tally.totals.entrySet()) {
            double wobble = counted >= 1 ? 0 : noise * (1 - counted) * deviation(tally.seed, index++, counted);
            skewed.put(entry.getKey(), Math.max(0, entry.getValue() * (1 + wobble)));
        }
        double sum = skewed.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Double> shares = new LinkedHashMap<>();
        skewed.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(entry -> shares.put(entry.getKey(), sum == 0 ? 0 : entry.getValue() * 100 / sum));
        return shares;
    }

    /** Eine feste Abweichung von -1 bis 1 je Kandidat, die sich zwischen den Schritten langsam ändert. */
    static double deviation(long seed, int candidate, double counted) {
        long mixed = seed * 0x9E3779B97F4A7C15L + candidate * 0xC2B2AE3D27D4EB4FL;
        mixed ^= mixed >>> 31;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 29;
        double base = (mixed & 0xFFFF) / 32_767.5 - 1;
        return Math.max(-1, Math.min(1, base + Math.sin(counted * 7 + candidate) * 0.25));
    }

    static String format(Map<String, Double> shares) {
        return shares.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.naturalOrder()).reversed())
                .limit(6)
                .map(entry -> entry.getKey() + " " + String.format(java.util.Locale.GERMANY, "%.1f %%", entry.getValue()))
                .collect(Collectors.joining(" · "));
    }
}
