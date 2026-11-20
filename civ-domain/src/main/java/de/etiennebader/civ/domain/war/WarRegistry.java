package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Register aller Kriege, Garnisonen und erkaufter Neutralität (#27, erste Ausbaustufe).
 *
 * <p>Bewusst nicht an {@code Aggregates}/{@code Repositories} angebunden und damit nicht über einen Neustart
 * hinweg persistiert — wie {@code de.etiennebader.civ.domain.conjuncture.BusinessCycle} (#6) ein eigenständiges
 * Fundament für eine spätere Ausbaustufe; siehe "bewusst nicht umgesetzte Teile" in {@code docs/issue-27-krieg.md}.
 */
public final class WarRegistry extends AggregateRoot<WarEvent> implements WarView {

    private final List<War> wars = new ArrayList<>();
    private final Map<PolityId, Map<ActorRef, MilitaryRank>> garrisons = new LinkedHashMap<>();
    private final Map<PolityId, Day> neutralUntil = new LinkedHashMap<>();

    public static WarRegistry empty() {
        return new WarRegistry();
    }

    @Override
    public List<War> wars() {
        return List.copyOf(wars);
    }

    @Override
    public Map<PolityId, Map<ActorRef, MilitaryRank>> garrisons() {
        return garrisons.entrySet().stream()
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), Map.copyOf(entry.getValue())),
                        Map::putAll);
    }

    @Override
    public Map<PolityId, Day> neutralUntil() {
        return Map.copyOf(neutralUntil);
    }

    /**
     * Erklärt {@code defender} den Krieg.
     *
     * @throws WarAlreadyOngoingException  wenn zwischen beiden schon ein Krieg läuft
     * @throws DefenderIsNeutralException  wenn sich der Verteidiger an diesem Tag noch Neutralität erkauft hat
     */
    public War declareWar(IdGenerator ids, PolityId aggressor, PolityId defender, Day day) {
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(aggressor, "aggressor");
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(day, "day");
        if (ongoingWarBetween(aggressor, defender).isPresent()) {
            throw new WarAlreadyOngoingException(aggressor, defender);
        }
        if (isNeutral(defender, day)) {
            throw new DefenderIsNeutralException(defender);
        }
        War war = War.declared(WarId.generate(ids), aggressor, defender, day);
        wars.add(war);
        raise(new WarEvent.WarDeclared(war.id(), aggressor, defender, day));
        return war;
    }

    /**
     * Nimmt {@code member} in die Garnison von {@code polity} auf.
     *
     * @throws AlreadyEnlistedException wenn das Mitglied schon in dieser Garnison dient
     */
    public void enlist(PolityId polity, ActorRef member, MilitaryRank rank, Day day) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(day, "day");
        Map<ActorRef, MilitaryRank> garrison = garrisons.computeIfAbsent(polity, key -> new LinkedHashMap<>());
        if (garrison.containsKey(member)) {
            throw new AlreadyEnlistedException(polity, member);
        }
        garrison.put(member, rank);
        raise(new WarEvent.Enlisted(polity, member, rank, day));
    }

    /**
     * Entlässt {@code member} aus der Garnison von {@code polity}.
     *
     * @throws NotEnlistedException wenn das Mitglied dort gar nicht dient
     */
    public void discharge(PolityId polity, ActorRef member, Day day) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(day, "day");
        Map<ActorRef, MilitaryRank> garrison = garrisons.get(polity);
        if (garrison == null || garrison.remove(member) == null) {
            throw new NotEnlistedException(polity, member);
        }
        raise(new WarEvent.Discharged(polity, member, day));
    }

    /**
     * Löst das nächste Gefecht des Kriegs {@code id} anhand der aktuellen Garnisonen auf ({@link BattleResolver}).
     * Bei einem Unentschieden geht der Krieg weiter (kein Ende, kein Sieger); sonst endet er sofort mit
     * {@link PeaceTerms} zugunsten der siegreichen Seite — bewusst vereinfacht auf ein einziges entscheidendes
     * Gefecht statt eines mehrteiligen Feldzugs, siehe Klassenkommentar.
     *
     * @param reparationsPerStrengthPoint Reparationen je Punkt Gefechtsstärke-Unterschied
     *                                    ({@link de.etiennebader.civ.domain.law.StandardParameters#WAR_REPARATIONS_PER_STRENGTH_POINT}),
     *                                    in der kleinsten Währungseinheit des Verlierers
     * @throws UnknownWarException      wenn es keinen Krieg mit dieser ID gibt
     * @throws WarAlreadyEndedException wenn dieser Krieg schon beendet ist
     */
    public BattleOutcome resolveBattle(WarId id, long reparationsPerStrengthPoint, Day day) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(day, "day");
        if (reparationsPerStrengthPoint < 0) {
            throw new IllegalArgumentException("Reparationen je Stärkepunkt dürfen nicht negativ sein");
        }
        War war = war(id).orElseThrow(() -> new UnknownWarException(id));
        if (!war.ongoing()) {
            throw new WarAlreadyEndedException(id);
        }
        double aggressorStrength = BattleResolver.strength((int) soldiersOf(war.aggressor()), (int) officersOf(war.aggressor()));
        double defenderStrength = BattleResolver.strength((int) soldiersOf(war.defender()), (int) officersOf(war.defender()))
                * BattleResolver.DEFENDER_ADVANTAGE;
        BattleOutcome outcome = BattleResolver.resolve((int) soldiersOf(war.aggressor()), (int) officersOf(war.aggressor()),
                (int) soldiersOf(war.defender()), (int) officersOf(war.defender()));
        Optional<PolityId> winner = switch (outcome) {
            case AGGRESSOR_WINS -> Optional.of(war.aggressor());
            case DEFENDER_WINS -> Optional.of(war.defender());
            case DRAW -> Optional.empty();
        };
        raise(new WarEvent.BattleResolved(id, winner, day));
        if (winner.isEmpty()) {
            return outcome;
        }
        PolityId loser = war.opponentOf(winner.get());
        long reparations = Math.round(Math.abs(aggressorStrength - defenderStrength) * reparationsPerStrengthPoint);
        PeaceTerms terms = new PeaceTerms(winner.get(), loser, reparations);
        endWar(war, day, Optional.of(terms));
        return outcome;
    }

    /** Beendet den Krieg {@code id} ohne Sieger (Verhandlungsfrieden, "weißer Frieden"). */
    public void negotiatePeace(WarId id, Day day) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(day, "day");
        War war = war(id).orElseThrow(() -> new UnknownWarException(id));
        if (!war.ongoing()) {
            throw new WarAlreadyEndedException(id);
        }
        endWar(war, day, Optional.empty());
    }

    private void endWar(War war, Day day, Optional<PeaceTerms> outcome) {
        wars.replaceAll(existing -> existing.id().equals(war.id()) ? existing.ended(day, outcome) : existing);
        raise(new WarEvent.WarEnded(war.id(), day));
    }

    /**
     * Erkauft {@code polity} Neutralität bis einschließlich {@code until} (Gebühr ist Sache des Aufrufers).
     *
     * @throws AtWarException wenn die Polity gerade in einem laufenden Krieg steht
     */
    public void purchaseNeutrality(PolityId polity, Day until, Day day) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(day, "day");
        if (until.isBefore(day)) {
            throw new IllegalArgumentException("Neutralität kann nicht rückwirkend erkauft werden");
        }
        if (!ongoingWarsOf(polity).isEmpty()) {
            throw new AtWarException(polity);
        }
        neutralUntil.put(polity, until);
        raise(new WarEvent.NeutralityPurchased(polity, until, day));
    }
}
