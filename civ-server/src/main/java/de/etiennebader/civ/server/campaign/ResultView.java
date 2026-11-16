package de.etiennebader.civ.server.campaign;

import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.command.Message;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein Wahlergebnis für Menü, Chat und Webseite.
 *
 * @param totals    Kandidaten mit Stimmen (100 = eine Bürgerstimme), die meisten zuerst
 * @param districts Ergebnis je Wahlbezirk
 * @param winners   Namen der Gewählten
 */
public record ResultView(PolityId polity, String polityName, Office office, Message officeName, long closedOn,
                         List<Line> totals, List<District> districts, List<String> winners) {

    /** Stimmen eines Kandidaten. */
    public record Line(String name, Optional<String> party, Optional<PolityId> partyId, long votes, long percent) {
    }

    /**
     * Ein Wahlbezirk.
     *
     * @param town       die Gemeinde; leer für Stimmen ohne Bezirk
     * @param leader     wer dort vorn liegt
     * @param leaderKey  Kennung für die Farbe auf der Karte: die Partei des Führenden, sonst er selbst
     */
    public record District(Optional<PolityId> town, String name, String leader, Optional<String> leaderParty,
                           String leaderKey, long votes, List<Line> lines) {
    }

    public ResultView {
        Objects.requireNonNull(polity, "polity");
        totals = List.copyOf(totals);
        districts = List.copyOf(districts);
        winners = List.copyOf(winners);
    }
}
