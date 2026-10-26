package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityFounded;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkClaimed;
import de.etiennebader.civ.domain.territory.ClaimType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.chunk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundPolityTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();

    @Test
    @DisplayName("Gründung legt Gemeinde, ersten Kern-Chunk und leeres Grundbuch an")
    void foundsPolityWithFirstChunk() {
        PolityId id = fixture.found("Nordhain", alice, 0, 0);

        PolityView polity = fixture.app.queries().polity(id).orElseThrow();
        assertEquals(MembershipType.CITIZEN, polity.membershipOf(alice).orElseThrow().type());
        assertEquals(Optional.of(id), fixture.app.queries().territory().sovereignOf(chunk(0, 0)));
        assertTrue(fixture.app.queries().land(id).plots().isEmpty());
        assertEquals(Optional.of(polity), fixture.app.queries().municipalityOf(alice));
    }

    @Test
    @DisplayName("Gründung veröffentlicht PolityFounded und ChunkClaimed")
    void publishesEvents() {
        PolityId id = fixture.found("Nordhain", alice, 0, 0);

        assertEquals(List.of(PolityFounded.class, ChunkClaimed.class),
                fixture.events.events().stream().map(Object::getClass).toList());
        assertEquals(ClaimType.CORE, fixture.events.eventsOfType(ChunkClaimed.class).getFirst().type());
        assertEquals(id, fixture.events.eventsOfType(PolityFounded.class).getFirst().polity());
    }

    @Test
    @DisplayName("Belegter Chunk → CHUNK_ALREADY_CLAIMED, keine Gemeinde, keine Events")
    void rejectsOccupiedChunk() {
        fixture.found("Nordhain", alice, 0, 0);
        fixture.events.clear();

        var exception = assertThrows(DomainException.class,
                () -> fixture.found("Eichenfurt", fixture.player(), 0, 0));

        assertEquals(ErrorCode.CHUNK_ALREADY_CLAIMED, exception.code());
        assertEquals(1, fixture.app.queries().polities().size());
        assertTrue(fixture.events.events().isEmpty());
    }

    @Test
    @DisplayName("Gründer gehört schon einer Gemeinde an → MEMBER_OF_OTHER_POLITY")
    void rejectsFounderWithMunicipality() {
        fixture.found("Nordhain", alice, 0, 0);

        var exception = assertThrows(MemberOfOtherPolityException.class, () -> fixture.found("Eichenfurt", alice, 10, 10));

        assertEquals(List.of(alice, "Nordhain"), exception.arguments());
        assertEquals(DAY, fixture.app.queries().polities().getFirst().foundedOn());
    }
}
