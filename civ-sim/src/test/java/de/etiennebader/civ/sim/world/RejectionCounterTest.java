package de.etiennebader.civ.sim.world;

import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.CurrencyMismatchException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RejectionCounterTest {

    @Test
    @DisplayName("Zählt nach Fehlercode und setzt den Tageszähler beim Abholen zurück")
    void countsPerCodeAndDay() {
        RejectionCounter counter = new RejectionCounter();
        CurrencyMismatchException rejection = new CurrencyMismatchException(CurrencyId.EMERALD, new CurrencyId("TALER"));

        counter.accept(rejection);
        counter.accept(rejection);

        assertEquals(2, counter.takeToday());
        assertEquals(0, counter.takeToday());
        assertEquals(Map.of(ErrorCode.CURRENCY_MISMATCH, 2L), counter.total());
    }
}
