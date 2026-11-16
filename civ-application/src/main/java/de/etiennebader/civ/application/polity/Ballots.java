package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.StatuteId;

/**
 * Auszählung: Stimmberechtigt sind alle Bürger, Personen wie Dorfbewohner nicht.
 *
 * <ul>
 *     <li>Angenommen, sobald mehr als die Hälfte der Bürger dafür gestimmt hat (bei Verfassungsartikeln einer
 *     ausgerufenen Verfassung: mindestens {@link StandardParameters#AMENDMENT_MAJORITY_PERCENT} aller Bürger).</li>
 *     <li>Abgelehnt, sobald diese Mehrheit nicht mehr erreichbar ist.</li>
 *     <li>Nach Ablauf der Frist entscheiden die abgegebenen Stimmen mit derselben Quote; Gleichstand lehnt ab.</li>
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

    static long requiredPercent(PolityView polity, ParameterKey<?> key) {
        return Majorities.requiredPercent(polity, key);
    }

    static VoteOutcome evaluate(Polity polity, Proposal proposal) {
        long electorate = electorate(polity);
        long percent = Majorities.requiredPercent(polity, proposal.motion());
        if (reaches(proposal.inFavour(), electorate, percent)) {
            return VoteOutcome.ACCEPTED;
        }
        if (!reaches(electorate - proposal.against(), electorate, percent)) {
            return VoteOutcome.REJECTED;
        }
        return VoteOutcome.OPEN;
    }

    static VoteOutcome atDeadline(Polity polity, Proposal proposal) {
        long cast = proposal.inFavour() + proposal.against();
        long percent = Majorities.requiredPercent(polity, proposal.motion());
        return cast > 0 && reaches(proposal.inFavour(), cast, percent) ? VoteOutcome.ACCEPTED : VoteOutcome.REJECTED;
    }

    /** Mehr als die Hälfte und mindestens {@code percent} Prozent von {@code total}. */
    private static boolean reaches(long votes, long total, long percent) {
        return votes * 2 > total && votes * 100 >= total * percent;
    }

    /** Setzt einen angenommenen Antrag um. */
    static void carryOut(Polity polity, Motion motion, IdGenerator ids, Day day) {
        switch (motion) {
            case Motion.Change change -> AmendLaw.apply(polity, change.change(), ids, day);
            case Motion.Enact enact -> polity.enact(new Statute(StatuteId.generate(ids), enact.title(), enact.text(),
                    enact.values()), day);
            case Motion.Repeal repeal -> {
                if (polity.laws().inForce().stream().anyMatch(law -> law.statute().id().equals(repeal.statute()))) {
                    polity.repeal(repeal.statute(), day);
                }
            }
        }
    }

    /** Beendet die Abstimmung, falls entschieden, und setzt die Änderung bei Annahme in Kraft. */
    static VoteOutcome settle(Polity polity, Proposal proposal, VoteOutcome outcome, IdGenerator ids, Day day) {
        if (outcome == VoteOutcome.OPEN) {
            return outcome;
        }
        boolean accepted = outcome == VoteOutcome.ACCEPTED;
        polity.closeProposal(proposal.id(), accepted, day);
        if (accepted) {
            carryOut(polity, proposal.motion(), ids, day);
        }
        return outcome;
    }
}
