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

import java.util.Optional;

import static de.etiennebader.civ.domain.testsupport.Places.seatIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotRightsRulesTest {

    private static final DimensionId OVERWORLD = DimensionId.OVERWORLD;
    private static final Day DAY = Day.of(1);

    private final TestActors actors = new TestActors(12);
    private final ProtectionRules rules = new ProtectionRules();
    private final TerritoryRegistry territory = TerritoryRegistry.empty();
    private final PlayerRef alice = actors.player();
    private final PlayerRef bertram = actors.player();
    private final PlayerRef carla = actors.player();
    private final PlayerRef dave = actors.player();

    private Polity nordhain;
    private PlotId home;
    private PlotRights rights = PlotRights.DEFAULT;
    private Context context;
    private final BlockPosition inHome = block(20, 4);
    private final BlockPosition onCommon = block(12, 12);

    @BeforeEach
    void buildTown() {
        nordhain = Polity.found(actors.polityId(), PolityName.of("Nordhain"), alice, CurrencyId.EMERALD, seatIn(0, 0), DAY);
        nordhain.admit(bertram, MembershipType.CITIZEN, DAY);
        territory.claim(new ClaimContext(nordhain.id(), ClaimType.CORE, new ChunkCoord(OVERWORLD, 0, 0), 1, 0),
                ClaimPolicy.ALLOW_ALL, DAY);
        territory.claim(new ClaimContext(nordhain.id(), ClaimType.CORE, new ChunkCoord(OVERWORLD, 1, 0), 1, 0),
                ClaimPolicy.ALLOW_ALL, DAY);
        LandRegistry land = LandRegistry.open(nordhain.id());
        home = PlotId.generate(actors.ids());
        land.createPlot(home, new Area(OVERWORLD, 16, 0, 23, 7), PlotType.RESIDENTIAL, territory, DAY);
        land.assign(home, bertram, DAY);
        context = new Context(territory, nordhain, land);
    }

    private void law(de.etiennebader.civ.domain.law.ParameterKey<Boolean> key, boolean value) {
        nordhain.enact(Statute.of(StatuteId.generate(actors.ids()), "Test " + key.id(), ParameterValue.of(key, value)), DAY);
    }

    private boolean allowed(ActorRef actor, ProtectedAction action, BlockPosition at) {
        return rules.check(actor, action, at, context).isEmpty();
    }

    @Test
    @DisplayName("Ohne Einstellungen bleibt alles wie bisher")
    void defaultsKeepOldBehaviour() {
        assertTrue(allowed(bertram, ProtectedAction.BUILD, inHome));
        assertTrue(allowed(alice, ProtectedAction.INTERACT, inHome));
        assertFalse(allowed(alice, ProtectedAction.OPEN_CONTAINER, inHome));
        assertFalse(allowed(dave, ProtectedAction.INTERACT, inHome));
    }

    @Test
    @DisplayName("Der Inhaber erlaubt Fremden einzelne Handlungen, andere bleiben verboten")
    void holderOpensPlotToOutsiders() {
        rights = PlotRights.DEFAULT.withOutsiders(ProtectedAction.OPEN_CONTAINER, true);

        assertTrue(allowed(dave, ProtectedAction.OPEN_CONTAINER, inHome));
        assertFalse(allowed(dave, ProtectedAction.BUILD, inHome));
        assertFalse(allowed(dave, ProtectedAction.OPEN_CONTAINER, onCommon), "Gemeindeland ist nicht betroffen");
    }

    @Test
    @DisplayName("Gäste bekommen eigene Rechte; Mitglieder lassen sich einschränken")
    void guestsAndRestrictedMembers() {
        rights = PlotRights.DEFAULT.withGuest(dave, ProtectedAction.BUILD, true)
                .withMembers(ProtectedAction.INTERACT, false);

        assertTrue(allowed(dave, ProtectedAction.BUILD, inHome));
        assertFalse(allowed(carla, ProtectedAction.BUILD, inHome));
        assertFalse(allowed(alice, ProtectedAction.INTERACT, inHome), "Mitglieder dürfen keine Türen mehr benutzen");
        assertTrue(allowed(bertram, ProtectedAction.INTERACT, inHome), "der Inhaber darf immer");
        assertTrue(rights.withoutGuest(dave).guests().isEmpty());
    }

    @Test
    @DisplayName("Mit Überschreibungsrecht entscheidet allein der Inhaber, ohne gilt das Recht auch auf dem Grundstück")
    void lawOverrideSwitch() {
        law(StandardParameters.OUTSIDERS_MAY_BUILD, true);

        // Überschreiben erlaubt (Standard): der Inhaber hat nichts erlaubt, also darf Dave nicht bauen.
        assertFalse(allowed(dave, ProtectedAction.BUILD, inHome));
        // Überschreiben verboten: Was das Recht Fremden erlaubt, ist überall erlaubt.
        law(StandardParameters.PLOT_RIGHTS_OVERRIDE, false);
        assertTrue(allowed(dave, ProtectedAction.BUILD, inHome));
        assertFalse(allowed(dave, ProtectedAction.OPEN_CONTAINER, inHome));
    }

    @Test
    @DisplayName("Regeln wie Explosionen, PvP und Tierschutz stellt der Inhaber nur mit Überschreibungsrecht anders ein")
    void flagsOverrideLaw() {
        assertFalse(rules.allows(Hazard.EXPLOSION, inHome, context), "Gesetz: keine Explosionen");
        rights = PlotRights.DEFAULT.withStance(PlotFlag.EXPLOSIONS, Stance.ALLOW)
                .withStance(PlotFlag.PVP, Stance.DENY);

        assertTrue(rules.allows(Hazard.EXPLOSION, inHome, context), "auf dem Grundstück erlaubt");
        assertFalse(rules.allows(Hazard.EXPLOSION, onCommon, context), "Gemeindeland bleibt geschützt");
        assertFalse(rules.allows(PlotFlag.PVP, inHome, context), "PvP auf dem Grundstück verboten");
        assertTrue(rules.allows(PlotFlag.PVP, onCommon, context), "im übrigen Gebiet gilt das Gesetz");

        law(StandardParameters.PLOT_RIGHTS_OVERRIDE, false);
        assertFalse(rules.allows(Hazard.EXPLOSION, inHome, context), "ohne Überschreibungsrecht gilt das Gesetz");
        assertTrue(rules.allows(PlotFlag.PVP, inHome, context));
    }

    @Test
    @DisplayName("Einstellungen, die dem Standard gleichen, gelten als Standard")
    void defaultDetection() {
        assertTrue(PlotRights.DEFAULT.isDefault());
        assertTrue(PlotRights.DEFAULT.withStance(PlotFlag.PVP, Stance.ALLOW).withStance(PlotFlag.PVP, Stance.INHERIT)
                .isDefault());
        assertFalse(PlotRights.DEFAULT.withOutsiders(ProtectedAction.BUILD, true).isDefault());
    }

    private static BlockPosition block(int x, int z) {
        return new BlockPosition(OVERWORLD, x, 70, z);
    }

    private final class Context implements ProtectionContext {

        private final TerritoryView territory;
        private final Polity polity;
        private final LandRegistry land;

        Context(TerritoryView territory, Polity polity, LandRegistry land) {
            this.territory = territory;
            this.polity = polity;
            this.land = land;
        }

        @Override
        public TerritoryView territory() {
            return territory;
        }

        @Override
        public Optional<? extends PolityView> polity(PolityId id) {
            return id.equals(polity.id()) ? Optional.of(polity) : Optional.empty();
        }

        @Override
        public Optional<? extends LandView> land(PolityId id) {
            return id.equals(polity.id()) ? Optional.of(land) : Optional.empty();
        }

        @Override
        public Optional<PlotRights> plotRights(PolityId id, PlotId plot) {
            return plot.equals(home) ? Optional.of(rights) : Optional.empty();
        }
    }
}
