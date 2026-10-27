package de.etiennebader.civ.domain.protection;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimContext;
import de.etiennebader.civ.domain.territory.ClaimPolicy;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;
import de.etiennebader.civ.domain.territory.TerritoryView;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static de.etiennebader.civ.domain.testsupport.Places.seatIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionRulesTest {

    private static final DimensionId OVERWORLD = DimensionId.OVERWORLD;
    private static final Day DAY = Day.of(1);

    private final TestActors actors = new TestActors(9);
    private final ProtectionRules rules = new ProtectionRules();
    private final TerritoryRegistry territory = TerritoryRegistry.empty();
    private final PlayerRef alice = actors.player();
    private final PlayerRef bertram = actors.player();
    private final PlayerRef carla = actors.player();
    private final PlayerRef dave = actors.player();

    private Polity nordhain;
    private Polity eichenfurt;
    private LandRegistry land;
    private Context context;

    @BeforeEach
    void buildTown() {
        nordhain = Polity.found(actors.polityId(), PolityName.of("Nordhain"), alice, CurrencyId.EMERALD, seatIn(0, 0), DAY);
        nordhain.admit(bertram, MembershipType.CITIZEN, DAY);
        eichenfurt = Polity.found(actors.polityId(), PolityName.of("Eichenfurt"), carla, CurrencyId.EMERALD, seatIn(20, 20), DAY);
        claim(nordhain, 0, 0);
        claim(nordhain, 1, 0);
        claim(eichenfurt, 20, 20);

        land = LandRegistry.open(nordhain.id());
        PlotId home = PlotId.generate(actors.ids());
        land.createPlot(home, new Area(OVERWORLD, 16, 0, 23, 7), PlotType.RESIDENTIAL, territory, DAY);
        land.assign(home, bertram, DAY);
        PlotId embassy = PlotId.generate(actors.ids());
        land.createPlot(embassy, new Area(OVERWORLD, 24, 0, 27, 3), PlotType.EMBASSY, territory, DAY);
        land.assign(embassy, eichenfurt.asActor(), DAY);
        land.createPlot(PlotId.generate(actors.ids()), new Area(OVERWORLD, 0, 0, 3, 3), PlotType.PUBLIC, territory, DAY);

        context = new Context(territory, Map.of(nordhain.id(), nordhain, eichenfurt.id(), eichenfurt),
                Map.of(nordhain.id(), land));
    }

    @Test
    @DisplayName("In der Wildnis darf jeder alles")
    void wildernessIsFree() {
        assertTrue(check(dave, ProtectedAction.BUILD, block(500, 500)).isEmpty());
    }

    @Test
    @DisplayName("Mitglieder dürfen auf Gemeindeland alles, auch auf Grundstücken der Gemeinde selbst")
    void membersMayUseCommonLand() {
        for (ProtectedAction action : ProtectedAction.values()) {
            assertTrue(check(bertram, action, block(12, 12)).isEmpty(), action::name);
            assertTrue(check(alice, action, block(1, 1)).isEmpty(), action::name);
        }
    }

    @Test
    @DisplayName("Fremde werden abgewiesen, bis ein Gesetz die jeweilige Handlung erlaubt")
    void outsidersNeedLaw() {
        assertEquals(Optional.of(new Denial(DenialReason.FOREIGN_TERRITORY, nordhain.id(), nordhain.asActor())),
                check(dave, ProtectedAction.OPEN_CONTAINER, block(12, 12)));

        nordhain.enact(Statute.of(StatuteId.generate(actors.ids()), "Gastrecht",
                ParameterValue.of(StandardParameters.OUTSIDERS_MAY_OPEN_CONTAINERS, true)), DAY);

        assertTrue(check(dave, ProtectedAction.OPEN_CONTAINER, block(12, 12)).isEmpty());
        assertEquals(DenialReason.FOREIGN_TERRITORY, check(dave, ProtectedAction.BUILD, block(12, 12)).orElseThrow().reason());
    }

    @Test
    @DisplayName("Das Gemeindehaus darf niemand verändern, benutzen dürfen es Mitglieder")
    void seatIsProtected() {
        BlockPosition seat = seatIn(0, 0);

        assertEquals(DenialReason.SEAT, rules.check(alice, ProtectedAction.BUILD, seat, context).orElseThrow().reason());
        assertTrue(rules.check(alice, ProtectedAction.INTERACT, seat, context).isEmpty());
        assertTrue(check(alice, ProtectedAction.BUILD, new BlockPosition(OVERWORLD, 8, 65, 8)).isEmpty());
    }

    @Test
    @DisplayName("Fremde Grundstücke: nur der Inhaber baut, Mitbürger dürfen nur Türen benutzen")
    void privatePlotBelongsToHolder() {
        BlockPosition inHome = block(20, 4);

        assertTrue(check(bertram, ProtectedAction.BUILD, inHome).isEmpty());
        assertEquals(Optional.of(new Denial(DenialReason.FOREIGN_PLOT, nordhain.id(), bertram)),
                check(alice, ProtectedAction.OPEN_CONTAINER, inHome));
        assertTrue(check(alice, ProtectedAction.INTERACT, inHome).isEmpty());
        assertEquals(DenialReason.FOREIGN_PLOT, check(dave, ProtectedAction.INTERACT, inHome).orElseThrow().reason());
    }

    @Test
    @DisplayName("Auf einer Botschaft gelten die Mitglieder der Gastgemeinde als Inhaber")
    void embassyBelongsToForeignPolity() {
        BlockPosition inEmbassy = block(25, 1);

        assertTrue(check(carla, ProtectedAction.BUILD, inEmbassy).isEmpty());
        assertEquals(Optional.of(new Denial(DenialReason.FOREIGN_PLOT, nordhain.id(), eichenfurt.asActor())),
                check(alice, ProtectedAction.BUILD, inEmbassy));
    }

    private Optional<Denial> check(ActorRef actor, ProtectedAction action, BlockPosition position) {
        return rules.check(actor, action, position, context);
    }

    private static BlockPosition block(int x, int z) {
        return new BlockPosition(OVERWORLD, x, 70, z);
    }

    private void claim(Polity polity, int chunkX, int chunkZ) {
        territory.claim(new ClaimContext(polity.id(), ClaimType.CORE, new ChunkCoord(OVERWORLD, chunkX, chunkZ), 1, 0),
                ClaimPolicy.ALLOW_ALL, DAY);
    }

    private record Context(TerritoryView territory, Map<PolityId, Polity> polities, Map<PolityId, LandRegistry> lands)
            implements ProtectionContext {

        @Override
        public Optional<? extends PolityView> polity(PolityId id) {
            return Optional.ofNullable(polities.get(id));
        }

        @Override
        public Optional<? extends LandView> land(PolityId polity) {
            return Optional.ofNullable(lands.get(polity));
        }
    }
}
