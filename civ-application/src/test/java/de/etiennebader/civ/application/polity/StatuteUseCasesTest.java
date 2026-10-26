package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.StatuteEnacted;
import de.etiennebader.civ.domain.polity.StatuteRepealed;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StatuteUseCasesTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PolityId nordhain = fixture.found("Nordhain", fixture.player(), 0, 0);

    @Test
    @DisplayName("Gesetz erlassen und wieder aufheben")
    void enactAndRepeal() {
        StatuteId statute = fixture.app.enactStatute().execute(new EnactStatute.Command(nordhain, "Grundsteuerordnung",
                List.of(ParameterValue.of(StandardParameters.LAND_TAX_PER_BLOCK, 3L)), DAY));

        assertEquals(3L, landTax());

        fixture.app.repealStatute().execute(new RepealStatute.Command(nordhain, statute, DAY.next()));

        assertEquals(0L, landTax());
        assertEquals(1, fixture.events.eventsOfType(StatuteEnacted.class).size());
        assertEquals(1, fixture.events.eventsOfType(StatuteRepealed.class).size());
    }

    private long landTax() {
        return fixture.app.queries().polity(nordhain).orElseThrow().laws().valueOf(StandardParameters.LAND_TAX_PER_BLOCK);
    }
}
