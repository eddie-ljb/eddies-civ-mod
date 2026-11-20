package de.etiennebader.civ.server.election;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.civic.CivicView;
import de.etiennebader.civ.domain.polity.ElectionResult;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Welche Farbe ein Gebiet auf der Wahlkarte zeigt (Ingame und Web, siehe #9): die Partei bzw. der Kandidat, die im
 * jeweiligen Wahlbezirk des letzten Ergebnisses vorn liegen. Ohne Ergebnis (oder ohne Partei) eine feste Kennung je
 * Bezirk, damit Bezirksgrenzen schon vor der Auszählung sichtbar sind. Nutzt dieselbe Bezirkszuordnung wie die
 * Auszählung selbst ({@link CivicView#constituencyFor}), damit Karte und Ergebnis nie auseinanderlaufen.
 */
public final class ElectionMapColor {

    private ElectionMapColor() {
    }

    /** Das letzte Ergebnis der Polity zu {@code office}; leer ohne Ergebnis dazu. */
    public static Optional<ElectionResult> latestResult(PolityView electionPolity, Office office) {
        Objects.requireNonNull(electionPolity, "electionPolity");
        Objects.requireNonNull(office, "office");
        return electionPolity.results().stream()
                .filter(result -> result.office() == office)
                .max(Comparator.comparingLong(result -> result.closedOn().value()));
    }

    /** Das jeweils letzte Ergebnis der Polity, gleich zu welchem Amt; leer ohne jedes Ergebnis. */
    public static Optional<ElectionResult> latestResult(PolityView electionPolity) {
        Objects.requireNonNull(electionPolity, "electionPolity");
        return electionPolity.results().stream()
                .max(Comparator.comparingLong(result -> result.closedOn().value()));
    }

    /**
     * Der Wahlbezirk eines Gebiets: sein eigener Wahlkreis bei {@code electionPolity}, falls einer dort liegt, sonst
     * die Gemeinde, der es gehört. Dieselbe Zuordnung wie bei der Auszählung ({@code RunElections#districtOf}), damit
     * Karte und Ergebnis zusammenpassen.
     *
     * @param civic          Wahlkreise der Welt
     * @param electionPolity die Polity, deren Wahl gezeigt wird (Gemeinde oder Bund)
     * @param representative repräsentative Blockposition des Gebiets (z. B. Chunk-Mitte)
     * @param ownerTown      die Gemeinde, der das Gebiet gehört
     */
    public static PolityId districtOf(CivicView civic, PolityId electionPolity, Optional<BlockPosition> representative,
                                      PolityId ownerTown) {
        Objects.requireNonNull(civic, "civic");
        Objects.requireNonNull(electionPolity, "electionPolity");
        Objects.requireNonNull(representative, "representative");
        Objects.requireNonNull(ownerTown, "ownerTown");
        return civic.constituencyFor(electionPolity, representative, Optional.of(ownerTown))
                .map(constituency -> constituency.id().asDistrict())
                .orElse(ownerTown);
    }

    /**
     * Wie oben, aber direkt die Kartenfarbe: die Partei bzw. der Kandidat, die im Bezirk des Gebiets vorn liegen,
     * sonst eine feste, ergebnisunabhängige Kennung je Bezirk (damit Bezirksgrenzen schon vor der Auszählung
     * sichtbar sind).
     *
     * @param result  letztes Ergebnis, falls vorhanden
     * @param partyOf Partei eines Akteurs, falls Mitglied
     * @return Farbschlüssel: {@code p-<Partei>} bzw. {@code a-<Akteur>} des Siegers im Bezirk, sonst {@code d-<Bezirk>}
     */
    public static Optional<String> colorOf(CivicView civic, PolityId electionPolity, Optional<BlockPosition> representative,
                                           PolityId ownerTown, Optional<ElectionResult> result,
                                           Function<ActorRef, Optional<PolityView>> partyOf) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(partyOf, "partyOf");
        PolityId district = districtOf(civic, electionPolity, representative, ownerTown);
        Optional<String> leaderKey = result.flatMap(found -> found.districts().stream()
                        .filter(entry -> entry.district().equals(Optional.of(district)))
                        .findFirst())
                .flatMap(ElectionResult.District::leader)
                .map(leader -> partyOf.apply(leader).map(party -> "p-" + party.id().value())
                        .orElseGet(() -> "a-" + leader.uuid()));
        return leaderKey.isPresent() ? leaderKey : Optional.of("d-" + district.value());
    }
}
