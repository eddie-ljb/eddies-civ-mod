package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
