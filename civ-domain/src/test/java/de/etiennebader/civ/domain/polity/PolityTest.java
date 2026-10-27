package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.domain.law.StandardParameters.LAND_TAX_PER_BLOCK;
import static de.etiennebader.civ.domain.testsupport.DomainAssertions.assertDomainError;
import static de.etiennebader.civ.domain.testsupport.Places.seatIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolityTest {

    private static final Day FOUNDING_DAY = Day.of(1);

    private final TestActors actors = new TestActors();
    private PlayerRef founder;
    private PersonRef villager;
    private Polity polity;

    @BeforeEach
    void foundPolity() {
        founder = actors.player();
        villager = actors.villager();
        polity = Polity.found(actors.polityId(), PolityName.of("Nordhain"), founder, CurrencyId.EMERALD, seatIn(0, 0),
                FOUNDING_DAY);
    }

    @Test
    @DisplayName("Gründung: Gründer ist CITIZEN, Event PolityFounded")
    void foundingMakesFounderCitizen() {
        assertEquals(Optional.of(new Membership(founder, MembershipType.CITIZEN, FOUNDING_DAY)), polity.membershipOf(founder));
        assertEquals(1, polity.memberCount());
        assertEquals(PolityKind.TERRITORIAL, polity.kind());
        assertEquals(SettlementTier.MUNICIPALITY, polity.tier());
        assertEquals(List.of(new PolityFounded(polity.id(), PolityName.of("Nordhain"), founder, seatIn(0, 0), FOUNDING_DAY)),
                polity.pullEvents());
    }

    @Test
    @DisplayName("Gründer ist eine Polity → ILLEGAL_FOUNDER")
    void rejectsPolityAsFounder() {
        assertDomainError(ErrorCode.ILLEGAL_FOUNDER, () -> Polity.found(
                actors.polityId(), PolityName.of("Tochterstadt"), actors.polity(), CurrencyId.EMERALD, seatIn(0, 0), FOUNDING_DAY));
    }

    @Test
    @DisplayName("Beitritt nimmt auf und erzeugt MemberAdmitted")
    void admitAddsMember() {
        polity.pullEvents();

        polity.admit(villager, MembershipType.RESIDENT, Day.of(2));

        assertTrue(polity.isMember(villager));
        assertEquals(List.of(new MemberAdmitted(polity.id(), villager, MembershipType.RESIDENT, Day.of(2))),
                polity.pullEvents());
    }

    @Test
    @DisplayName("Doppelter Beitritt → ALREADY_MEMBER")
    void rejectsDuplicateAdmission() {
        polity.admit(villager, MembershipType.CITIZEN, Day.of(2));

        assertDomainError(ErrorCode.ALREADY_MEMBER, () -> polity.admit(villager, MembershipType.RESIDENT, Day.of(3)));
    }

    @Test
    @DisplayName("Austritt entfernt das Mitglied und erzeugt MemberLeft")
    void leaveRemovesMember() {
        polity.admit(villager, MembershipType.CITIZEN, Day.of(2));
        polity.pullEvents();

        polity.leave(founder, Day.of(3));

        assertFalse(polity.isMember(founder));
        assertEquals(List.of(new MemberLeft(polity.id(), founder, Day.of(3))), polity.pullEvents());
    }

    @Test
    @DisplayName("Austritt eines Nichtmitglieds → NOT_A_MEMBER")
    void rejectsLeaveOfNonMember() {
        assertDomainError(ErrorCode.NOT_A_MEMBER, () -> polity.leave(villager, Day.of(2)));
    }

    @Test
    @DisplayName("Letztes Mitglied tritt aus → LAST_MEMBER")
    void rejectsLeaveOfLastMember() {
        assertDomainError(ErrorCode.LAST_MEMBER, () -> polity.leave(founder, Day.of(2)));
        assertTrue(polity.isMember(founder));
    }

    @Test
    @DisplayName("Gesetz erlassen und aufheben erzeugt Events und ändert das Recht")
    void enactAndRepealStatute() {
        Statute statute = Statute.of(StatuteId.generate(actors.ids()), "Grundsteuerordnung",
                ParameterValue.of(LAND_TAX_PER_BLOCK, 3L));
        polity.pullEvents();

        polity.enact(statute, Day.of(2));
        assertEquals(3L, polity.laws().valueOf(LAND_TAX_PER_BLOCK));

        polity.repeal(statute.id(), Day.of(3));
        assertEquals(0L, polity.laws().valueOf(LAND_TAX_PER_BLOCK));

        PolityId id = polity.id();
        assertEquals(List.of(
                new StatuteEnacted(id, statute.id(), Day.of(2)),
                new StatuteRepealed(id, statute.id(), Day.of(3))), polity.pullEvents());
    }

    @Test
    @DisplayName("Aufhebung eines unbekannten Gesetzes → UNKNOWN_STATUTE, kein Event")
    void rejectsRepealOfUnknownStatute() {
        polity.pullEvents();

        assertDomainError(ErrorCode.UNKNOWN_STATUTE, () -> polity.repeal(StatuteId.generate(actors.ids()), Day.of(2)));
        assertTrue(polity.pullEvents().isEmpty());
    }

    @Test
    @DisplayName("Ungültiger Name → INVALID_POLITY_NAME")
    void rejectsInvalidName() {
        assertDomainError(ErrorCode.INVALID_POLITY_NAME, () -> PolityName.of(" N "));
        assertDomainError(ErrorCode.INVALID_POLITY_NAME, () -> PolityName.of("N".repeat(33)));
        assertEquals("Nordhain", PolityName.of("  Nordhain ").value());
    }

    @Test
    @DisplayName("Die Polity handelt als PolityRef mit eigener ID")
    void actsAsPolityRef() {
        assertEquals(polity.id(), polity.asActor().id());
    }
}
