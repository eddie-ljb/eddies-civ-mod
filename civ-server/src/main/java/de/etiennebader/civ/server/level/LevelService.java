package de.etiennebader.civ.server.level;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.server.campaign.CampaignService;
import de.etiennebader.civ.server.election.DebateService;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.server.civic.CivicService;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.CivCommands;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.constitution.ConstitutionService;
import de.etiennebader.civ.server.federation.FederationService;
import de.etiennebader.civ.server.federation.Level;
import de.etiennebader.civ.server.law.LawBookService;
import de.etiennebader.civ.server.office.OfficeService;
import de.etiennebader.civ.server.parliament.ParliamentService;
import de.etiennebader.civ.server.vote.VotingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Befehle je Ebene: {@code /civ gemeinde|land|nation|<eigene Ebene> …} wirken auf die Polity dieser Ebene in der
 * Kette der eigenen Gemeinde, z. B. {@code /civ nation wahl ausrufen parlament} auf die Nation, zu der die eigene
 * Gemeinde (über ihr Land) gehört.
 */
public final class LevelService {

    private final WorldQueries queries;
    private final Texts texts;
    private final FederationService federations;
    private final CivCommands commands;
    private final OfficeService offices;
    private final VotingService voting;
    private final ParliamentService parliaments;
    private final ConstitutionService constitutions;
    private final CampaignService campaigns;
    private final LawBookService lawBooks;
    private final CivicService civic;
    private final DebateService debates;
    private final ParameterCatalog laws = ParameterCatalog.standard();

    public LevelService(WorldQueries queries, Texts texts, FederationService federations, CivCommands commands,
                        OfficeService offices, VotingService voting, ParliamentService parliaments,
                        ConstitutionService constitutions, CampaignService campaigns, LawBookService lawBooks,
                        CivicService civic, DebateService debates) {
        this.civic = Objects.requireNonNull(civic, "civic");
        this.debates = Objects.requireNonNull(debates, "debates");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.federations = Objects.requireNonNull(federations, "federations");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.offices = Objects.requireNonNull(offices, "offices");
        this.voting = Objects.requireNonNull(voting, "voting");
        this.parliaments = Objects.requireNonNull(parliaments, "parliaments");
        this.constitutions = Objects.requireNonNull(constitutions, "constitutions");
        this.campaigns = Objects.requireNonNull(campaigns, "campaigns");
        this.lawBooks = Objects.requireNonNull(lawBooks, "lawBooks");
    }

    /** Alle Ebenen nach Rang, von der Gemeinde aufwärts. */
    public List<Level> levels() {
        return federations.levels().all();
    }

    /** Die Ebene einer Polity, z. B. Land. */
    public Level levelOf(PolityView polity) {
        return federations.levelOf(polity);
    }

    /** Die eigene Gemeinde und ihre Bünde, von unten nach oben. */
    public List<PolityView> chainOf(ActorRef actor) {
        List<PolityView> chain = new ArrayList<>();
        queries.municipalityOf(actor).ifPresent(town -> {
            chain.add(town);
            chain.addAll(queries.ancestorsOf(town.id()));
        });
        return List.copyOf(chain);
    }

    /** Die Polity dieser Ebene in der Kette der eigenen Gemeinde. */
    public Optional<PolityView> polityOf(ActorRef actor, Level level) {
        return chainOf(actor).stream().filter(polity -> polity.level().equals(level.id())).findFirst();
    }

    /**
     * Führt eine Aktion für die Polity der Ebene aus; meldet, wenn es die Ebene nicht gibt oder die eigene Gemeinde
     * ihr nicht angehört.
     */
    public Reply with(Caller caller, String levelInput, Function<PolityView, Reply> action) {
        Optional<Level> level = federations.levels().find(levelInput);
        if (level.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_LEVEL, levelInput.strip()));
        }
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        return polityOf(caller.player(), level.get()).map(action)
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.LEVEL_NOT_JOINED, town.get().name().value(),
                        level.get().displayName(), level.get().id())));
    }

    /** {@code /civ ebenen}: die eigene Gemeinde und alle Bünde darüber mit Spitze und Befehl. */
    public Reply overview(Caller caller) {
        List<PolityView> chain = chainOf(caller.player());
        if (chain.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.LEVEL_CHAIN_HEADER));
        for (Level level : levels()) {
            Optional<PolityView> polity = chain.stream().filter(found -> found.level().equals(level.id())).findFirst();
            lines.add(polity.map(found -> Message.of(MessageKey.LEVEL_CHAIN_ENTRY, level.displayName(),
                            found.name().value(), found.mayor().map(texts::actor).orElse("-"), level.id()))
                    .orElseGet(() -> Message.of(MessageKey.LEVEL_CHAIN_MISSING, level.displayName())));
        }
        lines.add(Message.of(MessageKey.LEVEL_CHAIN_HINT));
        return Reply.success(lines);
    }

    /** {@code /civ <Ebene> [info]} */
    public Reply info(Caller caller, String level) {
        return with(caller, level, polity -> polity.kind() == PolityKind.FEDERATION
                ? federations.info(caller, Optional.of(polity.name().value()))
                : commands.describe(caller, Optional.of(polity.name().value())));
    }

    /** {@code /civ <Ebene> aemter} */
    public Reply offices(Caller caller, String level) {
        return with(caller, level, polity -> offices.list(caller, polity));
    }

    /** {@code /civ <Ebene> wahl ausrufen <Amt>} */
    public Reply callElection(Caller caller, String level, String office) {
        return with(caller, level, polity -> offices.callElection(caller, polity, office));
    }

    /** {@code /civ <Ebene> wahl kandidieren <Amt> [Programm]} */
    public Reply stand(Caller caller, String level, String office, Optional<String> platform) {
        return with(caller, level, polity -> offices.stand(caller, polity, office, platform));
    }

    /** {@code /civ <Ebene> wahl stimmen <Amt> <Kandidat>} */
    public Reply vote(Caller caller, String level, String office, String candidate) {
        return with(caller, level, polity -> offices.vote(caller, polity, office, candidate));
    }

    /** {@code /civ <Ebene> amt ernennen <Amt> <Spieler>} */
    public Reply appoint(Caller caller, String level, String office, String player) {
        return with(caller, level, polity -> offices.appoint(caller, polity, office, player));
    }

    /** {@code /civ <Ebene> amt entlassen <Amt>} */
    public Reply dismiss(Caller caller, String level, String office) {
        return with(caller, level, polity -> offices.dismiss(caller, polity, office));
    }

    /** {@code /civ <Ebene> parlament} */
    public Reply parliament(Caller caller, String level) {
        return with(caller, level, polity -> parliaments.info(caller, Optional.of(polity.name().value())));
    }

    /** {@code /civ <Ebene> recht} */
    public Reply laws(Caller caller, String level) {
        return with(caller, level, polity -> commands.laws(caller, Optional.of(polity.name().value())));
    }

    /** {@code /civ <Ebene> recht setzen <Regelung> <Wert>}: Verordnung der Spitze oder Antrag. */
    public Reply amendLaw(Caller caller, String level, String lawId, String value) {
        Optional<ParameterKey<?>> key = laws.find(lawId.strip());
        if (key.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_LAW, lawId.strip()));
        }
        return with(caller, level, polity -> commands.amendLaw(caller, polity, key.get(), value));
    }

    /** {@code /civ <Ebene> abstimmungen} */
    public Reply proposals(Caller caller, String level) {
        return with(caller, level, polity -> voting.list(caller, polity));
    }

    /** {@code /civ <Ebene> abstimmen <Nr> ja|nein} */
    public Reply voteOnProposal(Caller caller, String level, int number, boolean inFavour) {
        return with(caller, level, polity -> voting.vote(caller, polity, number, inFavour));
    }

    /** {@code /civ <Ebene> verfassung} */
    public Reply constitution(Caller caller, String level) {
        return with(caller, level, polity -> constitutions.show(caller, Optional.of(polity.name().value())));
    }

    /** {@code /civ <Ebene> verfassung ausrufen} */
    public Reply proclaim(Caller caller, String level) {
        return with(caller, level, polity -> constitutions.proclaim(caller, polity.id()));
    }

    /** {@code /civ <Ebene> gesetze} */
    public Reply lawBook(Caller caller, String level) {
        return with(caller, level, polity -> lawBooks.list(caller, Optional.of(polity.name().value())));
    }

    /** {@code /civ <Ebene> umfrage} */
    public Reply poll(Caller caller, String level) {
        return with(caller, level, polity -> campaigns.poll(caller, Optional.of(polity.name().value())));
    }

    /** {@code /civ <Ebene> wahlergebnis} */
    public Reply lastResult(Caller caller, String level) {
        return with(caller, level, polity -> campaigns.lastResult(caller, Optional.of(polity.name().value())));
    }

    /** {@code /civ <Ebene> wahlkreis liste} */
    public Reply constituencies(Caller caller, String level) {
        return with(caller, level, polity -> civic.list(caller, polity));
    }

    /** {@code /civ <Ebene> wahlkreis anlegen|gebiet|gebiet-entfernen|leeren|aufloesen <Name>} */
    public Reply constituency(Caller caller, String level, String action, String name) {
        return with(caller, level, polity -> switch (action) {
            case "anlegen" -> civic.define(caller, polity, name);
            case "gebiet" -> civic.addArea(caller, polity, name);
            case "gebiet-entfernen" -> civic.removeArea(caller, polity, name);
            case "leeren" -> civic.clear(caller, polity, name);
            case "aufloesen" -> civic.remove(caller, polity, name);
            default -> throw new IllegalArgumentException("Unbekannte Aktion: " + action);
        });
    }

    /** {@code /civ <Ebene> wahlkreis gemeinde <Name> <Gemeinde>} */
    public Reply constituencyTown(Caller caller, String level, String name, String town) {
        return with(caller, level, polity -> civic.addTown(caller, polity, name, town));
    }

    /** {@code /civ <Ebene> wahlkreis gemeinde-entfernen <Name> <Gemeinde>} */
    public Reply constituencyTownRemove(Caller caller, String level, String name, String town) {
        return with(caller, level, polity -> civic.removeTown(caller, polity, name, town));
    }

    /** {@code /civ <Ebene> hymne setzen <Titel> | [Instrument |] <Noten>} */
    public Reply setAnthem(Caller caller, String level, String input) {
        return with(caller, level, polity -> civic.setAnthem(caller, polity, input));
    }

    public Reply removeAnthem(Caller caller, String level) {
        return with(caller, level, polity -> civic.removeAnthem(caller, polity));
    }

    /** {@code /civ <Ebene> debatte starten [Amt]} */
    public Reply startDebate(Caller caller, String level, Optional<String> office) {
        Optional<Office> wanted = office.flatMap(OfficeService::parse);
        if (office.isPresent() && wanted.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_OFFICE, office.get().strip()));
        }
        return with(caller, level, polity -> debates.start(caller, polity, wanted, System.currentTimeMillis()));
    }

    /** Flagge setzen oder entfernen; die Flagge liest das Spiel aus einem Banner. */
    public Reply setFlag(Caller caller, String level, Optional<de.etiennebader.civ.domain.civic.Flag> flag) {
        return with(caller, level, polity -> civic.setFlag(caller, polity, flag));
    }
}
