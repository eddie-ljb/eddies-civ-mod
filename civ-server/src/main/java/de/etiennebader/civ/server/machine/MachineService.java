package de.etiennebader.civ.server.machine;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.company.ProductionUseCases;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.production.Branch;
import de.etiennebader.civ.domain.production.Enterprise;
import de.etiennebader.civ.domain.production.Machine;
import de.etiennebader.civ.domain.production.MachineFleet;
import de.etiennebader.civ.domain.production.MachinePos;
import de.etiennebader.civ.domain.production.MachineType;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Grundstückspflicht, Maschinen und Zusatzwaren der Firmen.
 *
 * <ul>
 *   <li><b>Grundstück:</b> Ist {@code company_plot_required} an, produziert eine Firma nur, solange sie in einer
 *       Gemeinde ein Grundstück hält. Dorfbewohner-Firmen kaufen sich eines ({@code VillagerBusiness}).</li>
 *   <li><b>Maschinen:</b> je Branche mehrere {@link MachineType}; sie werden gekauft (Firmenkasse), als eigener
 *       Block auf einem Firmengrundstück aufgestellt, steigern den Ertrag je Ausbaustufe und verschleißen täglich.
 *       Ausgefallene Maschinen stehen still, bis sie gewartet sind: selbst (Kosten verschwinden) oder durch eine
 *       Wartungsfirma ({@link Branch#MAINTENANCE}, Aufschlag von 25 %, der Betrag geht an sie).</li>
 *   <li><b>Arbeitsweg:</b> Angestellte laufen tagsüber zu den Maschinen; je Anteil anwesender Angestellter steigt der
 *       Maschinenbonus von der Hälfte bis zum vollen Wert.</li>
 *   <li><b>Zusatzwaren:</b> Die Leitung darf Waren, die thematisch zur Branche passen ({@link Branch#extras()}),
 *       aus dem Inventar einlagern; sie werden wie das übrige Lager verkauft.</li>
 * </ul>
 */
public final class MachineService implements ProductionUseCases.Rules {

    /** Aufschlag, den eine Wartungsfirma für ihre Arbeit nimmt, in Prozent. */
    static final int FIRM_SURCHARGE_PERCENT = 125;
    /** Verschleiß, ab dem Dorfbewohner ihre Maschinen warten lassen. */
    static final int VILLAGER_MAINTAIN_AT = 50;

    private final CivApplication app;
    private final WorldQueries queries;
    private final ServerSettings settings;
    private final Texts texts;
    private final Announcements announcements;
    private final IdGenerator ids;
    private final MachineStore store;
    private final MachineFleet fleet;
    private final Map<PolityId, Set<ActorRef>> workedToday = new HashMap<>();
    private final Supplier<Day> today;

    public MachineService(CivApplication app, ServerSettings settings, Texts texts, Announcements announcements,
                          IdGenerator ids, Supplier<Day> today, Optional<Path> file) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.settings = Objects.requireNonNull(settings, "settings");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.today = Objects.requireNonNull(today, "today");
        this.store = new MachineStore(file);
        this.fleet = store.load();
    }

    // ---- Regeln der Produktion ----

    @Override
    public synchronized boolean mayProduce(PolityId company) {
        return !settings.economy().companyPlotRequired() || holdsPlot(company);
    }

    @Override
    public synchronized double outputFactor(PolityId company) {
        double factor = fleet.factor(company, machine -> running(machine));
        boolean physical = fleet.of(company).stream().anyMatch(machine -> machine.where().isPresent());
        if (factor <= 1 || !physical || villagerLed(company)) {
            return factor;
        }
        int employees = app.production().employees(company).size();
        if (employees == 0) {
            return factor;
        }
        double present = Math.min(1.0, workedToday.getOrDefault(company, Set.of()).size() / (double) employees);
        return 1 + (factor - 1) * (0.5 + 0.5 * present);
    }

    @Override
    public synchronized void produced(PolityId company, Day day) {
        List<Machine> broken = fleet.wearDay(company, this::running);
        workedToday.remove(company);
        for (Machine machine : broken) {
            queries.polity(company).flatMap(PolityView::mayor).filter(PlayerRef.class::isInstance)
                    .map(PlayerRef.class::cast).ifPresent(head -> announcements.add(Announcement.toPlayer(head,
                            Message.of(MessageKey.MACHINE_BROKEN, Message.of(typeKey(machine.type())),
                                    queries.polity(company).map(found -> found.name().value()).orElse("?")))));
        }
    }

    private boolean running(Machine machine) {
        return machine.where().isPresent() || villagerLed(machine.company());
    }

    private boolean villagerLed(PolityId company) {
        return queries.polity(company).flatMap(PolityView::mayor).filter(PersonRef.class::isInstance).isPresent();
    }

    private boolean holdsPlot(PolityId company) {
        Optional<PolityView> firm = queries.polity(company);
        if (firm.isEmpty()) {
            return false;
        }
        for (PolityView town : queries.polities()) {
            if (town.kind() == PolityKind.TERRITORIAL
                    && !queries.land(town.id()).plotsHeldBy(firm.get().asActor()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Das Grundstück einer Firma, auf dem diese Position liegt. */
    private Optional<Plot> plotOf(PolityView firm, MachinePos position) {
        de.etiennebader.civ.domain.territory.DimensionId dimension =
                new de.etiennebader.civ.domain.territory.DimensionId(position.dimension());
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            Optional<Plot> plot = queries.land(town.id()).plotAt(dimension, position.x(), position.z())
                    .filter(found -> found.holder().equals(firm.asActor()));
            if (plot.isPresent()) {
                return plot;
            }
        }
        return Optional.empty();
    }

    // ---- Befehle ----

    private Reply guarded(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Reply.failure(Message.of(MessageKey.CIVIC_REFUSED, e.getMessage()));
        }
    }

    private Optional<PolityView> company(String name) {
        return queries.companies().stream().filter(firm -> firm.name().value().equalsIgnoreCase(name.strip()))
                .findFirst();
    }

    private Money money(PolityView firm, long amount) {
        return Money.of(amount, firm.currency());
    }

    /** {@code /civ firma maschinen <Firma>} */
    public synchronized Reply list(String companyName) {
        Optional<PolityView> firm = company(companyName);
        if (firm.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.COMPANY_UNKNOWN, companyName.strip()));
        }
        List<Machine> machines = fleet.of(firm.get().id());
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.MACHINE_HEADER, firm.get().name().value(),
                settings.economy().companyPlotRequired() && !holdsPlot(firm.get().id())
                        ? Message.of(MessageKey.MACHINE_NO_PLOT) : Message.of(MessageKey.MACHINE_PLOT_OK)));
        if (machines.isEmpty()) {
            lines.add(Message.of(MessageKey.MACHINE_NONE, typeIds(queries.production().enterpriseOf(firm.get().id()))));
        }
        for (int i = 0; i < machines.size(); i++) {
            Machine machine = machines.get(i);
            Message state = Message.of(machine.broken() ? MessageKey.MACHINE_STATE_BROKEN
                    : machine.where().isPresent() ? MessageKey.MACHINE_STATE_RUNNING : MessageKey.MACHINE_STATE_UNPLACED);
            lines.add(Message.of(MessageKey.MACHINE_LINE, i + 1, Message.of(typeKey(machine.type())), machine.level(),
                    machine.wear(), state));
        }
        return Reply.success(lines);
    }

    /** Ergebnis eines Kaufs: der Block, der dem Spieler ins Inventar gelegt werden soll. */
    public record Purchase(Reply reply, Optional<MachineType> type) {
    }

    /** {@code /civ firma maschine kaufen <Firma> <Typ>} */
    public synchronized Purchase buy(Caller caller, String companyName, String typeWord) {
        Optional<PolityView> firm = company(companyName);
        if (firm.isEmpty()) {
            return new Purchase(Reply.failure(Message.of(MessageKey.COMPANY_UNKNOWN, companyName.strip())), Optional.empty());
        }
        Optional<MachineType> type = MachineType.parse(typeWord);
        Optional<Enterprise> enterprise = queries.production().enterpriseOf(firm.get().id());
        if (type.isEmpty() || enterprise.isEmpty() || type.get().branch() != enterprise.get().branch()) {
            return new Purchase(Reply.failure(Message.of(MessageKey.MACHINE_WRONG_TYPE, typeWord.strip(),
                    typeIds(enterprise))), Optional.empty());
        }
        Reply[] reply = new Reply[1];
        boolean[] ok = new boolean[1];
        reply[0] = guarded(() -> {
            if (fleet.of(firm.get().id()).size() >= MachineType.MAX_PER_COMPANY) {
                return Reply.failure(Message.of(MessageKey.MACHINE_LIMIT, MachineType.MAX_PER_COMPANY));
            }
            app.production().spend(caller.player(), firm.get().id(), money(firm.get(), type.get().price()), today.get());
            fleet.buy(ids.next(), firm.get().id(), type.get());
            save();
            ok[0] = true;
            return Reply.success(Message.of(MessageKey.MACHINE_BOUGHT, Message.of(typeKey(type.get())),
                    de.etiennebader.civ.server.company.CompanyService.money(type.get().price())));
        });
        return new Purchase(reply[0], ok[0] ? type : Optional.empty());
    }

    /** {@code /civ firma maschine ausbauen <Firma> <Nr>} */
    public synchronized Reply upgrade(Caller caller, String companyName, int number) {
        return withMachine(companyName, number, (firm, machine) -> guarded(() -> {
            if (machine.level() >= MachineType.MAX_LEVEL) {
                return Reply.failure(Message.of(MessageKey.MACHINE_MAX_LEVEL));
            }
            long cost = machine.type().upgradeCost(machine.level());
            app.production().spend(caller.player(), firm.id(), money(firm, cost), today.get());
            Machine upgraded = fleet.upgrade(machine.id());
            save();
            return Reply.success(Message.of(MessageKey.MACHINE_UPGRADED, Message.of(typeKey(machine.type())),
                    upgraded.level(), de.etiennebader.civ.server.company.CompanyService.money(cost)));
        }));
    }

    /** {@code /civ firma maschine warten <Firma> <Nr> [selbst|firma]} */
    public synchronized Reply maintain(Caller caller, String companyName, int number, boolean byFirm) {
        return withMachine(companyName, number, (firm, machine) -> guarded(() ->
                doMaintain(caller.player(), firm, machine, byFirm)));
    }

    private Reply doMaintain(ActorRef head, PolityView firm, Machine machine, boolean byFirm) {
        if (machine.wear() == 0) {
            return Reply.failure(Message.of(MessageKey.MACHINE_NO_NEED));
        }
        long cost = machine.type().maintenanceCost(machine.level(), machine.wear());
        if (byFirm) {
            Optional<PolityView> provider = maintenanceFirm(firm.id());
            if (provider.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.MACHINE_NO_FIRM));
            }
            long price = cost * FIRM_SURCHARGE_PERCENT / 100;
            app.production().payFirm(head, firm.id(), provider.get().id(), money(firm, price), today.get());
            fleet.maintain(machine.id());
            save();
            return Reply.success(Message.of(MessageKey.MACHINE_MAINTAINED_FIRM, Message.of(typeKey(machine.type())),
                    provider.get().name().value(), de.etiennebader.civ.server.company.CompanyService.money(price)));
        }
        app.production().spend(head, firm.id(), money(firm, cost), today.get());
        fleet.maintain(machine.id());
        save();
        return Reply.success(Message.of(MessageKey.MACHINE_MAINTAINED, Message.of(typeKey(machine.type())),
                de.etiennebader.civ.server.company.CompanyService.money(cost)));
    }

    /** Eine Wartungsfirma, die einspringen kann: andere Firma der Branche {@link Branch#MAINTENANCE} mit Personal. */
    private Optional<PolityView> maintenanceFirm(PolityId except) {
        return queries.companies().stream()
                .filter(other -> !other.id().equals(except))
                .filter(other -> queries.production().enterpriseOf(other.id())
                        .filter(found -> found.branch() == Branch.MAINTENANCE).isPresent())
                .filter(other -> app.production().workforce(other.id()) > 0)
                .min(Comparator.comparing((PolityView other) -> other.id().value()));
    }

    private Reply withMachine(String companyName, int number, java.util.function.BiFunction<PolityView, Machine, Reply> action) {
        Optional<PolityView> firm = company(companyName);
        if (firm.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.COMPANY_UNKNOWN, companyName.strip()));
        }
        List<Machine> machines = fleet.of(firm.get().id());
        if (number < 1 || number > machines.size()) {
            return Reply.failure(Message.of(MessageKey.MACHINE_UNKNOWN_NUMBER, number));
        }
        return action.apply(firm.get(), machines.get(number - 1));
    }

    /** Ergebnis von {@code einlagern}: wie viel dem Inventar abgezogen werden soll. */
    public record Deposit(Reply reply, long quantity) {
    }

    /** {@code /civ firma einlagern <Firma>}: Zusatzwaren aus der Hand ins Lager. Nur thematisch passende Waren. */
    public synchronized Deposit deposit(Caller caller, String companyName, String itemId, long quantity) {
        Optional<PolityView> firm = company(companyName);
        if (firm.isEmpty()) {
            return new Deposit(Reply.failure(Message.of(MessageKey.COMPANY_UNKNOWN, companyName.strip())), 0);
        }
        Optional<Enterprise> enterprise = queries.production().enterpriseOf(firm.get().id());
        if (enterprise.isEmpty()) {
            return new Deposit(Reply.failure(Message.of(MessageKey.MACHINE_WRONG_TYPE, "-", "-")), 0);
        }
        Commodity commodity = Commodity.of(itemId);
        if (!enterprise.get().branch().accepts(commodity)) {
            return new Deposit(Reply.failure(Message.of(MessageKey.DEPOSIT_REFUSED, itemId,
                    String.join(", ", enterprise.get().branch().extras().stream()
                            .map(id -> id.substring(id.indexOf(':') + 1)).toList()))), 0);
        }
        long[] added = new long[1];
        Reply reply = guarded(() -> {
            added[0] = app.production().deposit(caller.player(), firm.get().id(), commodity, quantity, today.get());
            if (added[0] <= 0) {
                return Reply.failure(Message.of(MessageKey.DEPOSIT_FULL));
            }
            return Reply.success(Message.of(MessageKey.DEPOSITED, added[0],
                    Message.of(de.etiennebader.civ.server.company.CompanyService.itemKey(commodity))));
        });
        return new Deposit(reply, added[0]);
    }

    /** Waren, die diese Firma einlagern darf, für Vorschläge. */
    public List<String> acceptedItems(String companyName) {
        return company(companyName).flatMap(firm -> queries.production().enterpriseOf(firm.id()))
                .map(found -> found.branch().extras()).orElse(List.of());
    }

    // ---- Blöcke in der Welt ----

    /**
     * Ein Maschinenblock wurde aufgestellt. Er zählt nur auf einem Grundstück einer Firma, die der Spieler leitet und
     * die eine solche Maschine noch ohne Platz besitzt.
     *
     * @return ob der Block stehen bleiben darf
     */
    public synchronized boolean placed(PlayerRef player, MachineType type, MachinePos position) {
        for (PolityView firm : queries.companies()) {
            if (!firm.mayor().filter(player::equals).isPresent() || plotOf(firm, position).isEmpty()) {
                continue;
            }
            Optional<Machine> free = fleet.of(firm.id()).stream()
                    .filter(machine -> machine.type() == type && machine.where().isEmpty()).findFirst();
            if (free.isPresent()) {
                fleet.place(free.get().id(), position);
                save();
                return true;
            }
        }
        return false;
    }

    /** Ein Maschinenblock wurde abgebaut oder ist verschwunden: die Maschine bleibt der Firma, steht aber nicht mehr. */
    public synchronized void removed(MachinePos position) {
        if (fleet.unplaceAt(position).isPresent()) {
            save();
        }
    }

    /** Ob an dieser Stelle eine Maschine eingetragen ist. */
    public synchronized boolean registered(MachinePos position) {
        return fleet.at(position).isPresent();
    }

    /** Alle aufgestellten Maschinen, damit das Spiel prüfen kann, ob ihre Blöcke noch stehen. */
    public synchronized List<MachinePos> placedPositions() {
        return fleet.all().stream().flatMap(machine -> machine.where().stream()).toList();
    }

    /** Wo ein Angestellter arbeitet: die Firma und ihre aufgestellten Maschinen. */
    public record Workplace(PolityId company, List<MachinePos> machines) {
    }

    public synchronized Optional<Workplace> workplaceOf(PersonRef employee) {
        for (Contract contract : queries.contracts().active()) {
            if (contract.kind() == ContractKind.EMPLOYMENT && contract.counterparty().equals(employee)
                    && contract.offeror() instanceof de.etiennebader.civ.domain.actor.PolityRef firm) {
                List<MachinePos> positions = fleet.of(firm.id()).stream().filter(machine -> !machine.broken())
                        .flatMap(machine -> machine.where().stream()).toList();
                if (!positions.isEmpty()) {
                    return Optional.of(new Workplace(firm.id(), positions));
                }
            }
        }
        return Optional.empty();
    }

    /** Ein Angestellter steht heute an seinen Maschinen. */
    public synchronized void worked(PolityId company, PersonRef employee) {
        workedToday.computeIfAbsent(company, id -> new HashSet<>()).add(employee);
    }

    // ---- Tagesabschluss ----

    /** Dorfbewohner-Firmen kaufen und warten Maschinen; Maschinen aufgelöster Firmen verschwinden. */
    public synchronized void endOfDay(Day day, boolean villagersAct) {
        for (Machine machine : fleet.all()) {
            if (queries.polity(machine.company()).isEmpty()) {
                fleet.removeCompany(machine.company());
            }
        }
        if (villagersAct) {
            for (PolityView firm : queries.companies().stream()
                    .sorted(Comparator.comparing(found -> found.id().value())).toList()) {
                firm.mayor().filter(PersonRef.class::isInstance).map(PersonRef.class::cast)
                        .ifPresent(head -> villagerTurn(firm, head, day));
            }
        }
        save();
    }

    private void villagerTurn(PolityView firm, PersonRef head, Day day) {
        Optional<Enterprise> enterprise = queries.production().enterpriseOf(firm.id());
        if (enterprise.isEmpty() || VillagerAmbitions.roll(head, day, 0x90) >= 0.6) {
            return;
        }
        if (settings.economy().companyPlotRequired() && !holdsPlot(firm.id())) {
            return;
        }
        long treasury = queries.ledger().balanceOf(firm.asActor(), firm.currency()).amount();
        try {
            for (Machine machine : fleet.of(firm.id())) {
                if (machine.wear() >= VILLAGER_MAINTAIN_AT && !machine.broken() || machine.broken()) {
                    long cost = machine.type().maintenanceCost(machine.level(), machine.wear());
                    boolean useFirm = maintenanceFirm(firm.id()).isPresent();
                    long price = useFirm ? cost * FIRM_SURCHARGE_PERCENT / 100 : cost;
                    if (treasury >= price * 3) {
                        doMaintain(head, firm, machine, useFirm);
                        return;
                    }
                }
            }
            List<Machine> owned = fleet.of(firm.id());
            Optional<MachineType> missing = MachineType.of(enterprise.get().branch()).stream()
                    .filter(type -> owned.stream().noneMatch(machine -> machine.type() == type))
                    .min(Comparator.comparingLong(MachineType::price));
            if (missing.isPresent() && owned.size() < MachineType.MAX_PER_COMPANY
                    && treasury >= missing.get().price() * 4) {
                app.production().spend(head, firm.id(), money(firm, missing.get().price()), day);
                fleet.buy(ids.next(), firm.id(), missing.get());
                return;
            }
            Optional<Machine> weakest = owned.stream().filter(machine -> machine.level() < MachineType.MAX_LEVEL)
                    .min(Comparator.comparingInt(Machine::level));
            if (weakest.isPresent() && treasury >= weakest.get().type().upgradeCost(weakest.get().level()) * 4) {
                app.production().spend(head, firm.id(), money(firm,
                        weakest.get().type().upgradeCost(weakest.get().level())), day);
                fleet.upgrade(weakest.get().id());
            }
        } catch (DomainException | IllegalStateException | IllegalArgumentException ignored) {
            // zu wenig Geld oder keine Berechtigung: heute geschieht nichts
        }
    }

    private void save() {
        store.save(fleet);
    }

    static String typeKey(MachineType type) {
        return "eddies-civ-mod.machine." + type.name().toLowerCase(Locale.ROOT);
    }

    private static String typeIds(Optional<Enterprise> enterprise) {
        return enterprise.map(found -> String.join(", ", MachineType.of(found.branch()).stream()
                .map(MachineType::id).toList())).orElse("-");
    }
}
