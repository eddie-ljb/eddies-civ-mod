package de.etiennebader.civ.server.road;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Street;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Wegebau auf Straßen. Bürgermeister und Bauamt (Spieler wie Dorfbewohner) lassen auf einer benannten Straße einen
 * Weg anlegen: entweder mit der Schaufel (kostenlos, langsam) oder gepflastert (kostet die Gemeindekasse, schnell).
 * Gezahlt wird nach Baufortschritt an die Baufirma der Gemeinde, falls es eine gibt; das Spiel holt sich mit
 * {@link #advance} ab, welche Felder jetzt zu belegen sind.
 */
public final class RoadService {

    /** Wegfelder je Tag mit der Schaufel (ein Villager trampelt langsam). */
    public static final int SHOVEL_PER_DAY = 48;
    /** Wegfelder je Tag mit Pflaster. */
    public static final int PAVING_PER_DAY = 160;
    /** Preis je Pflasterstein in kleinster Einheit. */
    public static final long PAVING_PRICE = 20;
    static final int MAX_PER_CALL = 64;

    /** Die Felder, die das Spiel jetzt belegen soll. */
    public record Work(UUID job, DimensionId dimension, RoadJob.Material material, List<int[]> cells) {
    }

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final long ticksPerDay;
    private final Announcements announcements;
    private final Optional<Path> file;
    private final Map<UUID, RoadJob> jobs = new LinkedHashMap<>();
    private final Map<UUID, Double> carry = new java.util.HashMap<>();

    public RoadService(CivApplication app, Calendar calendar, long ticksPerDay, Announcements announcements,
                       Optional<Path> file) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.ticksPerDay = Math.max(1, ticksPerDay);
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.file = Objects.requireNonNull(file, "file");
        load();
    }

    public synchronized List<RoadJob> jobs() {
        return new ArrayList<>(jobs.values());
    }

    /** Ob auf der Straße schon gebaut wird oder gebaut wurde. */
    public synchronized boolean hasJob(PolityId town, String street) {
        return jobs.values().stream().anyMatch(job -> job.town().equals(town) && job.street().equalsIgnoreCase(street));
    }

    /**
     * Beauftragt den Wegebau auf einer Straße ohne weitere Prüfung der Berechtigung (Dorfbewohner als Bauamt).
     *
     * @param paving ob gepflastert wird; sonst mit der Schaufel
     * @return ob ein Auftrag entstand
     */
    public synchronized boolean start(PolityView town, Street street, boolean paving) {
        if (hasJob(town.id(), street.name())) {
            return false;
        }
        RoadJob job = new RoadJob(UUID.randomUUID(), town.id(), street.name(), street.area(),
                paving ? RoadJob.Material.PAVING : RoadJob.Material.PATH, 0, 0, false);
        jobs.put(job.id(), job);
        save();
        return true;
    }

    /** Ob die Kasse einen gepflasterten Weg auf der Straße tragen kann (mit Reserve). */
    public boolean canAffordPaving(PolityView town, Street street) {
        long cost = street.area().width() * street.area().depth() * PAVING_PRICE;
        return queries.ledger().balanceOf(town.asActor(), town.currency()).amount() >= cost * 2;
    }

    /** {@code /civ strasse bauen <Name> [schaufel|pflaster]} */
    public synchronized Reply order(Caller caller, String streetName, String mode) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.ROAD_NO_TOWN));
        }
        PolityView polity = town.get();
        if (!polity.holds(caller.player(), Office.MAYOR) && !polity.holds(caller.player(), Office.PLANNER)) {
            return Reply.failure(Message.of(MessageKey.ROAD_NOT_PLANNER));
        }
        Optional<Street> street = polity.streets().stream()
                .filter(found -> found.name().equalsIgnoreCase(streetName.strip())).findFirst();
        if (street.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.STREET_UNKNOWN, streetName.strip()));
        }
        if (hasJob(polity.id(), street.get().name())) {
            return Reply.failure(Message.of(MessageKey.ROAD_ALREADY, street.get().name()));
        }
        boolean paving = mode.toLowerCase(Locale.ROOT).startsWith("pfl") || mode.toLowerCase(Locale.ROOT).startsWith("pav")
                || mode.toLowerCase(Locale.ROOT).startsWith("item");
        start(polity, street.get(), paving);
        return Reply.success(Message.of(MessageKey.ROAD_ORDERED, street.get().name(),
                Message.of(paving ? MessageKey.ROAD_MODE_PAVING : MessageKey.ROAD_MODE_PATH)));
    }

    /** {@code /civ strasse wege}: der Stand aller Wegebauten der eigenen Gemeinde. */
    public synchronized Reply list(Caller caller) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.ROAD_NO_TOWN));
        }
        List<Message> lines = new ArrayList<>();
        for (RoadJob job : jobs.values()) {
            if (job.town().equals(town.get().id())) {
                lines.add(Message.of(MessageKey.ROAD_ENTRY, job.street(),
                        Message.of(job.material() == RoadJob.Material.PAVING ? MessageKey.ROAD_MODE_PAVING
                                : MessageKey.ROAD_MODE_PATH), job.total() == 0 ? 100 : job.done() * 100 / job.total()));
            }
        }
        return lines.isEmpty() ? Reply.success(Message.of(MessageKey.ROAD_NONE)) : Reply.success(lines);
    }

    /**
     * Die Felder, die in den letzten {@code ticks} Ticks hinzukommen. Gepflasterte Wege bezahlt die Gemeindekasse nach
     * Fortschritt; reicht das Geld nicht, ruht die Baustelle.
     */
    public synchronized List<Work> advance(long ticks) {
        List<Work> work = new ArrayList<>();
        for (RoadJob job : new ArrayList<>(jobs.values())) {
            if (job.finished()) {
                continue;
            }
            double rate = job.material() == RoadJob.Material.PAVING ? PAVING_PER_DAY : SHOVEL_PER_DAY;
            double budget = carry.getOrDefault(job.id(), 0.0) + rate * ticks / ticksPerDay;
            int count = (int) Math.min(Math.min(budget, MAX_PER_CALL), job.total() - job.done());
            if (count <= 0) {
                carry.put(job.id(), budget);
                continue;
            }
            long due = 0;
            if (job.material() == RoadJob.Material.PAVING) {
                due = count * PAVING_PRICE;
                if (!pay(job, due)) {
                    carry.put(job.id(), Math.min(budget, MAX_PER_CALL));
                    continue;
                }
            }
            List<int[]> cells = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                cells.add(job.cell(job.done() + i));
            }
            carry.put(job.id(), budget - count);
            RoadJob next = job.progressed(count, due);
            jobs.put(job.id(), next);
            work.add(new Work(job.id(), job.area().dimension(), job.material(), cells));
            if (next.finished()) {
                announcements.add(Announcement.toTown(job.town(), Message.of(MessageKey.ROAD_FINISHED, job.street())));
            }
        }
        if (!work.isEmpty()) {
            save();
        }
        return work;
    }

    private boolean pay(RoadJob job, long amount) {
        Optional<PolityView> town = queries.polity(job.town());
        if (town.isEmpty()) {
            return false;
        }
        ActorRef recipient = queries.companies().stream()
                .filter(company -> queries.production().enterpriseOf(company.id())
                        .map(e -> e.branch() == de.etiennebader.civ.domain.production.Branch.CONSTRUCTION).orElse(false))
                .findFirst().<ActorRef>map(PolityView::asActor).orElse(null);
        if (recipient == null) {
            return queries.ledger().balanceOf(town.get().asActor(), town.get().currency()).amount() >= amount; // Material aus der Kasse: keine Buchung nötig
        }
        try {
            app.transferFunds().execute(new TransferFunds.Command(town.get().asActor(), recipient,
                    Money.of(amount, town.get().currency()), TransferReason.CONSTRUCTION, calendar.today()));
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ Speichern

    private void load() {
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.get(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                if (f.length < 11) {
                    continue;
                }
                RoadJob job = new RoadJob(UUID.fromString(f[0]), new PolityId(UUID.fromString(f[1])), f[2],
                        new Area(new DimensionId(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                                Integer.parseInt(f[6]), Integer.parseInt(f[7])),
                        RoadJob.Material.valueOf(f[8]), Integer.parseInt(f[9]), Long.parseLong(f[10]),
                        f.length > 11 && Boolean.parseBoolean(f[11]));
                jobs.put(job.id(), job);
            }
        } catch (IOException | RuntimeException e) {
            // Beschädigte Datei: ohne Wegebauten weiter
        }
    }

    private void save() {
        if (file.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (RoadJob job : jobs.values()) {
            lines.add(String.join("\t", job.id().toString(), job.town().value().toString(), job.street(),
                    job.area().dimension().value(), Integer.toString(job.area().minX()),
                    Integer.toString(job.area().minZ()), Integer.toString(job.area().maxX()),
                    Integer.toString(job.area().maxZ()), job.material().name(), Integer.toString(job.done()),
                    Long.toString(job.paid()), Boolean.toString(job.finished())));
        }
        try {
            Path target = file.get().toAbsolutePath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // beim nächsten Fortschritt erneut versuchen
        }
    }
}
