package de.etiennebader.civ.persistence.json;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.FundsTransferred;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.polity.PolityFounded;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventJsonTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID POLITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Test
    @DisplayName("Akteure, IDs, Tage, Namen und Beträge werden kompakt dargestellt")
    void encodesEvents() {
        assertEquals("{\"polity\":\"" + POLITY + "\",\"name\":\"Nord\\\"hain\",\"founder\":{\"kind\":\"PLAYER\",\"id\":\""
                        + PLAYER + "\"},\"occurredOn\":3}",
                EventJson.toJson(new PolityFounded(new PolityId(POLITY), PolityName.of("Nord\"hain"),
                        PlayerRef.of(PLAYER), Day.of(3))));

        assertEquals("{\"from\":{\"kind\":\"PLAYER\",\"id\":\"" + PLAYER + "\"},\"to\":{\"kind\":\"POLITY\",\"id\":\""
                        + POLITY + "\"},\"amount\":{\"amount\":250,\"currency\":\"EMERALD\"},\"reason\":\"LAND_TAX\",\"occurredOn\":9}",
                EventJson.toJson(new FundsTransferred(PlayerRef.of(PLAYER), new PolityRef(new PolityId(POLITY)),
                        Money.of(250, CurrencyId.EMERALD), TransferReason.LAND_TAX, Day.of(9))));
    }
}
