package de.etiennebader.civ.server.vote;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.polity.CastVote;
import de.etiennebader.civ.application.polity.ProposeLawChange;
import de.etiennebader.civ.application.polity.TallyProposals;
import de.etiennebader.civ.application.polity.VoteOutcome;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.polity.Vote;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.command.TranslationKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Abstimmungen im Spiel. Gilt in einer Gemeinde {@code laws_by_vote}, wird jede Rechtsänderung zum Antrag,
 * über den die Bürger abstimmen. Die Mitglieder der Gemeinde erfahren von neuen Anträgen und Ergebnissen.
 */
public final class VotingService {

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final Texts texts;
    private final Announcements announcements;

    public VotingService(CivApplication app, Calendar calendar, Texts texts, Announcements announcements) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
    }

    /** Ob Rechtsänderungen in dieser Gemeinde eine Abstimmung brauchen. */
    public static boolean votingRequired(PolityView town) {
        return town.laws().valueOf(StandardParameters.LAWS_BY_VOTE);
    }

    /** Stellt einen Antrag; wird aus {@code /civ recht setzen} aufgerufen, wenn abgestimmt werden muss. */
    public Reply propose(Caller caller, PolityView town, ParameterValue<?> change) {
        try {
            ProposeLawChange.Result result = app.proposeLawChange().execute(
                    new ProposeLawChange.Command(town.id(), caller.player(), change, calendar.today()));
            Message law = Message.of(TranslationKeys.law(change.key()));
            Object value = texts.lawValue(change.value());
            if (result.outcome() == VoteOutcome.ACCEPTED) {
                return Reply.success(Message.of(MessageKey.LAW_AMENDED, town.name().value(), law, value));
            }
            int number = numberOf(town, result);
            announcements.add(Announcement.toTown(town.id(),
                    Message.of(MessageKey.PROPOSAL_ANNOUNCED, caller.name(), law, value, number)));
            return Reply.success(Message.of(MessageKey.PROPOSAL_OPENED, law, value, number));
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    /** {@code /civ abstimmen <Nr> ja|nein} */
    public Reply vote(Caller caller, int number, boolean inFavour) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        List<Proposal> open = List.copyOf(town.get().proposals());
        if (number < 1 || number > open.size()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_PROPOSAL_NUMBER, number));
        }
        Proposal proposal = open.get(number - 1);
        try {
            VoteOutcome outcome = app.castVote().execute(new CastVote.Command(town.get().id(), proposal.id(),
                    caller.player(), inFavour, calendar.today()));
            Message law = Message.of(TranslationKeys.law(proposal.change().key()));
            Object value = texts.lawValue(proposal.change().value());
            if (outcome != VoteOutcome.OPEN) {
                announceResult(town.get(), law, value, outcome == VoteOutcome.ACCEPTED);
            }
            return Reply.success(Message.of(inFavour ? MessageKey.VOTED_YES : MessageKey.VOTED_NO, law, value));
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    /** {@code /civ abstimmungen}: offene Abstimmungen der eigenen Gemeinde. */
    public Reply list(Caller caller) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        List<ProposalView> proposals = proposalsOf(town.get(), caller.player());
        if (proposals.isEmpty()) {
            return Reply.success(Message.of(MessageKey.PROPOSALS_NONE, town.get().name().value()));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.PROPOSALS_HEADER, town.get().name().value()));
        for (ProposalView proposal : proposals) {
            lines.add(Message.of(MessageKey.PROPOSAL_ENTRY, proposal.number(), proposal.law(), proposal.value(),
                    proposal.inFavour(), proposal.against(), proposal.electorate(), proposal.decidedOn()));
        }
        return Reply.success(lines);
    }

    /** Offene Abstimmungen einer Gemeinde, nummeriert in Eröffnungsreihenfolge. */
    public List<ProposalView> proposalsOf(PolityView town, ActorRef viewer) {
        long electorate = town.members().stream()
                .filter(membership -> membership.type() == MembershipType.CITIZEN)
                .filter(membership -> !(membership.member() instanceof PersonRef))
                .count();
        long voteDays = town.laws().valueOf(StandardParameters.VOTE_DAYS);
        List<ProposalView> views = new ArrayList<>();
        int number = 1;
        for (Proposal proposal : town.proposals()) {
            Optional<Boolean> own = proposal.votes().stream()
                    .filter(vote -> vote.voter().equals(viewer))
                    .map(Vote::inFavour)
                    .findFirst();
            views.add(new ProposalView(number++, proposal.id(), Message.of(TranslationKeys.law(proposal.change().key())),
                    texts.lawValue(proposal.change().value()), texts.actor(proposal.proposer()), proposal.inFavour(),
                    proposal.against(), electorate, own, proposal.openedOn().plus(voteDays).value()));
        }
        return views;
    }

    /** Tagesende: abgelaufene Abstimmungen auszählen und das Ergebnis den Mitgliedern melden. */
    public void endOfDay(Day day) {
        for (TallyProposals.Tallied tallied : app.tallyProposals().execute(day)) {
            queries.polity(tallied.polity()).ifPresent(town -> announceResult(town,
                    Message.of(TranslationKeys.law(tallied.proposal().change().key())),
                    texts.lawValue(tallied.proposal().change().value()), tallied.outcome() == VoteOutcome.ACCEPTED));
        }
    }

    private void announceResult(PolityView town, Message law, Object value, boolean accepted) {
        announcements.add(Announcement.toTown(town.id(), Message.of(
                accepted ? MessageKey.PROPOSAL_ACCEPTED : MessageKey.PROPOSAL_REJECTED, town.name().value(), law, value)));
    }

    private int numberOf(PolityView town, ProposeLawChange.Result result) {
        int number = 1;
        for (Proposal proposal : queries.polity(town.id()).orElseThrow().proposals()) {
            if (proposal.id().equals(result.proposal())) {
                return number;
            }
            number++;
        }
        return number;
    }
}
