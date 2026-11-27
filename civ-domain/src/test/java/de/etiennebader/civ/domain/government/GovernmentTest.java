package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PlayerId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernmentTest {

    private static ActorRef actor() {
        return new PlayerRef(new PlayerId(UUID.randomUUID()));
    }

    private static ErrorCode code(Runnable action) {
        return assertThrows(GovernmentException.class, action::run).code();
    }

    @Test
    void createsMinistriesWithinLimit() {
        Government g = new Government();
        g.configureRules(2, 7, 50);
        g.create("Finanzministerium", "Kasse", Selection.APPOINTMENT);
        g.create("Innenministerium", "Ordnung", Selection.ELECTION);
        assertEquals(ErrorCode.TOO_MANY_MINISTRIES, code(() -> g.create("Wirtschaft", "", Selection.APPOINTMENT)));
        assertEquals(ErrorCode.MINISTRY_EXISTS, code(() -> g.create("finanzministerium", "", Selection.APPOINTMENT)));
        assertEquals(ErrorCode.UNKNOWN_MINISTRY, code(() -> g.require("gibtsnicht")));
    }

    @Test
    void staffAndMinisterAreExclusive() {
        Government g = new Government();
        g.create("Bildung", "Schulen", Selection.APPOINTMENT);
        ActorRef anna = actor();
        g.update("Bildung", m -> m.hire(anna));
        assertEquals(ErrorCode.ALREADY_EMPLOYED, code(() -> g.update("Bildung", m -> m.hire(anna))));
        g.appoint("Bildung", anna);
        assertTrue(g.require("Bildung").staff().isEmpty());
        assertTrue(g.isMinister(anna, "bildung"));
        g.dismissMinister("Bildung");
        assertEquals(ErrorCode.NOT_EMPLOYED, code(() -> g.dismissMinister("Bildung")));
    }

    @Test
    void budgetNeedsMajorityAndFunds() {
        Government g = new Government();
        g.create("Finanzministerium", "", Selection.APPOINTMENT);
        g.update("Finanzministerium", m -> m.withWages(10, 5));
        ActorRef minister = actor();
        ActorRef clerk = actor();
        g.appoint("Finanzministerium", minister);
        g.update("Finanzministerium", m -> m.hire(clerk));
        g.draftAllocation("Finanzministerium", 100);
        ActorRef a = actor();
        ActorRef b = actor();
        g.vote(a, true);
        g.vote(b, false);
        // 1 von 2 = 50 % ist nicht mehr als 50 %
        assertFalse(g.closeBudget(2, 1000, Day.of(1)));
        assertTrue(g.adopted().allocations().isEmpty());

        g.draftAllocation("Finanzministerium", 100);
        g.vote(a, true);
        g.vote(b, true);
        assertEquals(ErrorCode.BUDGET_EXCEEDS_FUNDS, code(() -> g.closeBudget(2, 50, Day.of(2))));

        g.draftAllocation("Finanzministerium", 100);
        g.vote(a, true);
        g.vote(b, true);
        assertTrue(g.closeBudget(2, 1000, Day.of(2)));
        assertEquals(100, g.require("Finanzministerium").etat());
        assertFalse(g.budgetDue(Day.of(5)));
        assertTrue(g.budgetDue(Day.of(9)));
    }

    @Test
    void wagesAreBoundedByEtat() {
        Government g = new Government();
        g.create("Wirtschaft", "", Selection.APPOINTMENT);
        g.update("Wirtschaft", m -> m.withWages(10, 6));
        ActorRef minister = actor();
        ActorRef clerk = actor();
        g.appoint("Wirtschaft", minister);
        g.update("Wirtschaft", m -> m.hire(clerk));
        g.update("Wirtschaft", m -> m.withEtat(15));
        List<Government.Wage> wages = g.dueWages();
        assertEquals(1, wages.size());
        assertEquals(minister, wages.get(0).employee());
        g.spend("Wirtschaft", 10);
        assertEquals(5, g.require("Wirtschaft").etat());
        assertTrue(g.dueWages().isEmpty());
        assertEquals(ErrorCode.ETAT_EXHAUSTED, code(() -> g.spend("Wirtschaft", 6)));
    }

    @Test
    void headOfStateIsConfigurable() {
        HeadOfState king = HeadOfState.DEFAULT.withForm(StateForm.MONARCHY).withSelection(Selection.APPOINTMENT)
                .withSalary(50).withPowers(false, true, false).withTitle("Hochkönig");
        assertEquals("Hochkönig", king.title());
        assertEquals(ErrorCode.INVALID_GOVERNMENT_VALUE,
                assertThrows(GovernmentException.class, () -> king.withSalary(-1)).code());
    }

    @Test
    void snapshotRoundTrips() {
        Government g = new Government();
        g.create("Kanzleramt", "Führung", Selection.APPOINTMENT);
        g.configureHead(HeadOfState.DEFAULT.withTitle("Reichspräsident"));
        Government copy = Government.restore(g.snapshot());
        assertEquals(g.snapshot(), copy.snapshot());
    }
}
