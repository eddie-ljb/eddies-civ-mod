package de.etiennebader.civ.server.election;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.civic.CivicRegistry;
import de.etiennebader.civ.domain.civic.CivicView;
import de.etiennebader.civ.domain.civic.Constituency;
import de.etiennebader.civ.domain.civic.ConstituencyId;
import de.etiennebader.civ.domain.polity.ElectionResult;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die Farb-/Bezirksauflösung für die Wahlkarte (#9) ab: Gebiete desselben Wahlbezirks müssen dieselbe Farbe
 * zeigen, unterschiedliche Bezirke unterschiedliche, und ohne Ergebnis bzw. ohne Partei fällt die Auflösung auf
 * sinnvolle Ersatzwerte zurück statt zu crashen.
 */
class ElectionMapColorTest {

    private final PolityId nation = new PolityId(UUID.randomUUID());
    private final PolityId townA = new PolityId(UUID.randomUUID());
    private final PolityId townB = new PolityId(UUID.randomUUID());
    private final PolityId townC = new PolityId(UUID.randomUUID());
    private final ConstituencyId districtAId = new ConstituencyId(UUID.randomUUID());
    private final ConstituencyId districtBId = new ConstituencyId(UUID.randomUUID());
    private final ActorRef candidateA = new PersonRef(new PersonId(UUID.randomUUID()));
    private final ActorRef candidateB = new PersonRef(new PersonId(UUID.randomUUID()));

    private final CivicView civic = CivicRegistry.restore(
            List.of(
                    new Constituency(districtAId, nation, "Bezirk A", List.of(), List.of(townA)),
                    new Constituency(districtBId, nation, "Bezirk B", List.of(), List.of(townB))),
            Map.of(), Map.of());

    @Test
    @DisplayName("Gemeinden desselben Wahlbezirks lösen auf denselben Bezirksschlüssel auf, ein anderer Bezirk auf einen anderen")
    void districtOfGroupsByConstituency() {
        PolityId district = ElectionMapColor.districtOf(civic, nation, Optional.empty(), townA);
        assertEquals(districtAId.asDistrict(), district);
        assertEquals(district, ElectionMapColor.districtOf(civic, nation, Optional.empty(), townA),
                "dieselbe Gemeinde liefert immer denselben Bezirksschlüssel");
        assertTrue(!district.equals(ElectionMapColor.districtOf(civic, nation, Optional.empty(), townB)),
                "unterschiedliche Bezirke liefern unterschiedliche Schlüssel");
    }

    @Test
    @DisplayName("Eine Gemeinde ohne eigenen Wahlkreis fällt auf sich selbst als Bezirk zurück")
    void districtOfFallsBackToTownWithoutConstituency() {
        assertEquals(townC, ElectionMapColor.districtOf(civic, nation, Optional.empty(), townC));
    }

    @Test
    @DisplayName("Farbe folgt dem Sieger im Bezirk: Partei, falls Mitglied, sonst der Akteur")
    void colorOfPrefersPartyThenActor() {
        ElectionResult result = result(Map.of(
                districtAId.asDistrict(), Map.of(candidateA, 100L),
                districtBId.asDistrict(), Map.of(candidateB, 50L)));
        PolityId party = new PolityId(UUID.randomUUID());
        PolityView partyView = partyView(party);

        Optional<String> withParty = ElectionMapColor.colorOf(civic, nation, Optional.empty(), townA,
                Optional.of(result), actor -> actor.equals(candidateA) ? Optional.of(partyView) : Optional.empty());
        assertEquals(Optional.of("p-" + party.value()), withParty);

        Optional<String> withoutParty = ElectionMapColor.colorOf(civic, nation, Optional.empty(), townB,
                Optional.of(result), actor -> Optional.empty());
        assertEquals(Optional.of("a-" + candidateB.uuid()), withoutParty);
    }

    @Test
    @DisplayName("Ohne Ergebnis oder ohne Stimmen im Bezirk bleibt eine feste, bezirksabhängige Farbe (Bezirksgrenzen schon sichtbar)")
    void colorOfFallsBackToDistrictKeyWithoutResult() {
        Optional<String> withoutResult = ElectionMapColor.colorOf(civic, nation, Optional.empty(), townA,
                Optional.empty(), actor -> Optional.empty());
        assertEquals(Optional.of("d-" + districtAId.asDistrict().value()), withoutResult);

        ElectionResult emptyDistrict = result(Map.of(districtAId.asDistrict(), Map.of()));
        Optional<String> noVotes = ElectionMapColor.colorOf(civic, nation, Optional.empty(), townA,
                Optional.of(emptyDistrict), actor -> Optional.empty());
        assertEquals(Optional.of("d-" + districtAId.asDistrict().value()), noVotes);
    }

    @Test
    @DisplayName("latestResult liefert das zuletzt geschlossene Ergebnis, optional nur zu einem bestimmten Amt")
    void latestResultPicksMostRecent() {
        ElectionResult older = new ElectionResult(ElectionId.generate(ids()), Office.MAYOR, new Day(1), List.of(), List.of());
        ElectionResult newer = new ElectionResult(ElectionId.generate(ids()), Office.MAYOR, new Day(5), List.of(), List.of());
        ElectionResult otherOffice = new ElectionResult(ElectionId.generate(ids()), Office.TREASURER, new Day(9), List.of(), List.of());
        PolityView polity = polityWithResults(List.of(older, newer, otherOffice));

        assertEquals(Optional.of(newer), ElectionMapColor.latestResult(polity, Office.MAYOR));
        assertEquals(Optional.of(otherOffice), ElectionMapColor.latestResult(polity));
    }

    private ElectionResult result(Map<PolityId, Map<ActorRef, Long>> votesByDistrict) {
        List<ElectionResult.District> districts = votesByDistrict.entrySet().stream()
                .map(entry -> new ElectionResult.District(Optional.of(entry.getKey()), entry.getValue()))
                .toList();
        return new ElectionResult(ElectionId.generate(ids()), Office.MAYOR, Day.ZERO, districts, List.of());
    }

    private de.etiennebader.civ.domain.shared.IdGenerator ids() {
        return new de.etiennebader.civ.domain.shared.SeededIdGenerator(7);
    }

    private PolityView partyView(PolityId id) {
        return new TestPolityView(id, List.of()).asView();
    }

    /** Minimaler {@link PolityView}-Stub nur für die hier gebrauchten Methoden ({@code id()}, {@code results()}). */
    private PolityView polityWithResults(List<ElectionResult> results) {
        return new TestPolityView(new PolityId(UUID.randomUUID()), results).asView();
    }

    private record TestPolityView(PolityId polityId, List<ElectionResult> electionResults) {

        PolityView asView() {
            return (PolityView) java.lang.reflect.Proxy.newProxyInstance(
                    PolityView.class.getClassLoader(), new Class<?>[]{PolityView.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "id" -> polityId;
                        case "results" -> electionResults;
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "TestPolityView[" + polityId + "]";
                        default -> throw new UnsupportedOperationException(
                                "Test-Stub deckt PolityView#" + method.getName() + " nicht ab");
                    });
        }
    }
}
