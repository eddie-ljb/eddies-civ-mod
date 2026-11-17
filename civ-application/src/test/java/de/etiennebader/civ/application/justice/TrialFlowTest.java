package de.etiennebader.civ.application.justice;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.justice.CaseKind;
import de.etiennebader.civ.domain.justice.CourtCase;
import de.etiennebader.civ.domain.justice.Role;
import de.etiennebader.civ.domain.justice.Side;
import de.etiennebader.civ.domain.justice.Stage;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrialFlowTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef mayor = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PlayerRef carla = fixture.player();
    private final PlayerRef dora = fixture.player();
    private final PolityId town = fixture.found("Nordhain", mayor, 0, 0);

    private JusticeUseCases justice() {
        return fixture.app.justice();
    }

    private void setUp(int villagers) {
        for (PlayerRef citizen : new PlayerRef[]{bob, carla, dora}) {
            fixture.app.joinPolity().execute(new JoinPolity.Command(town, citizen, MembershipType.CITIZEN, DAY));
            fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(citizen, emeralds(5_000), DAY));
        }
        for (int i = 0; i < villagers; i++) {
            PersonRef villager = fixture.villager();
            fixture.app.joinPolity().execute(new JoinPolity.Command(town, villager, MembershipType.RESIDENT, DAY));
        }
    }

    @Test
    @DisplayName("Aussagen, Beweise und Plädoyers gehen bis zur Beratung, auch von Zeugen")
    void statementsAndEvidenceUntilDeliberation() {
        setUp(0);
        CourtCase filed = justice().file(bob, town, CaseKind.CIVIL, carla, "Zaun beschädigt", "300", DAY);
        justice().testify(carla, filed.id(), "Ich war es nicht.", DAY);
        justice().submit(bob, filed.id(), "buch", "Foto", "Zaun kaputt", 2, DAY);
        justice().open(mayor, filed.id(), DAY);
        justice().summon(bob, filed.id(), dora, DAY);
        CourtCase withWitness = justice().submit(dora, filed.id(), "text", "Beobachtung",
                "Ich sah Carla den Zaun beschädigen, sie ist schuldig.", 1, DAY);
        assertEquals(Side.PROSECUTION, withWitness.evidence().getLast().side());
        justice().plead(bob, filed.id(), "Die Beweise sind eindeutig.", DAY);
        justice().advance(mayor, filed.id(), DAY);
        assertEquals(Stage.PLEADINGS, current(filed).stage());
        justice().testify(dora, filed.id(), "Noch etwas: es war nachts.", DAY);
        justice().submit(carla, filed.id(), "text", "Quittung", "Zaun war schon alt", 1, DAY);
        justice().plead(carla, filed.id(), "Ich bin unschuldig.", DAY);
        justice().advance(mayor, filed.id(), DAY);
        assertEquals(Stage.DELIBERATION, current(filed).stage());
        assertThrows(IllegalStateException.class, () -> justice().testify(carla, filed.id(), "zu spät", DAY));
        assertEquals(4L, current(filed).statements().size());
        assertEquals(3L, current(filed).evidence().size());
    }

    @Test
    @DisplayName("Urteil schon bei den Plädoyers: die Dorfbewohner-Geschworenen stimmen vorher ab")
    void juryVotesBeforeEarlyVerdict() {
        setUp(8);
        CourtCase filed = justice().file(bob, town, CaseKind.CRIMINAL, carla, "Diebstahl", "", DAY);
        CourtCase opened = justice().open(mayor, filed.id(), DAY);
        assertTrue(opened.hasJury());
        for (int i = 0; i < 4; i++) {
            justice().submit(bob, filed.id(), "vertrag", "Beleg " + i, "Carla hat gestohlen, schuldig", 3, DAY);
        }
        justice().advance(mayor, filed.id(), DAY);
        ActorRef juror = current(filed).jurors().getFirst();
        if (juror instanceof PlayerRef player) {
            justice().vote(player, filed.id(), true, DAY);
        }
        CourtCase decided = justice().decide(mayor, filed.id(), true, 500, 0, false, false, "klar", DAY);
        assertEquals(current(filed).jurors().size(), decided.votes().size(), "alle Geschworenen haben gestimmt");
        assertEquals(Stage.DECIDED, decided.stage());
    }

    @Test
    @DisplayName("Berufung ohne höhere Ebene: dasselbe Gericht verhandelt neu, mit anderem Richter")
    void appealWithinSameCourt() {
        setUp(3);
        CourtCase filed = justice().file(bob, town, CaseKind.CIVIL, carla, "Schulden", "200", DAY);
        justice().open(mayor, filed.id(), DAY);
        justice().submit(bob, filed.id(), "vertrag", "Schuldschein", "Carla schuldet 2 Smaragde", 3, DAY);
        justice().advance(mayor, filed.id(), DAY);
        justice().decide(mayor, filed.id(), true, 0, 200, false, false, "Schuldschein", DAY);
        assertThrows(IllegalArgumentException.class, () -> justice().appeal(bob, filed.id(), DAY));
        CourtCase appeal = justice().appeal(carla, filed.id(), DAY);
        assertEquals(town, appeal.court());
        assertEquals(Optional.of(filed.id()), appeal.appealOf());
        assertEquals(1L, appeal.evidence().size());
        assertEquals(Stage.APPEALED, current(filed).stage());
        // der Richter der Vorinstanz urteilt nicht erneut
        assertThrows(IllegalArgumentException.class, () -> justice().open(mayor, appeal.id(), DAY));
        justice().endOfDay(DAY.plus(1));
        CourtCase reopened = current(appeal);
        assertEquals(Stage.EVIDENCE, reopened.stage());
        assertNotEquals(Optional.of((ActorRef) mayor), reopened.judge());
        assertTrue(reopened.judge().isPresent());
        assertFalse(reopened.roleOf(reopened.judge().get()).filter(role -> role != Role.JUDGE).isPresent());
    }

    private CourtCase current(CourtCase found) {
        return fixture.app.queries().justice().courtCase(found.id()).orElseThrow();
    }
}
