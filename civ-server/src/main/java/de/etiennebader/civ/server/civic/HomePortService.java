package de.etiennebader.civ.server.civic;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.civic.CivicUseCases;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.civic.HomePort;
import de.etiennebader.civ.domain.civic.PortAccess;
import de.etiennebader.civ.domain.civic.PortSettings;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.war.WarView;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Texts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Heimat-Häfen: Jede Polity hat einen ersten Hafen auf ihrem Gemeindehaus (dem Heimat-Grundstück) und kann zwei
 * weitere auf ihrem Gebiet setzen. Sie entscheidet selbst, wer sich dorthin porten darf und was es kostet.
 * Das Teleportieren selbst übernimmt der Minecraft-Adapter: {@link #prepare} prüft Recht und Ziel,
 * {@link #pay} bucht den Preis erst, wenn ein sicherer Landeplatz feststeht.
 */
public final class HomePortService {

    /** Ein genehmigtes, noch nicht bezahltes Ziel. */
    public record Trip(PolityView polity, int slot, BlockPosition target, long fee) {
    }

    /** Ergebnis von {@link #prepare}: entweder ein Ziel oder die Ablehnung samt Grund. */
    public record Outcome(Optional<Trip> trip, Optional<Reply> refusal) {

        static Outcome of(Trip trip) {
            return new Outcome(Optional.of(trip), Optional.empty());
        }

        static Outcome refused(Message message) {
            return new Outcome(Optional.empty(), Optional.of(Reply.failure(message)));
        }
    }

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final Texts texts;
    private final Supplier<WarView> wars;
    private final LongSupplier clock;
    private final ServerSettings.WarPortLock warLock;
    /** Zeitpunkt (ms) des letzten Teleports je Spieler; nur im Speicher, ein Neustart setzt die Wartezeit zurück. */
    private final Map<ActorRef, Long> lastTrip = new ConcurrentHashMap<>();

    public HomePortService(CivApplication app, Calendar calendar, Texts texts, Supplier<WarView> wars,
                           ServerSettings.WarPortLock warLock) {
        this(app, calendar, texts, wars, warLock, System::currentTimeMillis);
    }

    HomePortService(CivApplication app, Calendar calendar, Texts texts, Supplier<WarView> wars,
                    ServerSettings.WarPortLock warLock, LongSupplier clock) {
        this.warLock = Objects.requireNonNull(warLock, "warLock");
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.wars = Objects.requireNonNull(wars, "wars");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ---- Einstellungen der Polity (Spitze) ----

    /** {@code <Ebene> heim setzen <2|3>}: der Hafen kommt an die Stelle, an der der Spieler steht. */
    public Reply set(Caller caller, PolityView polity, int slot) {
        if (!isExtraSlot(slot)) {
            return Reply.failure(Message.of(MessageKey.PORT_SLOT_INVALID));
        }
        BlockPosition here = caller.position();
        if (!onTerritoryOf(polity.id(), here)) {
            return Reply.failure(Message.of(MessageKey.PORT_OUTSIDE_TERRITORY, polity.name().value()));
        }
        return attempt(() -> {
            app.civic().setPort(caller.player(), polity.id(), slot, here, calendar.today());
            return Reply.success(Message.of(MessageKey.PORT_SET, polity.name().value(), slot, describe(here)));
        });
    }

    public Reply remove(Caller caller, PolityView polity, int slot) {
        if (!isExtraSlot(slot)) {
            return Reply.failure(Message.of(MessageKey.PORT_SLOT_INVALID));
        }
        return attempt(() -> {
            boolean removed = app.civic().removePort(caller.player(), polity.id(), slot, calendar.today());
            return removed
                    ? Reply.success(Message.of(MessageKey.PORT_REMOVED, polity.name().value(), slot))
                    : Reply.failure(Message.of(MessageKey.PORT_REMOVED_NONE, polity.name().value(), slot));
        });
    }

    public Reply setAccess(Caller caller, PolityView polity, String input) {
        Optional<PortAccess> access = PortAccess.parse(input);
        if (access.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PORT_ACCESS_INVALID, input.strip()));
        }
        return attempt(() -> {
            app.civic().setPortAccess(caller.player(), polity.id(), access.get(), calendar.today());
            return Reply.success(Message.of(MessageKey.PORT_ACCESS_SET, polity.name().value(),
                    access.get().key()));
        });
    }

    /** {@code <Ebene> heim preis <Betrag>}; {@code 0}, {@code frei} oder {@code kostenlos} macht den Port gratis. */
    public Reply setFee(Caller caller, PolityView polity, String input) {
        long fee;
        String wanted = input.strip().toLowerCase(Locale.ROOT);
        try {
            fee = wanted.equals("0") || wanted.equals("frei") || wanted.equals("kostenlos") || wanted.equals("free")
                    ? 0 : MoneyFormat.parse(wanted);
            if (fee > PortSettings.MAX_FEE) {
                throw new IllegalArgumentException("zu hoch");
            }
        } catch (IllegalArgumentException e) {
            return Reply.failure(Message.of(MessageKey.PORT_FEE_INVALID,
                    texts.money(Money.of(PortSettings.MAX_FEE, polity.currency()))));
        }
        long chosen = fee;
        return attempt(() -> {
            app.civic().setPortFee(caller.player(), polity.id(), chosen, calendar.today());
            return Reply.success(Message.of(MessageKey.PORT_FEE_SET, polity.name().value(),
                    texts.money(Money.of(chosen, polity.currency()))));
        });
    }

    /** {@code <Ebene> heim liste}: die Häfen der Polity mit Zugang und Preis. */
    public Reply list(PolityView polity) {
        PortSettings settings = queries.civic().portSettingsOf(polity.id());
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.PORT_HEADER, polity.name().value(), settings.access().key(),
                texts.money(Money.of(settings.fee(), polity.currency()))));
        lines.add(polity.seat().map(seat -> Message.of(MessageKey.PORT_ENTRY_SEAT, describe(seat)))
                .orElseGet(() -> Message.of(MessageKey.PORT_NONE, polity.name().value(), 1)));
        for (int slot = HomePort.FIRST_EXTRA_SLOT; slot <= HomePort.LAST_EXTRA_SLOT; slot++) {
            int number = slot;
            lines.add(queries.civic().port(polity.id(), slot)
                    .map(found -> Message.of(MessageKey.PORT_ENTRY_EXTRA, number, describe(found.position())))
                    .orElseGet(() -> Message.of(MessageKey.PORT_ENTRY_EMPTY, number, number)));
        }
        return Reply.success(lines);
    }

    // ---- Reisen ----

    /**
     * Prüft, ob der Spieler zu diesem Hafen darf, und liefert das Ziel. {@code input} ist leer (eigene Gemeinde,
     * Slot 1), eine Zahl 1–3 (eigene Gemeinde) oder ein Polity-Name, optional gefolgt von der Slotnummer.
     */
    public Outcome prepare(Caller caller, Optional<String> input) {
        PolityView polity;
        int slot = 1;
        String text = input.map(String::strip).orElse("");
        if (text.isEmpty() || text.matches("[1-3]")) {
            if (!text.isEmpty()) {
                slot = Integer.parseInt(text);
            }
            Optional<PolityView> own = queries.municipalityOf(caller.player());
            if (own.isEmpty()) {
                return Outcome.refused(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
            }
            polity = own.get();
        } else {
            Optional<PolityView> named = queries.polityNamed(text);
            int split = text.lastIndexOf(' ');
            if (named.isEmpty() && split > 0 && text.substring(split + 1).matches("[1-3]")) {
                slot = Integer.parseInt(text.substring(split + 1));
                named = queries.polityNamed(text.substring(0, split).strip());
            }
            if (named.isEmpty()) {
                return Outcome.refused(Message.of(MessageKey.PORT_UNKNOWN_POLITY, text));
            }
            polity = named.get();
        }
        if (!isHead(caller, polity) && !isCitizen(caller, polity) && lockedByWar(caller, polity)) {
            return Outcome.refused(Message.of(MessageKey.PORT_WAR, polity.name().value()));
        }
        if (!mayUse(caller, polity)) {
            return Outcome.refused(Message.of(MessageKey.PORT_NOT_ALLOWED, polity.name().value()));
        }
        Optional<BlockPosition> target = slot == 1 ? polity.seat() : validExtra(polity, slot);
        if (target.isEmpty()) {
            return Outcome.refused(Message.of(MessageKey.PORT_NONE, polity.name().value(), slot));
        }
        long waitSeconds = cooldownLeft(caller, polity);
        if (waitSeconds > 0) {
            return Outcome.refused(Message.of(MessageKey.PORT_COOLDOWN, waitSeconds, polity.name().value()));
        }
        long fee = isHead(caller, polity) ? 0 : queries.civic().portSettingsOf(polity.id()).fee();
        return Outcome.of(new Trip(polity, slot, target.get(), fee));
    }

    /** Bucht den Preis des Teleports vom Spieler an die Polity. Schlägt fehl, wenn das Geld nicht reicht. */
    public Reply pay(Caller caller, Trip trip) {
        if (trip.fee() <= 0) {
            lastTrip.put(caller.player(), clock.getAsLong());
            return Reply.success(Message.of(MessageKey.PORT_ARRIVED, trip.polity().name().value()));
        }
        Money price = Money.of(trip.fee(), trip.polity().currency());
        try {
            app.transferFunds().execute(new TransferFunds.Command(caller.player(), trip.polity().asActor(), price,
                    TransferReason.FEE, calendar.today()));
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        lastTrip.put(caller.player(), clock.getAsLong());
        return Reply.success(Message.of(MessageKey.PORT_ARRIVED_PAID, trip.polity().name().value(),
                texts.money(price)));
    }

    // ---- Regeln ----

    /** Verbleibende Wartezeit in ganzen Sekunden (aufgerundet); 0, wenn der Spieler reisen darf. */
    private long cooldownLeft(Caller caller, PolityView polity) {
        long cooldownMillis = queries.effectiveLaws(polity).valueOf(StandardParameters.HOME_PORT_COOLDOWN_SECONDS)
                * 1_000L;
        Long last = lastTrip.get(caller.player());
        if (cooldownMillis <= 0 || last == null) {
            return 0;
        }
        long left = last + cooldownMillis - clock.getAsLong();
        return left <= 0 ? 0 : (left + 999) / 1_000;
    }

    /** Ob der Krieg diesem Nicht-Bürger den Zugang nach der Server-Einstellung {@code war_port_lock} verwehrt. */
    private boolean lockedByWar(Caller caller, PolityView polity) {
        return switch (warLock) {
            case ALL_OUTSIDERS -> atWar(polity);
            case ENEMIES -> isEnemy(caller, polity);
            case NONE -> false;
        };
    }

    /** Ob die Gemeinde (oder ein Bund) des Spielers mit der Polity oder einem ihrer Bünde im Krieg steht. */
    private boolean isEnemy(Caller caller, PolityView polity) {
        List<PolityId> own = new ArrayList<>();
        queries.municipalityOf(caller.player()).ifPresent(town -> {
            own.add(town.id());
            queries.ancestorsOf(town.id()).forEach(up -> own.add(up.id()));
        });
        List<PolityId> target = new ArrayList<>(List.of(polity.id()));
        queries.ancestorsOf(polity.id()).forEach(up -> target.add(up.id()));
        WarView registry = wars.get();
        return own.stream().anyMatch(mine -> target.stream()
                .anyMatch(theirs -> registry.ongoingWarBetween(mine, theirs).isPresent()));
    }

    /** Im Krieg stehen die Häfen einer Polity (und der Bünde, denen sie angehört) nur noch ihren Bürgern offen. */
    private boolean atWar(PolityView polity) {
        WarView registry = wars.get();
        return !registry.ongoingWarsOf(polity.id()).isEmpty() || queries.ancestorsOf(polity.id()).stream()
                .anyMatch(up -> !registry.ongoingWarsOf(up.id()).isEmpty());
    }

    /** Wer darf? Die Spitze immer, sonst nach der Zugangsregel der Polity. */
    boolean mayUse(Caller caller, PolityView polity) {
        if (isHead(caller, polity)) {
            return true;
        }
        return switch (queries.civic().portSettingsOf(polity.id()).access()) {
            case NOBODY -> false;
            case CITIZENS -> isCitizen(caller, polity);
            case CITIZENS_AND_ALLIES -> isCitizen(caller, polity) || queries.isAlly(caller.player(), polity);
            case EVERYONE -> true;
        };
    }

    /** Bürger einer Gemeinde sind ihre Mitglieder; Bürger eines Bundes sind die der Gemeinden darin. */
    private boolean isCitizen(Caller caller, PolityView polity) {
        return queries.municipalityOf(caller.player())
                .filter(town -> town.id().equals(polity.id())
                        || queries.ancestorsOf(town.id()).stream().anyMatch(up -> up.id().equals(polity.id())))
                .isPresent();
    }

    private static boolean isHead(Caller caller, PolityView polity) {
        return CivicUseCases.isHead(polity, caller.player());
    }

    /**
     * Ein zusätzlicher Hafen gilt nur, solange er auf dem Gebiet der Polity (oder ihrer Mitgliedsgemeinden) liegt;
     * wurde das Land abgetreten, geht er verloren und wird hier abgeräumt.
     */
    private Optional<BlockPosition> validExtra(PolityView polity, int slot) {
        Optional<HomePort> port = queries.civic().port(polity.id(), slot);
        if (port.isEmpty()) {
            return Optional.empty();
        }
        if (onTerritoryOf(polity.id(), port.get().position())) {
            return Optional.of(port.get().position());
        }
        app.civic().dropPort(polity.id(), slot, calendar.today());
        return Optional.empty();
    }

    private boolean onTerritoryOf(PolityId polity, BlockPosition position) {
        Optional<PolityId> sovereign = queries.territory().sovereignOf(position.chunk());
        return sovereign.isPresent() && (sovereign.get().equals(polity)
                || queries.ancestorsOf(sovereign.get()).stream().anyMatch(up -> up.id().equals(polity)));
    }

    private static boolean isExtraSlot(int slot) {
        return slot >= HomePort.FIRST_EXTRA_SLOT && slot <= HomePort.LAST_EXTRA_SLOT;
    }

    private static String describe(BlockPosition position) {
        return position.x() + " " + position.y() + " " + position.z();
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Reply.failure(Message.of(MessageKey.CIVIC_REFUSED, e.getMessage()));
        }
    }
}
