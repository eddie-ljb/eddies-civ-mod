package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.MemberAdmitted;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipUseCasesTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PolityId nordhain = fixture.found("Nordhain", fixture.player(), 0, 0);
    private final PolityId eichenfurt = fixture.found("Eichenfurt", fixture.player(), 20, 20);
    private final PersonRef bertram = fixture.villager();

    @Test
    @DisplayName("Beitritt nimmt auf und veröffentlicht MemberAdmitted")
    void joinAdmitsMember() {
        join(nordhain);

        assertTrue(fixture.app.queries().polity(nordhain).orElseThrow().isMember(bertram));
        assertEquals(1, fixture.events.eventsOfType(MemberAdmitted.class).size());
    }

    @Test
    @DisplayName("Beitritt zu einer zweiten Gemeinde → MEMBER_OF_OTHER_POLITY")
    void rejectsSecondMunicipality() {
        join(nordhain);

        assertEquals(ErrorCode.MEMBER_OF_OTHER_POLITY,
                assertThrows(DomainException.class, () -> join(eichenfurt)).code());
    }

    @Test
    @DisplayName("Nach dem Austritt ist ein Wechsel möglich")
    void canSwitchAfterLeaving() {
        join(nordhain);

        fixture.app.leavePolity().execute(new LeavePolity.Command(nordhain, bertram, DAY));
        join(eichenfurt);

        assertFalse(fixture.app.queries().polity(nordhain).orElseThrow().isMember(bertram));
        assertEquals(Optional.of(eichenfurt), fixture.app.queries().municipalityOf(bertram).map(p -> p.id()));
    }

    @Test
    @DisplayName("Unbekannte Gemeinde → UNKNOWN_POLITY")
    void rejectsUnknownPolity() {
        PolityId unknown = PolityId.generate(new SeededIdGenerator(123));

        assertEquals(ErrorCode.UNKNOWN_POLITY, assertThrows(DomainException.class, () -> join(unknown)).code());
    }

    private void join(PolityId polity) {
        fixture.app.joinPolity().execute(new JoinPolity.Command(polity, bertram, MembershipType.CITIZEN, DAY));
    }
}
