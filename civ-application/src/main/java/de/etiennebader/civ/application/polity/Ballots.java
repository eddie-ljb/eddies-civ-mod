package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;

/**
 * Auszählung: Stimmberechtigt sind alle Bürger, Personen wie Dorfbewohner nicht.
 *
 * <ul>
 *     <li>Angenommen, sobald mehr als die Hälfte der Bürger dafür gestimmt hat.</li>
 *     <li>Abgelehnt, sobald mindestens die Hälfte dagegen gestimmt hat.</li>
 *     <li>Nach Ablauf der Frist entscheidet die Mehrheit der abgegebenen Stimmen; Gleichstand lehnt ab.</li>
 * </ul>
 */
final class Ballots {

    private Ballots() {
    }

    static long electorate(Polity polity) {
        return polity.members().stream()
                .filter(membership -> membership.type() == MembershipType.CITIZEN)
                .filter(membership -> !(membership.member() instanceof PersonRef))
                .count();
    }

    static VoteOutcome evaluate(Polity polity, Proposal proposal) {
        long electorate = electorate(polity);
        if (proposal.inFavour() * 2 > electorate) {
            return VoteOutcome.ACCEPTED;
        }
        if (proposal.against() * 2 >= electorate) {
            return VoteOutcome.REJECTED;
        }
        return VoteOutcome.OPEN;
    }

    static VoteOutcome atDeadline(Proposal proposal) {
        return proposal.inFavour() > proposal.against() ? VoteOutcome.ACCEPTED : VoteOutcome.REJECTED;
    }

    /** Beendet die Abstimmung, falls entschieden, und setzt die Änderung bei Annahme in Kraft. */
    static VoteOutcome settle(Polity polity, Proposal proposal, VoteOutcome outcome, IdGenerator ids, Day day) {
        if (outcome == VoteOutcome.OPEN) {
            return outcome;
        }
        boolean accepted = outcome == VoteOutcome.ACCEPTED;
        polity.closeProposal(proposal.id(), accepted, day);
        if (accepted) {
            AmendLaw.apply(polity, proposal.change(), ids, day);
        }
        return outcome;
    }
}
