package de.etiennebader.civ.domain.law;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.etiennebader.civ.domain.law.StandardParameters.LAND_TAX_PER_BLOCK;
import static de.etiennebader.civ.domain.law.StandardParameters.MAX_OUTPOSTS;
import static de.etiennebader.civ.domain.law.StandardParameters.TAX_EMBASSIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParameterCatalogTest {

    private final ParameterCatalog catalog = ParameterCatalog.standard();

    @Test
    @DisplayName("Kodieren und Dekodieren ergibt denselben typisierten Wert")
    void roundTrips() {
        for (ParameterValue<?> value : List.of(ParameterValue.of(LAND_TAX_PER_BLOCK, 7L),
                ParameterValue.of(MAX_OUTPOSTS, 2), ParameterValue.of(TAX_EMBASSIES, true))) {
            assertEquals(value, catalog.decode(value.key().id(), ParameterCatalog.encode(value)));
        }
    }

    @Test
    @DisplayName("Unbekannte ID, falscher Typ oder verbotener Wert werden abgelehnt")
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> catalog.decode("unbekannt", "1"));
        assertThrows(IllegalArgumentException.class, () -> catalog.decode("tax_embassies", "ja"));
        assertThrows(IllegalArgumentException.class, () -> catalog.decode("max_outposts", "x"));
        assertThrows(RuntimeException.class, () -> catalog.decode("land_tax_per_block", "-1"));
    }

    @Test
    @DisplayName("Doppelte IDs im Katalog sind ein Programmierfehler")
    void rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException.class, () -> new ParameterCatalog(List.of(LAND_TAX_PER_BLOCK, LAND_TAX_PER_BLOCK)));
    }
}
