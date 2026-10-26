package de.etiennebader.civ.application.territory;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.polity.EnactStatute;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.chunk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TerritoryUseCasesTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PolityId nordhain = fixture.found("Nordhain", fixture.player(), 0, 0);

    @Test
    @DisplayName("Angrenzender Kern-Claim wird angenommen")
    void claimsAdjacentCoreChunk() {
        claim(1, 0, ClaimType.CORE);

        assertEquals(Optional.of(nordhain), fixture.app.queries().territory().sovereignOf(chunk(1, 0)));
    }

    @Test
    @DisplayName("Gebietsgrenze richtet sich nach der Mitgliederzahl: 2 + 1 × 1 = 3 Chunks")
    void claimLimitUsesMemberCount() {
        claim(1, 0, ClaimType.CORE);
        claim(2, 0, ClaimType.CORE);

        assertError(ErrorCode.CLAIM_LIMIT_REACHED, () -> claim(3, 0, ClaimType.CORE));
    }

    @Test
    @DisplayName("Außenposten erst, wenn ein Gesetz sie erlaubt")
    void outpostRequiresLaw() {
        assertError(ErrorCode.OUTPOST_LIMIT_REACHED, () -> claim(30, 30, ClaimType.OUTPOST));

        fixture.app.enactStatute().execute(new EnactStatute.Command(nordhain, "Außenpostengesetz",
                List.of(ParameterValue.of(StandardParameters.MAX_OUTPOSTS, 1)), DAY));
        claim(30, 30, ClaimType.OUTPOST);

        assertEquals(1, fixture.app.queries().territory().countOf(nordhain, ClaimType.OUTPOST));
    }

    @Test
    @DisplayName("Chunk mit Grundstück freigeben → CHUNK_HAS_PLOTS")
    void rejectsReleaseOfChunkWithPlots() {
        claim(1, 0, ClaimType.CORE);
        fixture.app.createPlot().execute(new CreatePlot.Command(nordhain,
                new Area(DimensionId.OVERWORLD, 16, 0, 20, 4), PlotType.RESIDENTIAL, DAY));

        assertError(ErrorCode.CHUNK_HAS_PLOTS, () -> release(1, 0));
        assertEquals(Optional.of(nordhain), fixture.app.queries().territory().sovereignOf(chunk(1, 0)));
    }

    @Test
    @DisplayName("Chunk ohne Grundstück wird freigegeben")
    void releasesEmptyChunk() {
        claim(1, 0, ClaimType.CORE);

        release(1, 0);

        assertEquals(Optional.empty(), fixture.app.queries().territory().sovereignOf(chunk(1, 0)));
    }

    private void claim(int x, int z, ClaimType type) {
        fixture.app.claimChunk().execute(new ClaimChunk.Command(nordhain, chunk(x, z), type, DAY));
    }

    private void release(int x, int z) {
        fixture.app.releaseChunk().execute(new ReleaseChunk.Command(nordhain, chunk(x, z), DAY));
    }

    private static void assertError(ErrorCode expected, Executable action) {
        assertEquals(expected, assertThrows(DomainException.class, action).code());
    }
}
