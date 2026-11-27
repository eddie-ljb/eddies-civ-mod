package de.etiennebader.civ.server.government;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.office.CallElection;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.ProposeLawChange;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.government.Coalition;
import de.etiennebader.civ.domain.government.CoalitionBuilder;
import de.etiennebader.civ.domain.government.Government;
import de.etiennebader.civ.domain.government.GovernmentException;
import de.etiennebader.civ.domain.government.Ministry;
import de.etiennebader.civ.domain.government.MinistryElection;
import de.etiennebader.civ.domain.government.Portfolio;
import de.etiennebader.civ.domain.government.Selection;
import de.etiennebader.civ.domain.government.Subsidy;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Regierungsbildung, Koalitionsvertrag, Gesetzesvorschläge und Verordnungen der Minister, Förderungen und
 * Volkswahl der Minister (Issue #79).
 */
public final class GovernmentPolitics {

    private final GovernmentService core;
    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final Texts texts;
    private final Announcements announcements;
    private final boolean villagerPolitics;

    GovernmentPolitics(GovernmentService core, CivApplication app, Calendar calendar, PlayerDirectory players,
                       Texts texts, Announcements announcements, boolean villagerPolitics) {
        this.core = core;
        this.app = app;
        this.queries = app.queries();
        this.calendar = calendar;
        this.players = players;
        this.texts = texts;
        this.announcements = announcements;
        this.villagerPolitics = villagerPolitics;
    }

    // ------------------------------------------------------------------ Koalition und Regierungsbildung

    /** Das Oberhaupt beauftragt die stärkste Partei mit der Regierungsbildung (wirkt wie die Parteienrunde). */
    public synchronized Reply form(Caller caller) {
        return core.as(caller, (town, g) -> mayor(caller, town) && g.head().appointsGovernment(),
                (town, g) -> formNow(town, g, calendar.today()));
    }

    /** Das Oberhaupt entlässt die Regierung samt ernannten Ministern. */
    public synchronized Reply dismissGovernment(Caller caller) {
        return core.as(caller, (town, g) -> mayor(caller, town) && g.head().appointsGovernment(), (town, g) -> {
            g.dismissGovernment();
            Message line = Message.of(MessageKey.GOV_GOVERNMENT_DISMISSED, town.name().toString());
            announcements.add(Announcement.toTown(town.id(), line));
            return line;
        });
    }

    /** Das Oberhaupt ordnet Neuwahlen des Parlaments an. */
    public synchronized Reply newElection(Caller caller) {
        return core.as(caller, (town, g) -> mayor(caller, town) && g.head().callsElections(), (town, g) -> {
            app.callElection().execute(new CallElection.Command(town.id(), caller.player(), Office.PARLIAMENT,
                    calendar.today()));
            Message line = Message.of(MessageKey.GOV_NEW_ELECTION, town.name().toString());
            announcements.add(Announcement.toTown(town.id(), line));
            return line;
        });
    }

    public synchronized Reply setCoalitionRules(Caller caller, int tolerance, boolean allowMinority) {
        return core.asHead(caller, (town, g) -> {
            g.configureCoalitionRules(tolerance, allowMinority);
            return Message.of(MessageKey.GOV_COALITION_RULES, g.coalitionTolerance(),
                    GovernmentService.yesNo(g.allowMinority()));
        });
    }

    /** Fügt dem Koalitionsvertrag ein Vorhaben hinzu (Oberhaupt oder Kanzleramt). */
    public synchronized Reply addAgreement(Caller caller, String parameter, String value) {
        return core.as(caller, (town, g) -> mayor(caller, town) || chancellor(g, caller.player()), (town, g) -> {
            ParameterValue<?> decoded = decode(parameter, value);
            g.addAgreement(decoded.key().id(), ParameterCatalog.encode(decoded));
            return Message.of(MessageKey.GOV_AGREEMENT_ADDED, decoded.key().id(), texts.lawValue(decoded.value()));
        });
    }

    private boolean chancellor(Government g, ActorRef actor) {
        return g.ministries().stream().anyMatch(m -> m.portfolio() == Portfolio.CHANCELLERY
                && m.minister().filter(actor::equals).isPresent());
    }

    private Message formNow(PolityView town, Government g, Day day) {
        Parliament parliament = town.parliament();
        if (!parliament.seated()) {
            throw new GovernmentException(ErrorCode.PARLIAMENT_REQUIRED);
        }
        Map<PolityId, Integer> seats = new LinkedHashMap<>();
        parliament.seats().forEach(seat -> seat.party().ifPresent(party -> seats.merge(party, 1, Integer::sum)));
        List<CoalitionBuilder.Party> parties = new ArrayList<>();
        seats.forEach((party, count) -> {
            PartyProfile profile = queries.polity(party).flatMap(PolityView::partyProfile)
                    .orElse(new PartyProfile(0, 0, PartyProfile.DEFAULT_EMBLEM));
            parties.add(new CoalitionBuilder.Party(party, count, profile.order(), profile.economy()));
        });
        CoalitionBuilder.Result result = CoalitionBuilder.form(parties, parliament.size(), g.coalitionTolerance(),
                g.allowMinority(), day);
        if (result.coalition().isEmpty()) {
            boolean called = false;
            if (g.head().callsElections()) {
                called = callParliamentElection(town, day);
            }
            Message line = Message.of(MessageKey.GOV_FORMATION_FAILED, town.name().toString());
            if (called) {
                announcements.add(Announcement.toTown(town.id(), line));
            }
            return line;
        }
        if (g.coalition().isPresent()) {
            g.dismissGovernment();
        }
        Coalition coalition = result.coalition().get();
        Map<String, ActorRef> ministers = chooseMinisters(town, g, coalition, seats);
        g.install(coalition, ministers);
        negotiate(town, g, coalition, ministers);
        Message line = Message.of(coalition.minority() ? MessageKey.GOV_FORMED_MINORITY : MessageKey.GOV_FORMED,
                town.name().toString(), partyNames(coalition));
        announcements.add(Announcement.toTown(town.id(), line));
        return line;
    }

    private Map<String, ActorRef> chooseMinisters(PolityView town, Government g, Coalition coalition,
                                                  Map<PolityId, Integer> seats) {
        List<Ministry> open = g.ministries().stream().filter(m -> m.selection() == Selection.APPOINTMENT)
                .sorted((a, b) -> Integer.compare(rank(g, a), rank(g, b))).toList();
        Map<String, PolityId> allocation = CoalitionBuilder.allocate(open.stream().map(Ministry::key).toList(),
                coalition.parties(), seats);
        Set<ActorRef> used = new LinkedHashSet<>();
        g.ministries().stream().filter(m -> m.selection() == Selection.ELECTION)
                .forEach(m -> m.minister().ifPresent(used::add));
        Map<String, ActorRef> ministers = new LinkedHashMap<>();
        allocation.forEach((key, party) -> {
            List<ActorRef> pool = new ArrayList<>();
            town.parliament().seats().stream().filter(seat -> seat.party().filter(party::equals).isPresent())
                    .forEach(seat -> pool.add(seat.member()));
            queries.polity(party).ifPresent(p -> p.members().forEach(m -> pool.add(m.member())));
            pool.stream().filter(actor -> isCitizen(town, actor) && !used.contains(actor)
                    && !g.ministry(key).orElseThrow().employs(actor)).findFirst().ifPresent(actor -> {
                used.add(actor);
                ministers.put(key, actor);
            });
        });
        return ministers;
    }

    /**
     * Koalitionsverhandlung: Nehmen Dorfbewohner für eine Partei teil (als Parteivorsitzende oder als designierte
     * Minister), bringen sie die Forderungen ihrer Partei ein. Hat schon eine stärkere Partei dieselbe Regelung
     * gefordert, gilt deren Forderung (Kompromiss zugunsten des Verhandlungsführers).
     */
    private void negotiate(PolityView town, Government g, Coalition coalition, Map<String, ActorRef> ministers) {
        var laws = queries.effectiveLaws(town);
        Set<String> taken = new LinkedHashSet<>();
        for (PolityId party : coalition.parties()) {
            Optional<PolityView> view = queries.polity(party);
            if (view.isEmpty() || view.get().partyProfile().isEmpty()) {
                continue;
            }
            boolean villagerAtTable = view.get().mayor(villagerPolitics).filter(m -> m instanceof PersonRef).isPresent()
                    || ministers.values().stream().anyMatch(m -> m instanceof PersonRef
                    && queries.partyOf(m).map(PolityView::id).filter(party::equals).isPresent());
            if (!villagerAtTable) {
                continue;
            }
            PartyProfile profile = view.get().partyProfile().get();
            List<ParameterValue<?>> demands = new ArrayList<>();
            long tax = laws.valueOf(StandardParameters.INCOME_TAX_PERCENT);
            if (profile.economy() >= 40 && tax < 40) {
                demands.add(ParameterValue.of(StandardParameters.INCOME_TAX_PERCENT, tax + 5));
            } else if (profile.economy() <= -40 && tax > 0) {
                demands.add(ParameterValue.of(StandardParameters.INCOME_TAX_PERCENT, Math.max(0, tax - 5)));
            }
            boolean open = laws.valueOf(StandardParameters.OUTSIDERS_MAY_BUILD);
            if (profile.order() >= 40 && open) {
                demands.add(ParameterValue.of(StandardParameters.OUTSIDERS_MAY_BUILD, false));
            } else if (profile.order() <= -40 && !open) {
                demands.add(ParameterValue.of(StandardParameters.OUTSIDERS_MAY_BUILD, true));
            }
            long stimulus = laws.valueOf(StandardParameters.CONJUNCTURE_STIMULUS_PERCENT);
            if (profile.economy() >= 40 && stimulus < 50) {
                demands.add(ParameterValue.of(StandardParameters.CONJUNCTURE_STIMULUS_PERCENT, stimulus + 5));
            }
            for (ParameterValue<?> demand : demands) {
                if (taken.add(demand.key().id())) {
                    g.addAgreement(demand.key().id(), ParameterCatalog.encode(demand));
                    announcements.add(Announcement.toTown(town.id(), Message.of(MessageKey.GOV_DEMAND,
                            view.get().name().toString(), demand.key().id(), texts.lawValue(demand.value()))));
                }
            }
        }
    }

    private static int rank(Government g, Ministry m) {
        return m.portfolio() == Portfolio.CHANCELLERY ? 0 : m.key().equals(g.preparer()) ? 1 : 2;
    }

    private boolean callParliamentElection(PolityView town, Day day) {
        Optional<ActorRef> mayor = town.mayor(villagerPolitics);
        if (mayor.isEmpty()) {
            return false;
        }
        try {
            app.callElection().execute(new CallElection.Command(town.id(), mayor.get(), Office.PARLIAMENT, day));
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    private String partyNames(Coalition coalition) {
        return String.join(", ", coalition.parties().stream()
                .map(party -> queries.polity(party).map(p -> p.name().toString()).orElse("?")).toList());
    }

    // ------------------------------------------------------------------ Gesetzesvorschläge und Verordnungen

    public synchronized Reply setPortfolio(Caller caller, String ministry, Portfolio portfolio) {
        return core.asHead(caller, (town, g) -> {
            Ministry next = g.update(ministry, m -> m.withPortfolio(portfolio));
            return Message.of(MessageKey.GOV_MINISTRY_UPDATED, next.name());
        });
    }

    public synchronized Reply setDecrees(Caller caller, String ministry, boolean allowed) {
        return core.asHead(caller, (town, g) -> {
            Ministry next = g.update(ministry, m -> m.withDecrees(allowed));
            return Message.of(MessageKey.GOV_MINISTRY_UPDATED, next.name());
        });
    }

    /**
     * Ein Minister bringt eine Regelung seines Bereichs ein: als Verordnung, wenn das Oberhaupt es erlaubt hat und
     * kein Parlament sitzt, sonst als Antrag zur Abstimmung.
     */
    public synchronized Reply propose(Caller caller, String ministry, String parameter, String value) {
        return core.asHeadOrMinister(caller, ministry, (town, g) -> {
            Ministry m = g.require(ministry);
            ParameterValue<?> decoded = decode(parameter, value);
            if (!m.portfolio().owns(decoded.key().id())) {
                throw new GovernmentException(ErrorCode.NOT_IN_PORTFOLIO, m.name());
            }
            return applyLaw(town, caller.player(), decoded, m.decrees() && m.portfolio().decreeable(), m.name());
        });
    }

    private Message applyLaw(PolityView town, ActorRef proposer, ParameterValue<?> value, boolean mayDecree,
                             String ministryName) {
        Day day = calendar.today();
        boolean decree = mayDecree && !town.parliament().seated()
                && !StandardParameters.isConstitutional(value.key());
        Object shown = texts.lawValue(value.value());
        if (decree) {
            app.amendLaw().execute(new AmendLaw.Command(town.id(), proposer, value, day));
            Message line = Message.of(MessageKey.GOV_DECREED, ministryName, value.key().id(), shown);
            announcements.add(Announcement.toTown(town.id(), line));
            return line;
        }
        app.proposeLawChange().execute(new ProposeLawChange.Command(town.id(), proposer, value, day));
        Message line = Message.of(MessageKey.GOV_PROPOSED, ministryName, value.key().id(), shown);
        announcements.add(Announcement.toTown(town.id(), line));
        return line;
    }

    private static ParameterValue<?> decode(String parameter, String value) {
        ParameterCatalog catalog = ParameterCatalog.standard();
        Optional<ParameterKey<?>> key = catalog.find(parameter.strip());
        if (key.isEmpty()) {
            throw new GovernmentException(ErrorCode.UNKNOWN_PARAMETER, parameter);
        }
        try {
            return catalog.decode(key.get().id(), value.strip());
        } catch (RuntimeException e) {
            throw new GovernmentException(ErrorCode.INVALID_GOVERNMENT_VALUE, value);
        }
    }

    // ------------------------------------------------------------------ Förderungen

    /**
     * Legt eine Förderung an, bezahlt aus dem Etat des Ministeriums. Bei sitzendem Parlament muss dieses sie
     * beschließen. {@code recipient}: „alle“ für alle Bürger, sonst Spieler, Firma oder Gemeinde.
     *
     * @param days       Laufzeit in Tagen, 0 = unbefristet
     * @param fullUpTo   voller Betrag bis zu diesem Vermögen, 0 = keine Bedürftigkeitsprüfung
     * @param taperUntil ab {@code fullUpTo} linear sinkend bis 0 bei diesem Vermögen
     */
    public synchronized Reply createSubsidy(Caller caller, String name, String ministry, long amount,
                                            String recipient, int days, long fullUpTo, long taperUntil) {
        return core.asHeadOrMinister(caller, ministry, (town, g) -> {
            Optional<ActorRef> target = resolveRecipient(recipient);
            boolean active = !town.parliament().seated();
            Subsidy subsidy = g.addSubsidy(Subsidy.create(name, target, g.require(ministry).key(), amount,
                    days > 0 ? Optional.of(calendar.today().plus(days - 1L)) : Optional.empty(), fullUpTo,
                    taperUntil, active));
            Message line = Message.of(active ? MessageKey.GOV_SUBSIDY_CREATED : MessageKey.GOV_SUBSIDY_PENDING,
                    subsidy.name(), amount);
            announcements.add(Announcement.toTown(town.id(), line));
            return line;
        });
    }

    public synchronized Reply cancelSubsidy(Caller caller, String name) {
        return core.as(caller, (town, g) -> mayor(caller, town) || g.subsidy(name)
                .filter(sub -> g.isMinister(caller.player(), sub.ministry())).isPresent(), (town, g) -> {
            Subsidy subsidy = g.requireSubsidy(name);
            g.cancelSubsidy(name);
            return Message.of(MessageKey.GOV_SUBSIDY_CANCELLED, subsidy.name());
        });
    }

    /** Stimme der Abgeordneten zu einer noch nicht beschlossenen Förderung. */
    public synchronized Reply voteSubsidy(Caller caller, String name, boolean inFavour) {
        return core.as(caller, (town, g) -> core.voters(town).contains(caller.player()), (town, g) -> {
            boolean activated = g.voteSubsidy(name, caller.player(), inFavour, core.voters(town).size());
            if (activated) {
                Message line = Message.of(MessageKey.GOV_SUBSIDY_CREATED, g.requireSubsidy(name).name(),
                        g.requireSubsidy(name).amount());
                announcements.add(Announcement.toTown(town.id(), line));
                return line;
            }
            return Message.of(MessageKey.GOV_VOTED, GovernmentService.yesNo(inFavour));
        });
    }

    public synchronized Reply listSubsidies(Caller caller) {
        Optional<PolityView> town = core.townOf(caller);
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.GOV_NO_TOWN));
        }
        List<Subsidy> subsidies = core.registry().of(town.get().id()).subsidies();
        if (subsidies.isEmpty()) {
            return Reply.success(Message.of(MessageKey.GOV_NO_SUBSIDIES));
        }
        return Reply.success(subsidies.stream().map(this::subsidyLine).toList());
    }

    private Message subsidyLine(Subsidy sub) {
        return Message.of(MessageKey.GOV_SUBSIDY_LINE, sub.name(),
                sub.recipient().map(core::nameOf).orElse("*"), sub.amount(), sub.ministry(),
                sub.until().map(d -> Long.toString(d.value())).orElse("-"),
                sub.fullUpTo() <= 0 ? "-" : sub.fullUpTo() + ".." + sub.taperUntil(),
                GovernmentService.yesNo(sub.active()));
    }

    private Optional<ActorRef> resolveRecipient(String recipient) {
        String text = recipient.strip();
        if (text.equalsIgnoreCase("alle") || text.equalsIgnoreCase("all")) {
            return Optional.empty();
        }
        Optional<PlayerRef> player = players.findByName(text);
        if (player.isPresent()) {
            return Optional.of(player.get());
        }
        Optional<PolityView> polity = queries.polityNamed(text);
        if (polity.isPresent()) {
            return Optional.of(polity.get().asActor());
        }
        throw new GovernmentException(ErrorCode.UNKNOWN_RECIPIENT, recipient);
    }

    /** Zahlt die Förderungen des Tages aus dem Etat; ist der Etat leer, entfällt der Rest. */
    void paySubsidies(PolityView town, Government g, Day day) {
        for (Subsidy sub : g.expireSubsidies(day, false)) {
            announcements.add(Announcement.toTown(town.id(), Message.of(MessageKey.GOV_SUBSIDY_ENDED, sub.name())));
        }
        for (Subsidy sub : g.subsidies()) {
            if (!sub.runsOn(day) || g.ministry(sub.ministry()).isEmpty()) {
                continue;
            }
            List<ActorRef> recipients = new ArrayList<>();
            if (sub.forAllCitizens()) {
                town.members().stream().filter(m -> m.type() == MembershipType.CITIZEN)
                        .forEach(m -> recipients.add(m.member()));
            } else {
                recipients.add(sub.recipient().get());
            }
            for (ActorRef recipient : recipients) {
                long wealth = queries.ledger().balanceOf(recipient, town.currency()).amount();
                long due = Math.min(sub.amountFor(wealth), g.etatLeft(sub.ministry()));
                if (due > 0 && core.pay(town, recipient, due, TransferReason.GRANT, day)) {
                    g.spend(sub.ministry(), due);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Volkswahl der Minister

    /** Das Oberhaupt ruft die Wahl des Ministers aus; Dorfbewohner im Parlament treten selbst an. */
    public synchronized Reply callElection(Caller caller, String ministry) {
        return core.asHead(caller, (town, g) -> {
            long days = queries.effectiveLaws(town).valueOf(StandardParameters.ELECTION_DAYS);
            Day closes = calendar.today().plus(Math.max(1, days));
            MinistryElection election = g.callElection(ministry, closes);
            if (villagerPolitics) {
                town.parliament().seats().stream().map(Parliament.Seat::member)
                        .filter(member -> member instanceof PersonRef && isCitizen(town, member)).limit(2)
                        .forEach(member -> g.stand(election.ministry(), member));
            }
            Message line = Message.of(MessageKey.GOV_ELECTION_CALLED, g.require(ministry).name(), closes.value());
            announcements.add(Announcement.toTown(town.id(), line));
            return line;
        });
    }

    public synchronized Reply stand(Caller caller, String ministry) {
        return core.as(caller, (town, g) -> isCitizen(town, caller.player()), (town, g) -> {
            g.stand(ministry, caller.player());
            return Message.of(MessageKey.GOV_CANDIDATE, g.require(ministry).name());
        });
    }

    public synchronized Reply castBallot(Caller caller, String ministry, String candidateName) {
        Optional<PlayerRef> candidate = players.findByName(candidateName.strip());
        if (candidate.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.GOV_UNKNOWN_PLAYER, candidateName));
        }
        return core.as(caller, (town, g) -> isCitizen(town, caller.player()), (town, g) -> {
            g.castBallot(ministry, caller.player(), candidate.get());
            return Message.of(MessageKey.GOV_BALLOT, core.nameOf(candidate.get()), g.require(ministry).name());
        });
    }

    private void closeElections(PolityView town, Government g, Day day) {
        for (MinistryElection election : g.elections()) {
            if (day.isBefore(election.closesOn())) {
                continue;
            }
            for (var membership : town.members()) {
                ActorRef voter = membership.member();
                if (voter instanceof PersonRef && membership.type() == MembershipType.CITIZEN
                        && !g.election(election.ministry()).orElseThrow().ballots().containsKey(voter)
                        && !g.election(election.ministry()).orElseThrow().candidates().isEmpty()) {
                    g.castBallot(election.ministry(), voter, villagerChoice(g.election(election.ministry())
                            .orElseThrow(), voter));
                }
            }
            String name = g.require(election.ministry()).name();
            Message line = g.closeElection(election.ministry())
                    .map(winner -> Message.of(MessageKey.GOV_ELECTION_WON, name, core.nameOf(winner)))
                    .orElseGet(() -> Message.of(MessageKey.GOV_ELECTION_NO_WINNER, name));
            announcements.add(Announcement.toTown(town.id(), line));
        }
    }

    /** Ein Dorfbewohner wählt einen Kandidaten seiner Partei, sonst den derzeit führenden. */
    private ActorRef villagerChoice(MinistryElection election, ActorRef voter) {
        Optional<PolityId> party = queries.partyOf(voter).map(PolityView::id);
        if (party.isPresent()) {
            for (ActorRef candidate : election.candidates()) {
                if (queries.partyOf(candidate).map(PolityView::id).filter(party.get()::equals).isPresent()) {
                    return candidate;
                }
            }
        }
        return election.winner().orElse(election.candidates().get(0));
    }

    // ------------------------------------------------------------------ Tagesende und Anzeige

    /** Läuft vor den Lohnzahlungen: Regierungsbildung, Koalitionsvertrag, Dorfbewohner-Minister, Wahlen. */
    void beforeWages(PolityView town, Government g, Day day) {
        autoFormation(town, g, day);
        implementAgreement(town, g, day);
        villagerMinisters(town, g, day);
        closeElections(town, g, day);
    }

    private void autoFormation(PolityView town, Government g, Day day) {
        Parliament parliament = town.parliament();
        if (!parliament.seated()) {
            return;
        }
        boolean automatic = !g.head().appointsGovernment()
                || town.mayor(villagerPolitics).filter(mayor -> mayor instanceof PersonRef).isPresent();
        if (!automatic) {
            return;
        }
        boolean stale = g.coalition().map(c -> parliament.electedOn().filter(e -> e.isAfter(c.formedOn())).isPresent()
                || (!c.minority() && coalitionSeats(parliament, c) * 2 <= parliament.size())).orElse(true);
        if (stale) {
            try {
                formNow(town, g, day);
            } catch (DomainException e) {
                // wird am nächsten Tag erneut versucht
            }
        }
    }

    private static int coalitionSeats(Parliament parliament, Coalition coalition) {
        return (int) parliament.seats().stream()
                .filter(seat -> seat.party().filter(coalition::includes).isPresent()).count();
    }

    /** Setzt je Tag ein Vorhaben des Koalitionsvertrags um, über das Kanzleramt oder das Oberhaupt. */
    private void implementAgreement(PolityView town, Government g, Day day) {
        Optional<Coalition> coalition = g.coalition();
        if (coalition.isEmpty()) {
            return;
        }
        List<Coalition.Item> items = coalition.get().agreement();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).done()) {
                continue;
            }
            Optional<ActorRef> proposer = g.ministries().stream()
                    .filter(m -> m.portfolio() == Portfolio.CHANCELLERY).flatMap(m -> m.minister().stream())
                    .findFirst().or(() -> town.mayor(villagerPolitics));
            Coalition.Item item = items.get(i);
            g.markAgreementDone(i);
            if (proposer.isEmpty()) {
                return;
            }
            try {
                applyLaw(town, proposer.get(), decode(item.parameter(), item.encodedValue()), true, "Koalition");
            } catch (DomainException e) {
                announcements.add(Announcement.toTown(town.id(), Message.of(MessageKey.GOV_AGREEMENT_FAILED,
                        item.parameter(), texts.error(e))));
            }
            return;
        }
    }

    /**
     * Dorfbewohner als Minister handeln nach der Richtung ihrer Partei, je Haushaltsperiode einmal:
     * Finanzen (Einkommensteuer), Inneres (Fremde dürfen bauen), Wirtschaft (Konjunkturimpuls).
     */
    private void villagerMinisters(PolityView town, Government g, Day day) {
        if (day.value() % g.budgetInterval() != 1) {
            return;
        }
        for (Ministry ministry : g.ministries()) {
            Optional<ActorRef> minister = ministry.minister().filter(m -> m instanceof PersonRef);
            if (minister.isEmpty()) {
                continue;
            }
            PartyProfile profile = queries.partyOf(minister.get()).flatMap(PolityView::partyProfile).orElse(null);
            if (profile == null) {
                continue;
            }
            var laws = queries.effectiveLaws(town);
            ParameterValue<?> change = switch (ministry.portfolio()) {
                case FINANCE -> {
                    long tax = laws.valueOf(StandardParameters.INCOME_TAX_PERCENT);
                    yield profile.economy() >= 40 && tax < 40 ? ParameterValue.of(StandardParameters.INCOME_TAX_PERCENT, tax + 5)
                            : profile.economy() <= -40 && tax > 0
                            ? ParameterValue.of(StandardParameters.INCOME_TAX_PERCENT, Math.max(0, tax - 5)) : null;
                }
                case INTERIOR -> {
                    boolean open = laws.valueOf(StandardParameters.OUTSIDERS_MAY_BUILD);
                    yield profile.order() >= 40 && open ? ParameterValue.of(StandardParameters.OUTSIDERS_MAY_BUILD, false)
                            : profile.order() <= -40 && !open ? ParameterValue.of(StandardParameters.OUTSIDERS_MAY_BUILD, true)
                            : null;
                }
                case ECONOMY -> {
                    long stimulus = laws.valueOf(StandardParameters.CONJUNCTURE_STIMULUS_PERCENT);
                    yield profile.economy() >= 40 && stimulus < 50
                            ? ParameterValue.of(StandardParameters.CONJUNCTURE_STIMULUS_PERCENT, stimulus + 5) : null;
                }
                default -> null;
            };
            if (change == null) {
                continue;
            }
            try {
                applyLaw(town, minister.get(), change, ministry.decrees() && ministry.portfolio().decreeable(),
                        ministry.name());
            } catch (DomainException e) {
                // z. B. Antrag zum selben Gegenstand läuft schon: später erneut
            }
        }
    }

    /** Zusätzliche Zeilen der Übersicht: Koalition, Förderungen, laufende Ministerwahlen. */
    List<Message> overviewLines(PolityView town, Government g) {
        List<Message> lines = new ArrayList<>();
        lines.add(g.coalition().map(c -> Message.of(MessageKey.GOV_COALITION_LINE, partyNames(c),
                        GovernmentService.yesNo(c.minority()), c.formedOn().value(),
                        c.agreement().stream().filter(Coalition.Item::done).count(), c.agreement().size()))
                .orElseGet(() -> Message.of(MessageKey.GOV_NO_COALITION_LINE, g.coalitionTolerance(),
                        GovernmentService.yesNo(g.allowMinority()))));
        for (Subsidy sub : g.subsidies()) {
            lines.add(subsidyLine(sub));
        }
        for (MinistryElection e : g.elections()) {
            lines.add(Message.of(MessageKey.GOV_ELECTION_LINE, g.require(e.ministry()).name(), e.closesOn().value(),
                    e.candidates().size(), e.ballots().size()));
        }
        return lines;
    }

    // ------------------------------------------------------------------ Hilfen

    private static boolean mayor(Caller caller, PolityView town) {
        return town.holds(caller.player(), Office.MAYOR);
    }

    private static boolean isCitizen(PolityView town, ActorRef actor) {
        return town.membershipOf(actor).filter(m -> m.type() == MembershipType.CITIZEN).isPresent();
    }
}
