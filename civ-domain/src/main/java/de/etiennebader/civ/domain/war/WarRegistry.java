package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Register aller Kriege, Garnisonen, Fronten, Feldzüge, Verwundeter, Bündnisse und Ansprüche (#27).
 *
 * <p>Ein Krieg hat einen Frontstand von -100 bis 100. Gefechte ({@link #fight}, geplante Feldzüge oder die
 * Entscheidungsschlacht {@link #resolveBattle}) verschieben ihn; erreicht er eine Grenze, ist der Krieg entschieden.
 * Dabei werden Soldaten verwundet und fallen aus, im Lazarett kürzer. Alle Rechnungen sind ganzzahlig und
 * deterministisch.
 */
public final class WarRegistry extends AggregateRoot<WarEvent> implements WarView {

    public static final int MAX_TRAINING = 5;
    public static final int MAX_EQUIPMENT = 3;
    public static final int HOSPITAL_RECOVERY_DAYS = 2;
    public static final int FIELD_RECOVERY_DAYS = 6;
    /** Anteil der Verwundeten beim unterlegenen bzw. siegreichen Teil, in Prozent. */
    static final int LOSER_CASUALTY_PERCENT = 30;
    static final int WINNER_CASUALTY_PERCENT = 10;
    /** Höchste Frontverschiebung durch ein einzelnes Gefecht. */
    static final int MAX_SHIFT = 50;

    /** Gesamter Zustand zum Speichern und Wiederherstellen. */
    public record Snapshot(List<War> wars, Map<PolityId, Map<ActorRef, Service>> services,
                           Map<PolityId, Map<ActorRef, Day>> wounded, Map<PolityId, Integer> hospitalBeds,
                           Map<WarId, Integer> fronts, List<Campaign> campaigns,
                           Map<PolityId, Set<PolityId>> alliances, Map<PolityId, Set<PolityId>> proposals,
                           Map<PolityId, Set<PolityId>> claims, Map<PolityId, Day> neutralUntil) {
    }

    private final List<War> wars = new ArrayList<>();
    private final Map<PolityId, Map<ActorRef, Service>> services = new LinkedHashMap<>();
    private final Map<PolityId, Map<ActorRef, Day>> wounded = new LinkedHashMap<>();
    private final Map<PolityId, Integer> beds = new LinkedHashMap<>();
    private final Map<WarId, Integer> fronts = new LinkedHashMap<>();
    private final List<Campaign> campaigns = new ArrayList<>();
    private final Map<PolityId, Set<PolityId>> allies = new LinkedHashMap<>();
    private final Map<PolityId, Set<PolityId>> proposals = new LinkedHashMap<>();
    private final Map<PolityId, Set<PolityId>> claims = new LinkedHashMap<>();
    private final Map<PolityId, Day> neutralUntil = new LinkedHashMap<>();

    public static WarRegistry empty() {
        return new WarRegistry();
    }

    public static WarRegistry restore(Snapshot snapshot) {
        WarRegistry registry = new WarRegistry();
        registry.wars.addAll(snapshot.wars());
        snapshot.services().forEach((polity, map) -> registry.services.put(polity, new LinkedHashMap<>(map)));
        snapshot.wounded().forEach((polity, map) -> registry.wounded.put(polity, new LinkedHashMap<>(map)));
        registry.beds.putAll(snapshot.hospitalBeds());
        registry.fronts.putAll(snapshot.fronts());
        registry.campaigns.addAll(snapshot.campaigns());
        snapshot.alliances().forEach((polity, set) -> registry.allies.put(polity, new LinkedHashSet<>(set)));
        snapshot.proposals().forEach((polity, set) -> registry.proposals.put(polity, new LinkedHashSet<>(set)));
        snapshot.claims().forEach((polity, set) -> registry.claims.put(polity, new LinkedHashSet<>(set)));
        registry.neutralUntil.putAll(snapshot.neutralUntil());
        return registry;
    }

    public Snapshot snapshot() {
        return new Snapshot(wars(), services(), wounded(), hospitalBeds(), fronts(), campaigns(), alliances(),
                copy(proposals), claims(), neutralUntil());
    }

    // ------------------------------------------------------------------ Lesen

    @Override
    public List<War> wars() {
        return List.copyOf(wars);
    }

    @Override
    public Map<PolityId, Map<ActorRef, MilitaryRank>> garrisons() {
        Map<PolityId, Map<ActorRef, MilitaryRank>> result = new LinkedHashMap<>();
        services.forEach((polity, map) -> {
            Map<ActorRef, MilitaryRank> ranks = new LinkedHashMap<>();
            map.forEach((member, service) -> ranks.put(member, service.rank()));
            result.put(polity, ranks);
        });
        return result;
    }

    @Override
    public Map<PolityId, Map<ActorRef, Service>> services() {
        Map<PolityId, Map<ActorRef, Service>> result = new LinkedHashMap<>();
        services.forEach((polity, map) -> result.put(polity, new LinkedHashMap<>(map)));
        return result;
    }

    @Override
    public Map<PolityId, Map<ActorRef, Day>> wounded() {
        Map<PolityId, Map<ActorRef, Day>> result = new LinkedHashMap<>();
        wounded.forEach((polity, map) -> result.put(polity, new LinkedHashMap<>(map)));
        return result;
    }

    @Override
    public Map<PolityId, Integer> hospitalBeds() {
        return Map.copyOf(beds);
    }

    @Override
    public Map<WarId, Integer> fronts() {
        return Map.copyOf(fronts);
    }

    @Override
    public List<Campaign> campaigns() {
        return List.copyOf(campaigns);
    }

    @Override
    public Map<PolityId, Set<PolityId>> alliances() {
        return copy(allies);
    }

    @Override
    public Map<PolityId, Set<PolityId>> claims() {
        return copy(claims);
    }

    @Override
    public Map<PolityId, Day> neutralUntil() {
        return Map.copyOf(neutralUntil);
    }

    private static Map<PolityId, Set<PolityId>> copy(Map<PolityId, Set<PolityId>> source) {
        Map<PolityId, Set<PolityId>> result = new LinkedHashMap<>();
        source.forEach((polity, set) -> result.put(polity, new LinkedHashSet<>(set)));
        return result;
    }

    /** Ausstehende Bündnisanträge (Antragsteller -> Empfänger). */
    public Map<PolityId, Set<PolityId>> proposals() {
        return copy(proposals);
    }

    /** Frontstand eines Kriegs; 0, wenn noch nichts passiert ist. */
    public int frontOf(WarId id) {
        return fronts.getOrDefault(id, 0);
    }

    // ------------------------------------------------------------------ Krieg

    /**
     * Erklärt {@code defender} den Krieg.
     *
     * @throws WarAlreadyOngoingException  wenn zwischen beiden schon ein Krieg läuft
     * @throws DefenderIsNeutralException  wenn sich der Verteidiger an diesem Tag noch Neutralität erkauft hat
     * @throws WarRuleException            wenn beide verbündet sind
     */
    public War declareWar(IdGenerator ids, PolityId aggressor, PolityId defender, Day day) {
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(aggressor, "aggressor");
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(day, "day");
        if (ongoingWarBetween(aggressor, defender).isPresent()) {
            throw new WarAlreadyOngoingException(aggressor, defender);
        }
        if (alliesOf(aggressor).contains(defender)) {
            throw new WarRuleException(ErrorCode.ALLIED_POLITIES, aggressor, defender);
        }
        if (isNeutral(defender, day)) {
            throw new DefenderIsNeutralException(defender);
        }
        War war = War.declared(WarId.generate(ids), aggressor, defender, day);
        wars.add(war);
        fronts.put(war.id(), 0);
        raise(new WarEvent.WarDeclared(war.id(), aggressor, defender, day));
        return war;
    }

    /** Beendet den Krieg {@code id} ohne Sieger (Verhandlungsfrieden, "weißer Frieden"). */
    public void negotiatePeace(WarId id, Day day) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(day, "day");
        endWar(ongoing(id), day, Optional.empty());
    }

    private War ongoing(WarId id) {
        War war = war(id).orElseThrow(() -> new UnknownWarException(id));
        if (!war.ongoing()) {
            throw new WarAlreadyEndedException(id);
        }
        return war;
    }

    private void endWar(War war, Day day, Optional<PeaceTerms> outcome) {
        wars.replaceAll(existing -> existing.id().equals(war.id()) ? existing.ended(day, outcome) : existing);
        campaigns.removeIf(campaign -> campaign.war().equals(war.id()));
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

    // ------------------------------------------------------------------ Garnison

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
        Map<ActorRef, Service> garrison = services.computeIfAbsent(polity, key -> new LinkedHashMap<>());
        if (garrison.containsKey(member)) {
            throw new AlreadyEnlistedException(polity, member);
        }
        garrison.put(member, new Service(rank, 0, 0));
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
        Map<ActorRef, Service> garrison = services.get(polity);
        if (garrison == null || garrison.remove(member) == null) {
            throw new NotEnlistedException(polity, member);
        }
        Map<ActorRef, Day> hurt = wounded.get(polity);
        if (hurt != null) {
            hurt.remove(member);
        }
        raise(new WarEvent.Discharged(polity, member, day));
    }

    /** Wie viele Mitglieder noch nicht voll ausgebildet sind. */
    public int trainable(PolityId polity) {
        return (int) services.getOrDefault(polity, Map.of()).values().stream()
                .filter(service -> service.training() < MAX_TRAINING).count();
    }

    /** Wie viele Mitglieder noch nicht voll ausgerüstet sind. */
    public int equippable(PolityId polity) {
        return (int) services.getOrDefault(polity, Map.of()).values().stream()
                .filter(service -> service.equipment() < MAX_EQUIPMENT).count();
    }

    /**
     * Bildet die ganze Garnison eine Stufe weiter aus.
     *
     * @return Zahl der ausgebildeten Mitglieder
     * @throws WarRuleException wenn niemand mehr zu verbessern ist
     */
    public int train(PolityId polity, Day day) {
        return improve(polity, day, true);
    }

    /** Rüstet die ganze Garnison eine Stufe besser aus. */
    public int equip(PolityId polity, Day day) {
        return improve(polity, day, false);
    }

    private int improve(PolityId polity, Day day, boolean training) {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(day, "day");
        Map<ActorRef, Service> garrison = services.getOrDefault(polity, Map.of());
        int improved = 0;
        for (Map.Entry<ActorRef, Service> entry : garrison.entrySet()) {
            Service service = entry.getValue();
            if (training && service.training() < MAX_TRAINING) {
                entry.setValue(new Service(service.rank(), service.training() + 1, service.equipment()));
                improved++;
            } else if (!training && service.equipment() < MAX_EQUIPMENT) {
                entry.setValue(new Service(service.rank(), service.training(), service.equipment() + 1));
                improved++;
            }
        }
        if (improved == 0) {
            throw new WarRuleException(ErrorCode.NOTHING_TO_IMPROVE);
        }
        raise(new WarEvent.TroopsImproved(polity, training ? improved : 0, training ? 0 : improved, day));
        return improved;
    }

    /** Baut {@code count} Betten im Lazarett von {@code polity}. */
    public void buildHospitalBeds(PolityId polity, int count) {
        Objects.requireNonNull(polity, "polity");
        if (count < 1) {
            throw new IllegalArgumentException("Es muss mindestens ein Bett gebaut werden");
        }
        beds.merge(polity, count, Integer::sum);
    }

    /** Lässt Verwundete genesen, deren Ausfallzeit am {@code day} vorbei ist. */
    public void heal(Day day) {
        Objects.requireNonNull(day, "day");
        wounded.values().forEach(map -> map.values().removeIf(until -> !day.isBefore(until)));
    }

    // ------------------------------------------------------------------ Gefechte

    /**
     * Plant einen Feldzug: {@code initiator} will im Krieg {@code id} am Tag {@code on} kämpfen. Pro Krieg und Seite
     * gibt es einen Plan; ein neuer ersetzt den alten.
     *
     * @throws WarRuleException wenn der Zeitpunkt in der Vergangenheit liegt, die Art nicht möglich ist
     *                          (Verteidigung nur für den Verteidiger, Hinterhalt nur mit Ausspähern) oder die Polity
     *                          nicht beteiligt ist
     */
    public Campaign planCampaign(WarId id, PolityId initiator, BattleKind kind, Day on, Day today) {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(today, "today");
        War war = ongoing(id);
        if (!war.involves(initiator) || on.isBefore(today)
                || (kind == BattleKind.DEFENSIVE && !war.defender().equals(initiator))
                || (kind == BattleKind.AMBUSH && healthyScouts(initiator, today) == 0)) {
            throw new WarRuleException(ErrorCode.INVALID_CAMPAIGN);
        }
        campaigns.removeIf(campaign -> campaign.war().equals(id) && campaign.initiator().equals(initiator));
        Campaign campaign = new Campaign(id, initiator, kind, on);
        campaigns.add(campaign);
        raise(new WarEvent.CampaignPlanned(id, initiator, kind, on, today));
        return campaign;
    }

    /** Führt alle am {@code day} oder früher fälligen Feldzüge aus; die Garnison heilt zuvor. */
    public List<BattleReport> runDueCampaigns(Day day, long reparationsPerStrengthPoint, int conquestPerWin) {
        heal(day);
        List<BattleReport> reports = new ArrayList<>();
        for (Campaign campaign : List.copyOf(campaigns)) {
            if (campaign.on().isBefore(day) || campaign.on().equals(day)) {
                campaigns.remove(campaign);
                if (war(campaign.war()).filter(War::ongoing).isPresent()) {
                    reports.add(fight(campaign.war(), campaign.initiator(), campaign.kind(),
                            reparationsPerStrengthPoint, conquestPerWin, day));
                }
            }
        }
        return reports;
    }

    /**
     * Trägt ein Gefecht aus: Die Front verschiebt sich, beide Seiten haben Verwundete. Erreicht die Front eine
     * Grenze, endet der Krieg mit Friedensbedingungen zugunsten der siegreichen Seite.
     */
    public BattleReport fight(WarId id, PolityId initiator, BattleKind kind, long reparationsPerStrengthPoint,
                              int conquestPerWin, Day day) {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(day, "day");
        if (reparationsPerStrengthPoint < 0 || conquestPerWin < 0) {
            throw new IllegalArgumentException("Reparationen und Eroberung dürfen nicht negativ sein");
        }
        War war = ongoing(id);
        if (!war.involves(initiator) || (kind == BattleKind.DEFENSIVE && !war.defender().equals(initiator))) {
            throw new WarRuleException(ErrorCode.INVALID_CAMPAIGN);
        }
        long aggressorPower = sidePower(war, war.aggressor(), day);
        long defenderPower = sidePower(war, war.defender(), day);
        int bonus = switch (kind) {
            case ASSAULT -> 100;
            case AMBUSH -> healthyScouts(initiator, day) > 0
                    && healthyScouts(war.opponentOf(initiator), day) == 0 ? 125 : 100;
            case DEFENSIVE -> 115;
        };
        if (initiator.equals(war.aggressor())) {
            aggressorPower = aggressorPower * bonus / 100;
        } else {
            defenderPower = defenderPower * bonus / 100;
        }
        long homeDefender = defenderPower * BattleResolver.DEFENDER_ADVANTAGE_PERCENT / 100;
        long larger = Math.max(Math.max(aggressorPower, homeDefender), 1);
        int shift = (int) Math.max(-MAX_SHIFT, Math.min(MAX_SHIFT, (aggressorPower - homeDefender) * MAX_SHIFT / larger));
        int front = Math.max(-100, Math.min(100, frontOf(id) + shift));
        fronts.put(id, front);
        int woundedAggressor = casualties(war.aggressor(), shift < 0, shift == 0, day);
        int woundedDefender = casualties(war.defender(), shift > 0, shift == 0, day);
        raise(new WarEvent.BattleFought(id, front, day));
        Optional<PeaceTerms> terms = Optional.empty();
        if (Math.abs(front) == 100) {
            PolityId winner = front > 0 ? war.aggressor() : war.defender();
            long reparations = Math.round(Math.abs(aggressorPower - homeDefender) / 100.0 * reparationsPerStrengthPoint);
            terms = Optional.of(new PeaceTerms(winner, war.opponentOf(winner), reparations, conquestPerWin));
            endWar(war, day, terms);
        }
        return new BattleReport(id, kind, aggressorPower, defenderPower, shift, front, woundedAggressor,
                woundedDefender, terms, day);
    }

    /**
     * Löst die Entscheidungsschlacht des Kriegs {@code id} anhand der aktuellen Kampfkräfte auf. Bei Gleichstand geht
     * der Krieg weiter; sonst endet er sofort mit {@link PeaceTerms} zugunsten der siegreichen Seite.
     */
    public BattleOutcome resolveBattle(WarId id, long reparationsPerStrengthPoint, Day day) {
        return resolveBattle(id, reparationsPerStrengthPoint, 0, day);
    }

    /** Wie {@link #resolveBattle(WarId, long, Day)}, mit {@code conquest} geforderten Grenz-Chunks im Sieger-Fall. */
    public BattleOutcome resolveBattle(WarId id, long reparationsPerStrengthPoint, int conquest, Day day) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(day, "day");
        if (reparationsPerStrengthPoint < 0 || conquest < 0) {
            throw new IllegalArgumentException("Reparationen und Eroberung dürfen nicht negativ sein");
        }
        War war = ongoing(id);
        long aggressorPower = sidePower(war, war.aggressor(), day);
        long defenderPower = sidePower(war, war.defender(), day);
        BattleOutcome outcome = BattleResolver.resolvePower(aggressorPower, defenderPower);
        Optional<PolityId> winner = switch (outcome) {
            case AGGRESSOR_WINS -> Optional.of(war.aggressor());
            case DEFENDER_WINS -> Optional.of(war.defender());
            case DRAW -> Optional.empty();
        };
        raise(new WarEvent.BattleResolved(id, winner, day));
        casualties(war.aggressor(), outcome == BattleOutcome.DEFENDER_WINS, outcome == BattleOutcome.DRAW, day);
        casualties(war.defender(), outcome == BattleOutcome.AGGRESSOR_WINS, outcome == BattleOutcome.DRAW, day);
        if (winner.isEmpty()) {
            return outcome;
        }
        fronts.put(id, winner.get().equals(war.aggressor()) ? 100 : -100);
        long homeDefender = defenderPower * BattleResolver.DEFENDER_ADVANTAGE_PERCENT / 100;
        long reparations = Math.round(Math.abs(aggressorPower - homeDefender) / 100.0 * reparationsPerStrengthPoint);
        endWar(war, day, Optional.of(new PeaceTerms(winner.get(), war.opponentOf(winner.get()), reparations, conquest)));
        return outcome;
    }

    /** Verwundet einen Teil der dienstfähigen Mitglieder; Soldaten zuerst, Offiziere zuletzt. */
    private int casualties(PolityId polity, boolean lost, boolean draw, Day day) {
        List<Map.Entry<ActorRef, Service>> healthy = services.getOrDefault(polity, Map.of()).entrySet().stream()
                .filter(entry -> !isWounded(polity, entry.getKey(), day))
                .sorted(Comparator.comparingInt(entry -> entry.getValue().rank().ordinal() == 1 ? 2
                        : entry.getValue().rank() == MilitaryRank.SCOUT ? 1 : 0))
                .toList();
        int percent = lost && !draw ? LOSER_CASUALTY_PERCENT : WINNER_CASUALTY_PERCENT;
        int count = lost && !draw ? (healthy.size() * percent + 99) / 100 : healthy.size() * percent / 100;
        int hurtNow = wounded.getOrDefault(polity, Map.of()).size();
        int free = beds.getOrDefault(polity, 0) - hurtNow;
        for (int i = 0; i < count; i++) {
            ActorRef member = healthy.get(i).getKey();
            int recovery = i < free ? HOSPITAL_RECOVERY_DAYS : FIELD_RECOVERY_DAYS;
            Day until = day.plus(recovery);
            wounded.computeIfAbsent(polity, key -> new LinkedHashMap<>()).put(member, until);
            raise(new WarEvent.Wounded(polity, member, until, day));
        }
        return count;
    }

    // ------------------------------------------------------------------ Bündnisse und Ansprüche

    /**
     * Schlägt {@code other} ein Bündnis vor. Hat {@code other} bereits eines vorgeschlagen, kommt es sofort zustande.
     *
     * @return ob das Bündnis damit geschlossen ist
     * @throws WarRuleException bei sich selbst, bestehendem Bündnis oder laufendem Krieg zwischen beiden
     */
    public boolean proposeAlliance(PolityId proposer, PolityId other, Day day) {
        Objects.requireNonNull(proposer, "proposer");
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(day, "day");
        if (proposer.equals(other) || alliesOf(proposer).contains(other)
                || ongoingWarBetween(proposer, other).isPresent()) {
            throw new WarRuleException(ErrorCode.INVALID_ALLIANCE);
        }
        if (proposals.getOrDefault(other, Set.of()).contains(proposer)) {
            return acceptAlliance(proposer, other, day);
        }
        proposals.computeIfAbsent(proposer, key -> new LinkedHashSet<>()).add(other);
        return false;
    }

    /** {@code accepter} nimmt den Bündnisantrag von {@code proposer} an. */
    public boolean acceptAlliance(PolityId accepter, PolityId proposer, Day day) {
        Set<PolityId> offered = proposals.get(proposer);
        if (offered == null || !offered.remove(accepter) || ongoingWarBetween(accepter, proposer).isPresent()) {
            throw new WarRuleException(ErrorCode.INVALID_ALLIANCE);
        }
        allies.computeIfAbsent(accepter, key -> new LinkedHashSet<>()).add(proposer);
        allies.computeIfAbsent(proposer, key -> new LinkedHashSet<>()).add(accepter);
        raise(new WarEvent.AllianceFormed(proposer, accepter, day));
        return true;
    }

    /** Kündigt das Bündnis zwischen beiden. */
    public void breakAlliance(PolityId first, PolityId second, Day day) {
        if (!alliesOf(first).contains(second)) {
            throw new WarRuleException(ErrorCode.INVALID_ALLIANCE);
        }
        allies.get(first).remove(second);
        allies.get(second).remove(first);
        raise(new WarEvent.AllianceBroken(first, second, day));
    }

    /** {@code claimant} erhebt Anspruch auf {@code target} (Grenzstreit, Rückforderung ...). */
    public void lodgeClaim(PolityId claimant, PolityId target, Day day) {
        Objects.requireNonNull(claimant, "claimant");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(day, "day");
        if (claimant.equals(target) || hasClaim(claimant, target) || alliesOf(claimant).contains(target)) {
            throw new WarRuleException(ErrorCode.INVALID_CLAIM);
        }
        claims.computeIfAbsent(claimant, key -> new LinkedHashSet<>()).add(target);
        raise(new WarEvent.ClaimLodged(claimant, target, day));
    }
}
