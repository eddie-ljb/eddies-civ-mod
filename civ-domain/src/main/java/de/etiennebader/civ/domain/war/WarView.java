package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lesender Zugriff auf Kriege, Garnisonen und Neutralität. */
public interface WarView {

    /** Alle Kriege, in Reihenfolge ihrer Erklärung. */
    List<War> wars();

    default Optional<War> war(WarId id) {
        return wars().stream().filter(war -> war.id().equals(id)).findFirst();
    }

    /** Alle laufenden Kriege, an denen die Polity beteiligt ist. */
    default List<War> ongoingWarsOf(PolityId polity) {
        return wars().stream().filter(War::ongoing).filter(war -> war.involves(polity)).toList();
    }

    /** Der laufende Krieg zwischen diesen beiden Polities, falls einer besteht (reihenfolgenunabhängig). */
    default Optional<War> ongoingWarBetween(PolityId first, PolityId second) {
        return wars().stream().filter(War::ongoing)
                .filter(war -> war.involves(first) && war.involves(second))
                .findFirst();
    }

    /** Garnison einer Polity: Rang je Mitglied. */
    Map<PolityId, Map<ActorRef, MilitaryRank>> garrisons();

    /** Dienstverhältnisse (Rang, Ausbildung, Ausrüstung) je Polity und Mitglied. */
    Map<PolityId, Map<ActorRef, Service>> services();

    /** Verwundete je Polity: Mitglied und der erste Tag, an dem es wieder dienstfähig ist. */
    Map<PolityId, Map<ActorRef, Day>> wounded();

    /** Betten im Lazarett je Polity. */
    Map<PolityId, Integer> hospitalBeds();

    /** Frontstand je Krieg: -100 (Verteidiger gewinnt) bis 100 (Angreifer gewinnt). */
    Map<WarId, Integer> fronts();

    /** Geplante Feldzüge. */
    List<Campaign> campaigns();

    /** Bündnisse: Verbündete je Polity (symmetrisch). */
    Map<PolityId, Set<PolityId>> alliances();

    /** Ansprüche: Polity -> Polities, auf die sie Anspruch erhebt. */
    Map<PolityId, Set<PolityId>> claims();

    /** Dienstverhältnis eines Mitglieds in Rang, Ausbildung und Ausrüstung. */
    record Service(MilitaryRank rank, int training, int equipment) {

        public Service {
            java.util.Objects.requireNonNull(rank, "rank");
            if (training < 0 || equipment < 0) {
                throw new IllegalArgumentException("Ausbildung und Ausrüstung dürfen nicht negativ sein");
            }
        }

        /** Kampfkraft in Hundertsteln eines Soldaten: Grundkraft, je Stufe Ausbildung und Ausrüstung +10 %. */
        public long power() {
            return (long) rank.power() * (100 + 10L * training + 10L * equipment) / 100;
        }
    }

    default boolean isWounded(PolityId polity, ActorRef member, Day day) {
        Day until = wounded().getOrDefault(polity, Map.of()).get(member);
        return until != null && day.isBefore(until);
    }

    /** Kampfkraft der dienstfähigen Mitglieder einer Polity, in Hundertsteln eines Soldaten. */
    default long power(PolityId polity, Day day) {
        return services().getOrDefault(polity, Map.of()).entrySet().stream()
                .filter(entry -> !isWounded(polity, entry.getKey(), day))
                .mapToLong(entry -> entry.getValue().power()).sum();
    }

    default int healthyScouts(PolityId polity, Day day) {
        return (int) services().getOrDefault(polity, Map.of()).entrySet().stream()
                .filter(entry -> entry.getValue().rank() == MilitaryRank.SCOUT)
                .filter(entry -> !isWounded(polity, entry.getKey(), day)).count();
    }

    default Set<PolityId> alliesOf(PolityId polity) {
        return alliances().getOrDefault(polity, Set.of());
    }

    /**
     * Kampfkraft einer Kriegsseite: die eigene plus die halbe Kraft der Verbündeten, sofern diese nicht auch mit dem
     * Gegner verbündet sind.
     */
    default long sidePower(War war, PolityId side, Day day) {
        PolityId opponent = war.opponentOf(side);
        long total = power(side, day);
        for (PolityId ally : alliesOf(side)) {
            if (!ally.equals(opponent) && !alliesOf(opponent).contains(ally)) {
                total += power(ally, day) / 2;
            }
        }
        return total;
    }

    /** Was {@code viewer} über die Truppe von {@code target} weiß. */
    default Intel intel(PolityId viewer, PolityId target, Day day) {
        int members = services().getOrDefault(target, Map.of()).size();
        if (healthyScouts(viewer, day) == 0) {
            return new Intel(false, members, 0, 0);
        }
        int wounded = (int) wounded().getOrDefault(target, Map.of()).values().stream()
                .filter(day::isBefore).count();
        return new Intel(true, members, power(target, day), wounded);
    }

    /** Anspruch einer Polity auf eine andere vorhanden? */
    default boolean hasClaim(PolityId claimant, PolityId target) {
        return claims().getOrDefault(claimant, Set.of()).contains(target);
    }

    /** Wie viele Polities Anspruch auf {@code target} erheben. */
    default int claimsAgainst(PolityId target) {
        return (int) claims().values().stream().filter(set -> set.contains(target)).count();
    }

    default Map<ActorRef, MilitaryRank> garrisonOf(PolityId polity) {
        return garrisons().getOrDefault(polity, Map.of());
    }

    default long soldiersOf(PolityId polity) {
        return garrisonOf(polity).values().stream().filter(rank -> rank == MilitaryRank.SOLDIER).count();
    }

    default long officersOf(PolityId polity) {
        return garrisonOf(polity).values().stream().filter(rank -> rank == MilitaryRank.OFFICER).count();
    }

    /** Bis zu welchem Tag (einschließlich) eine Polity sich Neutralität erkauft hat. */
    Map<PolityId, Day> neutralUntil();

    default boolean isNeutral(PolityId polity, Day day) {
        Day until = neutralUntil().get(polity);
        return until != null && !until.isBefore(day);
    }
}
