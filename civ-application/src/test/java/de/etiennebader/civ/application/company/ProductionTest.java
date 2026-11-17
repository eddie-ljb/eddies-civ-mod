package de.etiennebader.civ.application.company;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.market.PriceLevel;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.production.Branch;
import de.etiennebader.civ.domain.production.Enterprise;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionTest {

    private static final Commodity LOG = Commodity.of("minecraft:oak_log");

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();

    private PolityId company() {
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(20_000), DAY));
        return fixture.app.companies().foundCompany(alice, PolityName.of("Holz AG"), emeralds(10_000), DAY);
    }

    private ProductionUseCases production() {
        return fixture.app.production();
    }

    private long balance(PolityId company) {
        return fixture.app.queries().ledger().balanceOf(fixture.app.queries().polity(company).orElseThrow().asActor(),
                EMERALD).amount();
    }

    private List<ProductionUseCases.DayResult> runDay(int offset) {
        return production().endOfDay(commodity -> OptionalLong.of(8), PriceLevel.NEUTRAL, DAY.plus(offset));
    }

    private void hire(PolityId company, de.etiennebader.civ.domain.actor.ActorRef worker) {
        var actor = fixture.app.queries().polity(company).orElseThrow().asActor();
        Contract offer = fixture.app.contracts().offerEmployment(actor, worker, emeralds(50), 0, DAY);
        if (!(worker instanceof PersonRef)) {
            fixture.app.contracts().sign(offer.id(), worker, DAY);
        }
    }

    @Test
    @DisplayName("Ohne Branche keine Produktion; Belegschaft und Ausstattung heben den Ertrag")
    void workforceAndEquipmentRaiseOutput() {
        PolityId company = company();
        assertThrows(IllegalStateException.class, () -> production().invest(alice, company, emeralds(100), DAY));
        production().chooseBranch(alice, company, Branch.FORESTRY, DAY);
        production().autoSell(alice, company, false, DAY);
        long alone = production().forecast(company).get(LOG);
        assertEquals(Math.round(24 * Math.pow(0.5, 0.85)), alone);

        hire(company, fixture.villager());
        hire(company, bob);
        long team = production().forecast(company).get(LOG);
        assertTrue(team > alone * 3, team + " vs " + alone);

        long before = balance(company);
        Enterprise invested = production().invest(alice, company, emeralds(2_000), DAY);
        assertEquals(1L, invested.level());
        assertEquals(before - 2_000, balance(company));
        assertTrue(production().forecast(company).get(LOG) > team);

        runDay(1);
        Enterprise stocked = fixture.app.queries().production().enterpriseOf(company).orElseThrow();
        assertEquals(production().forecast(company).get(LOG).longValue(), stocked.stockOf(LOG));
        assertTrue(runDay(1).isEmpty(), "nur einmal am Tag");

        assertEquals(5L, production().take(alice, company, LOG, 5, DAY));
        assertThrows(IllegalArgumentException.class, () -> production().take(alice, company, Commodity.of("minecraft:dirt"), 1, DAY));
        assertThrows(RuntimeException.class, () -> production().take(bob, company, LOG, 1, DAY));
    }

    @Test
    @DisplayName("Automatischer Verkauf bringt Erlös in die Firmenkasse")
    void autoSellEarnsRevenue() {
        PolityId company = company();
        production().chooseBranch(alice, company, Branch.SHEEP, DAY);
        hire(company, fixture.villager());
        long before = balance(company);
        ProductionUseCases.DayResult day = runDay(1).getFirst();
        assertTrue(day.revenue().amount() > 0);
        assertEquals(before + day.revenue().amount(), balance(company));
        Enterprise after = fixture.app.queries().production().enterpriseOf(company).orElseThrow();
        assertTrue(after.stock().isEmpty());
        assertEquals(day.revenue().amount(), after.lastRevenue());
    }

    @Test
    @DisplayName("Ausstattungsstufen: 20, 60, 140 Smaragde")
    void levels() {
        Enterprise start = Enterprise.start(new PolityId(java.util.UUID.randomUUID()), Branch.MINE);
        assertEquals(0L, start.level());
        assertEquals(2_000L, start.toNextLevel());
        assertEquals(2L, new Enterprise(start.company(), Branch.MINE, 6_000, true, java.util.Map.of(),
                java.util.Optional.empty(), 0, 0).level());
        assertEquals(3L, new Enterprise(start.company(), Branch.MINE, 14_000, true, java.util.Map.of(),
                java.util.Optional.empty(), 0, 0).level());
    }
}
