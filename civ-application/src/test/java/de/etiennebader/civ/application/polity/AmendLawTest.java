package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmendLawTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Ein Bürger ändert eine Regelung; die frühere Einzelregelung wird aufgehoben")
    void amendReplacesEarlierSingleRule() {
        amend(alice, ParameterValue.of(StandardParameters.OUTSIDERS_MAY_BUILD, true));
        amend(alice, ParameterValue.of(StandardParameters.OUTSIDERS_MAY_BUILD, false));

        PolityView polity = fixture.app.queries().polity(nordhain).orElseThrow();
        assertFalse(polity.laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));
        assertEquals(1, polity.laws().inForce().size());
        assertEquals("outsiders_may_build", polity.laws().inForce().getFirst().statute().title());
    }

    @Test
    @DisplayName("Gesetze mit mehreren Regelungen bleiben bestehen, die neue Regelung hat Vorrang")
    void keepsCombinedStatutes() {
        fixture.app.enactStatute().execute(new EnactStatute.Command(nordhain, "Gastrecht",
                List.of(ParameterValue.of(StandardParameters.OUTSIDERS_MAY_BUILD, true),
                        ParameterValue.of(StandardParameters.OUTSIDERS_MAY_INTERACT, true)), DAY));

        amend(alice, ParameterValue.of(StandardParameters.OUTSIDERS_MAY_BUILD, false));

        PolityView polity = fixture.app.queries().polity(nordhain).orElseThrow();
        assertEquals(2, polity.laws().inForce().size());
        assertFalse(polity.laws().valueOf(StandardParameters.OUTSIDERS_MAY_BUILD));
        assertTrue(polity.laws().valueOf(StandardParameters.OUTSIDERS_MAY_INTERACT));
    }

    @Test
    @DisplayName("Nur Bürger dürfen das Recht ändern, weder Fremde noch Einwohner")
    void onlyCitizensMayAmend() {
        PlayerRef outsider = fixture.player();
        var villager = fixture.villager();
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, villager, MembershipType.RESIDENT, DAY));

        var exception = assertThrows(NotACitizenException.class,
                () -> amend(outsider, ParameterValue.of(StandardParameters.OUTSIDERS_MAY_BUILD, true)));
        assertEquals(ErrorCode.NOT_A_CITIZEN, exception.code());
        assertThrows(NotACitizenException.class,
                () -> fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, villager,
                        ParameterValue.of(StandardParameters.OUTSIDERS_MAY_BUILD, true), DAY)));
    }

    private void amend(PlayerRef citizen, ParameterValue<?> value) {
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, citizen, value, DAY));
    }
}
