package de.etiennebader.civ.server.civil;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.civil.Address;
import de.etiennebader.civ.application.civil.Addresses;
import de.etiennebader.civ.application.civil.IdCards;
import de.etiennebader.civ.application.civil.RunContracts;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.contract.ContractStatus;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Street;
import de.etiennebader.civ.domain.population.Gender;
import de.etiennebader.civ.domain.population.VillagerIdentity;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Straßen, Adressen und Verträge im Spiel.
 *
 * <ul>
 *   <li>{@code /civ strasse anlegen <Name>} (Fläche mit dem Grundstücksstab), {@code aufheben <Name>}, {@code liste}.</li>
 *   <li>{@code /civ adresse [Spieler]}, {@code /civ adressen}.</li>
 *   <li>{@code /civ vertrag liste | zeigen <Nr> | annehmen <Nr> | ablehnen <Nr> | kuendigen <Nr>}</li>
 *   <li>{@code /civ vertrag miete <Spieler> <Preis/Tag> [Kaution] [Tage]} auf dem eigenen Grundstück.</li>
 *   <li>{@code /civ vertrag arbeit <Spieler> <Lohn/Tag> [Tage]}; Dorfbewohner über das Spiel.</li>
 *   <li>{@code /civ vertrag frei <Spieler> <einmalig> <täglich> <Titel> | <Text>}</li>
 *   <li>{@code /civ vertrag buendnis <Gemeinde oder Bund>}</li>
 * </ul>
 */
public final class CivilService {

    private static final Map<String, ContractKind> KINDS = Map.of(
            "miete", ContractKind.LEASE, "arbeit", ContractKind.EMPLOYMENT, "frei", ContractKind.CUSTOM,
            "buendnis", ContractKind.ALLIANCE);

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final CurrencyId currency;
    private final Texts texts;
    private final Announcements announcements;
    private final Function<PlayerRef, Optional<Area>> selections;

    /**
     * @param selections die mit dem Grundstücksstab markierte Fläche eines Spielers
     */
    public CivilService(CivApplication app, Calendar calendar, PlayerDirectory players, CurrencyId currency,
                        Texts texts, Announcements announcements, Function<PlayerRef, Optional<Area>> selections) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.selections = Objects.requireNonNull(selections, "selections");
    }

    /** Vertragsarten für die Vervollständigung. */
    public static List<String> kindNames() {
        return List.of("miete", "arbeit", "frei", "buendnis");
    }

    public static Optional<ContractKind> parseKind(String input) {
        return Optional.ofNullable(KINDS.get(input.strip().toLowerCase(Locale.ROOT)));
    }

    // ---- Straßen und Adressen ----

    /** {@code /civ strasse anlegen <Name>} mit der Fläche aus dem Grundstücksstab. */
    public Reply layStreet(Caller caller, String name) {
        Optional<Area> area = selections.apply(caller.player());
        if (area.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.STREET_NEEDS_SELECTION));
        }
        return withTown(caller, town -> attempt(() -> {
            boolean added = app.streets().lay(caller.player(), town.id(), name, area.get(), calendar.today());
            return Reply.success(Message.of(added ? MessageKey.STREET_LAID : MessageKey.STREET_MOVED, name.strip(),
                    town.name().value()));
        }));
    }

    /** {@code /civ strasse aufheben <Name>} */
    public Reply removeStreet(Caller caller, String name) {
        return withTown(caller, town -> attempt(() -> app.streets().remove(caller.player(), town.id(), name,
                calendar.today())
                ? Reply.success(Message.of(MessageKey.STREET_REMOVED, name.strip()))
                : Reply.failure(Message.of(MessageKey.STREET_UNKNOWN, name.strip()))));
    }

    /** {@code /civ strasse liste} */
    public Reply listStreets(Caller caller) {
        return withTown(caller, town -> {
            List<Message> lines = new ArrayList<>();
            lines.add(Message.of(MessageKey.STREETS_HEADER, town.name().value(), town.postalCode(),
                    town.streets().size()));
            Addresses addresses = app.addresses();
            for (Street street : town.streets()) {
                long houses = addresses.all(town).stream().filter(address -> address.street().equals(street.name()))
                        .count();
                lines.add(Message.of(MessageKey.STREET_ENTRY, street.name(), houses));
            }
            return Reply.success(lines);
        });
    }

    /** Straßen einer Gemeinde mit Hausnummern, z. B. für Menü und Webseite. */
    public List<Address> addressesOf(PolityView town) {
        return app.addresses().all(town);
    }

    /** {@code /civ adresse [Spieler]} */
    public Reply address(Caller caller, Optional<String> playerName) {
        ActorRef who = caller.player();
        String name = caller.name();
        if (playerName.isPresent() && !playerName.get().isBlank()) {
            Optional<PlayerRef> found = players.findByName(playerName.get().strip());
            if (found.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, playerName.get().strip()));
            }
            who = found.get();
            name = texts.actor(who);
        }
        Optional<Address> residence = app.addresses().residenceOf(who);
        return residence.isPresent()
                ? Reply.success(Message.of(MessageKey.ADDRESS_OF, name, residence.get().line()))
                : Reply.success(Message.of(MessageKey.ADDRESS_NONE, name));
    }

    /**
     * {@code /civ ausweis}: Das Rathaus (die Gemeinde, in der der Aufrufer steht) stellt einen Personalausweis
     * für den Aufrufer oder für einen Dorfbewohner aus. {@code handout} übergibt die Daten an das Item.
     */
    public Reply issueIdCard(Caller caller, Optional<ActorRef> villager, Consumer<IdCardIssue> handout) {
        return withTown(caller, town -> {
            ActorRef holder = villager.orElse(caller.player());
            String name = villager.isPresent() ? texts.actor(holder) : caller.name();
            IdCards.Data data = app.idCards().issue(holder, name, genderOf(holder), calendar.today());
            handout.accept(new IdCardIssue(holder, data));
            return Reply.success(villager.isPresent()
                    ? Message.of(MessageKey.ID_CARD_ISSUED_FOR, name)
                    : Message.of(MessageKey.ID_CARD_ISSUED));
        });
    }

    /** {@code /civ ausweis pruefen}: Zeigt die Angaben eines Ausweises und ob sie noch stimmen. */
    public Reply checkIdCard(ActorRef holder, IdCards.Data card) {
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.ID_CARD_HEADER, card.holderName(), card.issuedOn().value()));
        card.gender().ifPresent(gender -> lines.add(Message.of(MessageKey.ID_CARD_GENDER, genderText(gender))));
        lines.add(card.address().map(line -> Message.of(MessageKey.ID_CARD_ADDRESS, line))
                .orElseGet(() -> Message.of(MessageKey.ID_CARD_ADDRESS_NONE)));
        lines.add(card.citizenOf().map(town -> Message.of(MessageKey.ID_CARD_CITIZEN_OF, town))
                .orElseGet(() -> Message.of(MessageKey.ID_CARD_CITIZEN_NONE)));
        boolean current = app.idCards().isCurrent(holder, texts.actor(holder), genderOf(holder), card);
        lines.add(Message.of(current ? MessageKey.ID_CARD_VALID : MessageKey.ID_CARD_OUTDATED));
        return Reply.success(lines);
    }

    /** Geschlecht eines Akteurs: bei Dorfbewohnern aus dem Namen, bei Spielern die eigene Angabe. */
    public Optional<Gender> genderOf(ActorRef actor) {
        return switch (actor) {
            case PersonRef person -> Optional.of(VillagerIdentity.genderOf(person.uuid()));
            case PlayerRef player -> players.genderOf(player);
            default -> Optional.empty();
        };
    }

    /** {@code /civ geschlecht [weiblich|maennlich|divers]}: ohne Angabe das eigene anzeigen, sonst setzen. */
    public Reply gender(Caller caller, Optional<Gender> chosen) {
        if (chosen.isEmpty()) {
            return genderOf(caller.player())
                    .map(gender -> Reply.success(Message.of(MessageKey.GENDER_OWN, genderText(gender))))
                    .orElseGet(() -> Reply.success(Message.of(MessageKey.GENDER_NONE)));
        }
        players.setGender(caller.player(), chosen.get());
        return Reply.success(Message.of(MessageKey.GENDER_SET, genderText(chosen.get())));
    }

    private static Message genderText(Gender gender) {
        return Message.of(switch (gender) {
            case FEMALE -> MessageKey.GENDER_FEMALE;
            case MALE -> MessageKey.GENDER_MALE;
            case DIVERSE -> MessageKey.GENDER_DIVERSE;
        });
    }

    /** Ein ausgestellter Ausweis samt Inhaber. */
    public record IdCardIssue(ActorRef holder, IdCards.Data data) {
    }

    /** Anschrift eines Akteurs als Text; leer ohne Wohnsitz. */
    public Optional<String> residenceLine(ActorRef actor) {
        return app.addresses().residenceOf(actor).map(Address::line);
    }

    /** Anschrift an einer Stelle, z. B. für Dorfbewohner. */
    public Optional<String> addressAt(BlockPosition position) {
        return app.addresses().at(position).map(Address::line);
    }

    public enum SignType { STREET, HOUSE, TOWN, COUNTRY, NATION }

    /** Automatische Beschriftung am Aufstellort. Ein Spielertext hat beim Platzieren Vorrang. */
    public List<String> signLines(SignType type, BlockPosition position) {
        Optional<PolityView> owner = queries.territory().claimAt(position.chunk())
                .flatMap(claim -> queries.polity(claim.polity()));
        if (owner.isEmpty()) {
            return List.of();
        }
        PolityView town = owner.orElseThrow();
        if (type == SignType.HOUSE) {
            return app.addresses().at(position).map(address -> List.of(Integer.toString(address.number())))
                    .orElse(List.of());
        }
        if (type == SignType.STREET) {
            Optional<String> onStreet = town.streets().stream()
                    .filter(street -> street.area().contains(position.dimension(), position.x(), position.z()))
                    .map(Street::name).findFirst();
            return onStreet.or(() -> app.addresses().at(position).map(Address::street)).map(List::of)
                    .orElse(List.of());
        }
        List<PolityView> ancestors = queries.ancestorsOf(town.id());
        Optional<String> land = ancestors.stream().filter(polity -> polity.level().equals("land"))
                .map(polity -> "Land: " + polity.name().value()).findFirst();
        Optional<String> nation = ancestors.stream().filter(polity -> polity.level().equals("nation"))
                .map(polity -> "Nation: " + polity.name().value()).findFirst();
        List<String> lines = new ArrayList<>();
        if (type == SignType.TOWN) {
            String tier = switch (town.tier()) {
                case MUNICIPALITY -> "Gemeinde";
                case MARKET_TOWN -> "Marktgemeinde";
                case CITY -> "Stadt";
                case METROPOLIS -> "Metropole";
            };
            lines.add(tier + ": " + town.name().value());
        }
        if (type == SignType.COUNTRY && land.isEmpty()) {
            return List.of();
        }
        if (type != SignType.NATION) {
            land.ifPresent(lines::add);
        }
        nation.ifPresent(lines::add);
        return List.copyOf(lines);
    }

    // ---- Verträge ----

    /** Verträge eines Spielers und der Polities, die er führt, die neuesten zuerst. */
    public List<ContractView> contractsOf(PlayerRef viewer) {
        List<ContractView> views = new ArrayList<>();
        for (Contract contract : queries.contracts().contracts().reversed()) {
            if (app.contracts().mayActFor(contract, viewer)) {
                views.add(view(contract, viewer));
            }
        }
        return views;
    }

    public ContractView view(Contract contract, PlayerRef viewer) {
        ActorRef side = contract.involves(viewer) ? viewer
                : contract.offeror() instanceof PolityRef polity && queries.polity(polity.id())
                .map(found -> found.holds(viewer, Office.MAYOR)).orElse(false)
                ? contract.offeror() : contract.counterparty();
        boolean offered = contract.status() == ContractStatus.OFFERED;
        Optional<String> address = contract.plot().flatMap(ref -> queries.polity(ref.polity())
                .flatMap(town -> queries.land(town.id()).plot(ref.plot()).flatMap(plot -> app.addresses().of(town, plot)
                        .map(Address::line).or(() -> Optional.of(town.name().value())))));
        return new ContractView(contract.id(), contract.number(), contract.kind(), contract.status(), contract.title(),
                contract.text(), texts.actor(contract.offeror()), texts.actor(contract.counterparty()),
                texts.actor(contract.otherParty(side)), contract.payer().equals(side), money(contract.perDay()),
                money(contract.once()), money(contract.deposit()), contract.termDays(), address,
                contract.offeredOn().value(), contract.startedOn().map(Day::value), contract.endsOn().map(Day::value),
                contract.endReason().map(Enum::name), offered && contract.counterparty().equals(side),
                offered, contract.active());
    }

    /** {@code /civ vertrag liste} */
    public Reply list(Caller caller) {
        List<ContractView> contracts = contractsOf(caller.player()).stream()
                .filter(contract -> contract.status() == ContractStatus.OFFERED || contract.status() == ContractStatus.ACTIVE)
                .toList();
        if (contracts.isEmpty()) {
            return Reply.success(Message.of(MessageKey.CONTRACTS_NONE));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.CONTRACTS_HEADER, contracts.size()));
        for (ContractView contract : contracts) {
            lines.add(Message.of(MessageKey.CONTRACT_ENTRY, contract.number(), kindName(contract.kind()),
                    contract.title(), contract.other(), statusName(contract.status())));
        }
        return Reply.success(lines);
    }

    /** {@code /civ vertrag zeigen <Nr>} */
    public Reply show(Caller caller, int number) {
        return withContract(caller, number, contract -> {
            ContractView view = view(contract, caller.player());
            List<Message> lines = new ArrayList<>();
            lines.add(Message.of(MessageKey.CONTRACT_HEADER, view.number(), kindName(view.kind()), view.title(),
                    statusName(view.status())));
            lines.add(Message.of(MessageKey.CONTRACT_PARTIES, view.offeror(), view.counterparty()));
            lines.add(Message.of(MessageKey.CONTRACT_TERMS, view.perDay(), view.once(), view.deposit(),
                    view.termDays() == 0 ? Message.of(MessageKey.CONTRACT_UNLIMITED) : view.termDays()));
            view.address().ifPresent(address -> lines.add(Message.of(MessageKey.CONTRACT_PLOT, address)));
            if (!view.text().isEmpty()) {
                lines.add(Message.of(MessageKey.CONTRACT_TEXT, view.text()));
            }
            view.endsOn().ifPresent(end -> lines.add(Message.of(MessageKey.CONTRACT_ENDS, end)));
            return Reply.success(lines);
        });
    }

    /** {@code /civ vertrag annehmen <Nr>} */
    public Reply sign(Caller caller, int number) {
        return withContract(caller, number, contract -> attempt(() -> {
            app.contracts().sign(contract.id(), caller.player(), calendar.today());
            notifyParties(contract, Message.of(MessageKey.CONTRACT_SIGNED, contract.number(), contract.title(),
                    caller.name()));
            return Reply.success(Message.of(MessageKey.CONTRACT_SIGNED, contract.number(), contract.title(),
                    caller.name()));
        }));
    }

    /** {@code /civ vertrag ablehnen <Nr>} */
    public Reply decline(Caller caller, int number) {
        return withContract(caller, number, contract -> attempt(() -> {
            app.contracts().decline(contract.id(), caller.player(), calendar.today());
            notifyParties(contract, Message.of(MessageKey.CONTRACT_DECLINED, contract.number(), caller.name()));
            return Reply.success(Message.of(MessageKey.CONTRACT_DECLINED, contract.number(), caller.name()));
        }));
    }

    /** {@code /civ vertrag kuendigen <Nr>} */
    public Reply giveNotice(Caller caller, int number) {
        return withContract(caller, number, contract -> attempt(() -> {
            Contract noticed = app.contracts().giveNotice(contract.id(), caller.player(), calendar.today());
            Message message = Message.of(MessageKey.CONTRACT_NOTICE, contract.number(), caller.name(),
                    noticed.endsOn().map(Day::value).orElse(calendar.today().value()));
            notifyParties(contract, message);
            return Reply.success(message);
        }));
    }

    /** {@code /civ vertrag miete <Mieter> <Preis/Tag> [Kaution] [Tage]} auf dem Grundstück, auf dem man steht. */
    public Reply offerLease(Caller caller, String tenantName, String perDay, Optional<String> deposit, long termDays) {
        Optional<PlayerRef> tenant = players.findByName(tenantName.strip());
        if (tenant.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, tenantName.strip()));
        }
        Optional<PolityView> town = queries.territory().claimAt(caller.position().chunk())
                .flatMap(claim -> queries.polity(claim.polity()));
        Optional<Plot> plot = town.flatMap(found -> queries.land(found.id()).plotAt(caller.position().dimension(),
                caller.position().x(), caller.position().z()));
        if (plot.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CONTRACT_NO_PLOT_HERE));
        }
        return withAmount(perDay, rent -> withAmount(deposit.orElse("0"), true, security -> attempt(() -> {
            Contract offered = app.contracts().offerLease(caller.player(), town.get().id(), plot.get().id(),
                    tenant.get(), rent, security, termDays, calendar.today());
            return offered(caller, offered, tenant.get());
        })));
    }

    /** {@code /civ vertrag arbeit <Spieler> <Lohn/Tag> [Tage]} */
    public Reply offerEmployment(Caller caller, String employeeName, String wage, long termDays) {
        Optional<PlayerRef> employee = players.findByName(employeeName.strip());
        if (employee.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, employeeName.strip()));
        }
        return offerEmployment(caller, employee.get(), wage, termDays);
    }

    /** Stelle für einen Spieler oder Dorfbewohner; ein Dorfbewohner entscheidet sofort. */
    public Reply offerEmployment(Caller caller, ActorRef employee, String wage, long termDays) {
        return withAmount(wage, pay -> attempt(() -> {
            Contract offered = app.contracts().offerEmployment(caller.player(), employee, pay, termDays,
                    calendar.today());
            if (employee instanceof PersonRef) {
                return Reply.success(Message.of(MessageKey.CONTRACT_VILLAGER_HIRED, offered.number(), money(pay)));
            }
            return offered(caller, offered, employee);
        }));
    }

    /** {@code /civ vertrag frei <Spieler> <einmalig> <täglich> <Titel> | <Text>} */
    public Reply offerCustom(Caller caller, String partnerName, String once, String perDay, String titleAndText,
                             long termDays) {
        Optional<ActorRef> partner = recipient(partnerName);
        if (partner.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, partnerName.strip()));
        }
        String[] parts = titleAndText.split("\\|", 2);
        String title = parts[0].strip();
        String text = parts.length > 1 ? parts[1].strip() : "";
        if (title.isEmpty() || title.length() > Contract.MAX_TITLE || text.length() > Contract.MAX_TEXT) {
            return Reply.failure(Message.of(MessageKey.CONTRACT_INVALID_TEXT, Contract.MAX_TITLE, Contract.MAX_TEXT));
        }
        return withAmount(once, true, single -> withAmount(perDay, true, daily -> attempt(() -> {
            Contract offered = app.contracts().offerCustom(caller.player(), partner.get(), single, daily, title, text,
                    termDays, calendar.today());
            return offered(caller, offered, partner.get());
        })));
    }

    /** {@code /civ vertrag buendnis <Name>}: für die Gemeinde (oder den Bund), die der Spieler führt. */
    public Reply offerAlliance(Caller caller, String partnerName, Optional<PolityView> acting) {
        Optional<PolityView> partner = queries.polityNamed(partnerName.strip());
        if (partner.isEmpty() || acting.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, partnerName.strip()));
        }
        return attempt(() -> {
            Contract offered = app.contracts().offerAlliance(caller.player(), acting.get().id(), partner.get().id(),
                    calendar.today());
            partner.get().mayor().ifPresent(head -> notify(head, Message.of(MessageKey.CONTRACT_OFFERED_TO_YOU,
                    acting.get().name().value(), offered.number(), offered.title())));
            return Reply.success(Message.of(MessageKey.CONTRACT_OFFERED, offered.number(), offered.title(),
                    partner.get().name().value()));
        });
    }

    private Reply offered(Caller caller, Contract offered, ActorRef partner) {
        notify(partner, Message.of(MessageKey.CONTRACT_OFFERED_TO_YOU, caller.name(), offered.number(),
                offered.title()));
        return Reply.success(Message.of(MessageKey.CONTRACT_OFFERED, offered.number(), offered.title(),
                texts.actor(partner)));
    }

    /** Tagesende: Raten zahlen, Verträge beenden, Gehälter; Beteiligte erfahren, was sie betrifft. */
    public void endOfDay(Day day) {
        for (RunContracts.Outcome outcome : app.runContracts().execute(day)) {
            switch (outcome) {
                case RunContracts.Ended ended -> notifyParties(ended.contract(), Message.of(
                        MessageKey.CONTRACT_ENDED, ended.contract().number(), ended.contract().title(),
                        Message.of(MessageKey.valueOf("CONTRACT_END_" + ended.reason().name()))));
                case RunContracts.Paid ignored -> {
                    // Laufende Zahlungen stehen im Kontoauszug; keine Meldung.
                }
                case RunContracts.SalariesPaid salaries -> announcements.add(Announcement.toTown(salaries.polity(),
                        Message.of(MessageKey.SALARIES_PAID, salaries.recipients(), money(salaries.total()),
                                salaries.unpaid())));
            }
        }
    }

    private void notifyParties(Contract contract, Message message) {
        notify(contract.offeror(), message);
        notify(contract.counterparty(), message);
    }

    private void notify(ActorRef party, Message message) {
        if (party instanceof PlayerRef player) {
            announcements.add(Announcement.toPlayer(player, message));
        } else if (party instanceof PolityRef polity) {
            queries.polity(polity.id()).flatMap(PolityView::mayor).filter(PlayerRef.class::isInstance)
                    .ifPresent(head -> announcements.add(Announcement.toPlayer((PlayerRef) head, message)));
        }
    }

    public Message kindName(ContractKind kind) {
        return Message.of(MessageKey.valueOf("CONTRACT_KIND_" + kind.name()));
    }

    public Message statusName(ContractStatus status) {
        return Message.of(MessageKey.valueOf("CONTRACT_STATUS_" + status.name()));
    }

    private String money(Money amount) {
        return MoneyFormat.whole(amount.amount()) + "," + MoneyFormat.cents(amount.amount());
    }

    private Optional<ActorRef> recipient(String name) {
        Optional<ActorRef> player = players.findByName(name.strip()).map(ActorRef.class::cast);
        return player.isPresent() ? player : queries.polityNamed(name.strip()).map(PolityView::asActor);
    }

    private Reply withContract(Caller caller, int number, Function<Contract, Reply> action) {
        return queries.contracts().byNumber(number)
                .filter(contract -> app.contracts().mayActFor(contract, caller.player()))
                .map(action)
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.CONTRACT_UNKNOWN, number)));
    }

    private Reply withTown(Caller caller, Function<PolityView, Reply> action) {
        return queries.municipalityOf(caller.player()).map(action)
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)));
    }

    private Reply withAmount(String input, Function<Money, Reply> action) {
        return withAmount(input, false, action);
    }

    private Reply withAmount(String input, boolean zeroAllowed, Function<Money, Reply> action) {
        if (zeroAllowed && input.strip().matches("0+([.,]0*)?")) {
            return action.apply(Money.zero(currency));
        }
        try {
            return action.apply(Money.of(MoneyFormat.parse(input), currency));
        } catch (IllegalArgumentException e) {
            return Reply.failure(Message.of(MessageKey.INVALID_AMOUNT, input.strip()));
        }
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}
