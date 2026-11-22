package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.civic.CivicView;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.law.VotingMethod;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;

/**
 * Wann eine Wahl eine Wahlkreiswahl ist (#47): eine Parlamentswahl nach {@code districts} oder {@code mixed} in
 * einer Polity mit eigenen Wahlkreisen. Dort kandidiert man in genau einem Wahlkreis und wird nur dort gewählt.
 */
final class DistrictVoting {

    private DistrictVoting() {
    }

    static boolean applies(PolityView polity, Election election, CivicView civic) {
        VotingMethod.Parliament method = VotingMethod.Parliament.of(
                polity.laws().valueOf(StandardParameters.PARLIAMENT_VOTING));
        return election.office() == Office.PARLIAMENT
                && (method == VotingMethod.Parliament.DISTRICTS || method == VotingMethod.Parliament.MIXED)
                && !civic.constituenciesOf(polity.id()).isEmpty();
    }
}
