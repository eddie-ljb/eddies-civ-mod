package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.AlreadyCandidateException;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Wahlkreiswahl: Kandidatur in genau einem gültigen Wahlkreis, Stimmen nur im eigenen Wahlkreis (#47). */
class DistrictCandidacyTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PlayerRef carla = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);
    private final PolityId north;
    private final PolityId south;
    private final ElectionId election;

    DistrictCandidacyTest() {
        for (PlayerRef player : new PlayerRef[]{bob, carla}) {
            fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, player, MembershipType.CITIZEN, DAY));
        }
        north = fixture.app.civic().addTown(alice, nordhain, "Nord", nordhain, DAY).id().asDistrict();
        south = fixture.app.civic().define(alice, nordhain, "Sued", DAY).id().asDistrict();
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice,
                ParameterValue.of(StandardParameters.PARLIAMENT_VOTING, "districts"), DAY));
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice,
                ParameterValue.of(StandardParameters.PARLIAMENT_SEATS, 2L), DAY));
        election = fixture.app.callElection().execute(new CallElection.Command(nordhain, alice, Office.PARLIAMENT, DAY));
    }

    private void stand(PlayerRef who, Optional<PolityId> district) {
        fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, who, Platform.NONE,
                DAY, district));
    }

    @Test
    @DisplayName("Wahlkreiswahl: ohne oder mit fremdem Wahlkreis kann man nicht kandidieren")
    void districtRequiredAndValid() {
        assertEquals(ErrorCode.INVALID_CANDIDACY_DISTRICT,
                assertThrows(DomainException.class, () -> stand(bob, Optional.empty())).code());
        assertEquals(ErrorCode.INVALID_CANDIDACY_DISTRICT,
                assertThrows(DomainException.class, () -> stand(bob, Optional.of(nordhain))).code());
    }

    @Test
    @DisplayName("Wahlkreiswahl: nur in einem Wahlkreis kandidieren")
    void onlyOneDistrict() {
        stand(bob, Optional.of(north));
        assertThrows(AlreadyCandidateException.class, () -> stand(bob, Optional.of(south)));
    }

    @Test
    @DisplayName("Wahlkreiswahl: Stimmen nur für Kandidaten des eigenen Wahlkreises")
    void voteOnlyInOwnDistrict() {
        stand(bob, Optional.of(north));
        stand(carla, Optional.of(south));
        fixture.app.castBallot().execute(new CastBallot.Command(nordhain, election, alice, bob, DAY));
        assertEquals(ErrorCode.VOTE_OUTSIDE_DISTRICT, assertThrows(DomainException.class,
                () -> fixture.app.castBallot().execute(new CastBallot.Command(nordhain, election, alice, carla, DAY)))
                .code());
    }
}
