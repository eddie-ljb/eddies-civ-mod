package de.etiennebader.civ.application.federation;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.office.CallElection;
import de.etiennebader.civ.application.polity.DissolvePolity;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PlayerRef carla = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);
    private final PolityId eichenfurt = fixture.found("Eichenfurt", carla, 20, 20);

    @Test
    @DisplayName("Ein Land aus einer Gemeinde: Bürger und Dorfbewohner werden Mitglieder, der Gründer führt es")
    void foundState() {
        join(nordhain, bob, MembershipType.CITIZEN);
        PersonRef villager = fixture.villager();
        join(nordhain, villager, MembershipType.RESIDENT);

        PolityId land = found("Westmark", "land", nordhain, alice);

        PolityView state = polity(land);
        assertEquals(PolityKind.FEDERATION, state.kind());
        assertEquals("land", state.level());
        assertTrue(state.seat().isEmpty());
        assertEquals(List.of(new PolityRef(nordhain)), state.constituents());
        assertTrue(state.isMember(bob));
        assertEquals(MembershipType.RESIDENT, state.membershipOf(villager).orElseThrow().type());
        assertEquals(Optional.of(alice), state.mayor());
        assertEquals(Optional.of(state), fixture.app.queries().parentOf(nordhain));
        assertEquals(nordhain, fixture.app.queries().municipalityOf(bob).orElseThrow().id(), "Gemeinde bleibt Gemeinde");
    }

    @Test
    @DisplayName("Wer einer Gemeinde beitritt oder sie verlässt, tut das auch in allen Bünden darüber")
    void membershipFollowsUpwards() {
        PolityId land = found("Westmark", "land", nordhain, alice);
        PolityId nation = found("Reich", "nation", land, alice);

        join(nordhain, bob, MembershipType.CITIZEN);
        assertTrue(polity(land).isMember(bob));
        assertTrue(polity(nation).isMember(bob));
        assertEquals(List.of(polity(land), polity(nation)), fixture.app.queries().ancestorsOf(nordhain));

        fixture.app.leavePolity().execute(new LeavePolity.Command(nordhain, bob, DAY));
        assertFalse(polity(land).isMember(bob));
        assertFalse(polity(nation).isMember(bob));
    }

    @Test
    @DisplayName("Beitritt und Austritt einer Gemeinde nehmen ihre Leute mit; das letzte Glied kann nicht austreten")
    void joinAndLeave() {
        PolityId land = found("Westmark", "land", nordhain, alice);
        fixture.app.joinFederation().execute(new JoinFederation.Command(land, eichenfurt, DAY));
        assertTrue(polity(land).isMember(carla));
        assertEquals(2, polity(land).constituents().size());

        assertEquals(ErrorCode.ALREADY_IN_FEDERATION, assertThrows(DomainException.class, () -> fixture.app
                .joinFederation().execute(new JoinFederation.Command(land, eichenfurt, DAY))).code());
        assertEquals(ErrorCode.FEDERATION_CYCLE, assertThrows(DomainException.class, () -> fixture.app
                .joinFederation().execute(new JoinFederation.Command(land, land, DAY))).code());
        assertEquals(ErrorCode.NOT_A_FEDERATION, assertThrows(DomainException.class, () -> fixture.app
                .joinFederation().execute(new JoinFederation.Command(eichenfurt, nordhain, DAY))).code());

        assertEquals(land, fixture.app.leaveFederation().execute(new LeaveFederation.Command(eichenfurt, DAY)));
        assertFalse(polity(land).isMember(carla));
        assertTrue(fixture.app.queries().parentOf(eichenfurt).isEmpty());
        assertEquals(ErrorCode.LAST_CONSTITUENT, assertThrows(DomainException.class, () -> fixture.app
                .leaveFederation().execute(new LeaveFederation.Command(nordhain, DAY))).code());
        assertEquals(ErrorCode.NOT_IN_FEDERATION, assertThrows(DomainException.class, () -> fixture.app
                .leaveFederation().execute(new LeaveFederation.Command(eichenfurt, DAY))).code());
    }

    @Test
    @DisplayName("Löst sich das letzte Glied auf, verschwindet der Bund mit; sonst bleibt er ohne dieses Glied")
    void dissolution() {
        PolityId land = found("Westmark", "land", nordhain, alice);
        fixture.app.joinFederation().execute(new JoinFederation.Command(land, eichenfurt, DAY));

        fixture.app.dissolvePolity().execute(new DissolvePolity.Command(eichenfurt, carla, DAY));
        assertEquals(List.of(new PolityRef(nordhain)), polity(land).constituents());
        assertFalse(polity(land).isMember(carla));

        fixture.app.dissolvePolity().execute(new DissolvePolity.Command(nordhain, alice, DAY));
        assertTrue(fixture.app.queries().polity(land).isEmpty());
    }

    @Test
    @DisplayName("Namen sind eindeutig, eine Gemeinde gehört höchstens einem Bund an, gewählt wird auch im Bund")
    void rulesAndElections() {
        PolityId land = found("Westmark", "land", nordhain, alice);
        assertEquals(ErrorCode.POLITY_NAME_TAKEN, assertThrows(DomainException.class,
                () -> found("Nordhain", "land", eichenfurt, carla)).code());
        assertEquals(ErrorCode.ALREADY_IN_FEDERATION, assertThrows(DomainException.class,
                () -> found("Ostmark", "land", nordhain, alice)).code());
        assertEquals(ErrorCode.NOT_A_MEMBER, assertThrows(DomainException.class,
                () -> found("Ostmark", "land", eichenfurt, alice)).code());
        assertEquals(ErrorCode.NOT_A_MUNICIPALITY, assertThrows(DomainException.class,
                () -> join(land, bob, MembershipType.CITIZEN)).code());

        fixture.app.callElection().execute(new CallElection.Command(land, alice, Office.MAYOR, DAY));
        assertTrue(polity(land).electionFor(Office.MAYOR).isPresent());
    }

    private PolityId found(String name, String level, PolityId constituent, PlayerRef head) {
        return fixture.app.foundFederation().execute(new FoundFederation.Command(new PolityName(name), level,
                constituent, head, DAY));
    }

    private void join(PolityId polity, ActorRef member, MembershipType type) {
        fixture.app.joinPolity().execute(new JoinPolity.Command(polity, member, type, DAY));
    }

    private PolityView polity(PolityId id) {
        return fixture.app.queries().polity(id).orElseThrow();
    }
}
