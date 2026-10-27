package de.etiennebader.civ.domain;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.AccountBalance;
import de.etiennebader.civ.domain.economy.CurrencyTotals;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotSnapshot;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolitySnapshot;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;
import de.etiennebader.civ.domain.testsupport.TestActors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.etiennebader.civ.domain.testsupport.Places.seatIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiederherstellen gespeicherter Aggregate: Zustand vollständig, keine Events, Invarianten geprüft.
 */
class RestoreTest {

    private static final CurrencyId EMERALD = CurrencyId.EMERALD;
    private static final DimensionId OVERWORLD = DimensionId.OVERWORLD;

    private final TestActors actors = new TestActors();

    @Nested
    @DisplayName("Polity")
    class PolityRestore {

        private final PolityId id = actors.polityId();
        private final PlayerRef alice = actors.player();
        private final PersonRef bertram = actors.villager();

        @Test
        @DisplayName("Mitglieder in Reihenfolge, Gesetze wirksam, keine Events")
        void restoresMembersAndLaws() {
            Statute tax = Statute.of(StatuteId.generate(actors.ids()), "Grundsteuerordnung",
                    ParameterValue.of(StandardParameters.LAND_TAX_PER_BLOCK, 4L));
            List<Membership> members = List.of(
                    new Membership(bertram, MembershipType.RESIDENT, Day.of(3)),
                    new Membership(alice, MembershipType.CITIZEN, Day.of(1)));

            Polity polity = Polity.restore(id, PolityName.of("Nordhain"), PolityKind.TERRITORIAL, SettlementTier.MUNICIPALITY,
                    EMERALD, Day.of(1), Optional.of(seatIn(0, 0)), members, List.of(new EnactedStatute(tax, Day.of(2))));

            assertEquals(members, List.copyOf(polity.members()));
            assertEquals(Optional.of(seatIn(0, 0)), polity.seat());
            assertEquals(4L, polity.laws().valueOf(StandardParameters.LAND_TAX_PER_BLOCK));
            assertTrue(polity.pullEvents().isEmpty());
        }

        @Test
        @DisplayName("Ein Snapshot ist eine unabhängige Kopie und ergibt wieder dieselbe Polity")
        void snapshotIsDetachedCopy() {
            Polity polity = Polity.found(id, PolityName.of("Nordhain"), alice, EMERALD, seatIn(0, 0), Day.of(1));
            PolitySnapshot snapshot = PolitySnapshot.of(polity);

            polity.admit(bertram, MembershipType.RESIDENT, Day.of(2));

            assertEquals(1, snapshot.members().size());
            Polity restored = snapshot.restore();
            assertEquals(polity.name(), restored.name());
            assertEquals(List.copyOf(snapshot.members()), List.copyOf(restored.members()));
            assertEquals(Optional.of(seatIn(0, 0)), restored.seat());
        }

        @Test
        @DisplayName("Ohne Mitglieder oder mit doppelter Mitgliedschaft → Fehler")
        void rejectsInvalidMembers() {
            assertThrows(IllegalArgumentException.class, () -> restoreWith(List.of()));
            Membership membership = new Membership(alice, MembershipType.CITIZEN, Day.ZERO);
            assertThrows(IllegalArgumentException.class, () -> restoreWith(List.of(membership, membership)));
        }

        private Polity restoreWith(List<Membership> members) {
            return Polity.restore(id, PolityName.of("Nordhain"), PolityKind.TERRITORIAL, SettlementTier.MUNICIPALITY,
                    EMERALD, Day.ZERO, Optional.empty(), members, List.of());
        }
    }

    @Test
    @DisplayName("TerritoryRegistry: Claims vollständig, keine Events")
    void restoresTerritory() {
        PolityId polity = actors.polityId();
        Map<ChunkCoord, Claim> claims = Map.of(
                new ChunkCoord(OVERWORLD, 0, 0), new Claim(polity, ClaimType.CORE, Day.of(1)),
                new ChunkCoord(OVERWORLD, 9, 9), new Claim(polity, ClaimType.OUTPOST, Day.of(5)));

        TerritoryRegistry registry = TerritoryRegistry.restore(claims);

        assertEquals(claims, registry.claims());
        assertEquals(1, registry.countOf(polity, ClaimType.OUTPOST));
        assertTrue(registry.pullEvents().isEmpty());
    }

    @Nested
    @DisplayName("LandRegistry")
    class LandRestore {

        private final PolityId owner = actors.polityId();
        private final PlayerRef alice = actors.player();

        @Test
        @DisplayName("Grundstücke und Inhaber-Index werden wiederhergestellt")
        void restoresPlots() {
            PlotSnapshot home = plot(new Area(OVERWORLD, 0, 0, 7, 7), PlotType.RESIDENTIAL, alice);
            PlotSnapshot embassy = plot(new Area(OVERWORLD, 8, 0, 11, 3), PlotType.EMBASSY, actors.polity());

            LandRegistry land = LandRegistry.restore(owner, List.of(home, embassy));

            assertEquals(List.of(home, embassy), land.plots().stream().map(p -> p.snapshot()).toList());
            assertEquals(List.of(home), land.plotsHeldBy(alice).stream().map(p -> p.snapshot()).toList());
            assertTrue(land.pullEvents().isEmpty());
        }

        @Test
        @DisplayName("Überlappung, doppelte ID oder ungültige Botschaft → Fehler")
        void rejectsInvalidPlots() {
            PlotSnapshot home = plot(new Area(OVERWORLD, 0, 0, 7, 7), PlotType.RESIDENTIAL, alice);
            PlotSnapshot overlapping = plot(new Area(OVERWORLD, 5, 5, 9, 9), PlotType.RESIDENTIAL, alice);
            PlotSnapshot embassyForPlayer = plot(new Area(OVERWORLD, 20, 20, 23, 23), PlotType.EMBASSY, alice);

            assertThrows(IllegalArgumentException.class, () -> LandRegistry.restore(owner, List.of(home, overlapping)));
            assertThrows(IllegalArgumentException.class, () -> LandRegistry.restore(owner, List.of(home, home)));
            assertThrows(RuntimeException.class, () -> LandRegistry.restore(owner, List.of(embassyForPlayer)));
        }

        private PlotSnapshot plot(Area area, PlotType type, ActorRef holder) {
            return new PlotSnapshot(PlotId.generate(actors.ids()), area, type, holder, Day.of(2));
        }
    }

    @Nested
    @DisplayName("Ledger")
    class LedgerRestore {

        private final PlayerRef alice = actors.player();
        private final PolityRef nordhain = actors.polity();

        @Test
        @DisplayName("Kontostände und Summen werden wiederhergestellt")
        void restoresBalances() {
            List<AccountBalance> balances = List.of(
                    new AccountBalance(alice, Money.of(70, EMERALD)), new AccountBalance(nordhain, Money.of(20, EMERALD)));
            List<CurrencyTotals> totals = List.of(new CurrencyTotals(EMERALD, Money.of(100, EMERALD), Money.of(10, EMERALD)));

            Ledger ledger = Ledger.restore(balances, totals);

            assertEquals(new HashSet<>(balances), new HashSet<>(ledger.accountBalances()));
            assertEquals(totals, ledger.currencyTotals());
            assertTrue(ledger.pullEvents().isEmpty());
        }

        @Test
        @DisplayName("Verletzte Geldmengenerhaltung oder Währung ohne Summen → Fehler")
        void rejectsInconsistentState() {
            List<AccountBalance> balances = List.of(new AccountBalance(alice, Money.of(70, EMERALD)));

            assertThrows(IllegalArgumentException.class, () -> Ledger.restore(balances,
                    List.of(new CurrencyTotals(EMERALD, Money.of(100, EMERALD), Money.of(0, EMERALD)))));
            assertThrows(IllegalArgumentException.class, () -> Ledger.restore(balances, List.of()));
        }
    }
}
