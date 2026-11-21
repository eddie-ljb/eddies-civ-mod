package de.etiennebader.civ.domain.war;

/**
 * Militärischer Rang eines Mitglieds in der Garnison einer Polity.
 *
 * <p>Phase 0: nur zwei Ränge. Offiziere zählen bei der Gefechtsstärke ({@link BattleResolver}) mehr
 * als einfache Soldaten, stehen aber (anders als {@link de.etiennebader.civ.domain.polity.Office}) nicht in
 * Konkurrenz zu zivilen Ämtern derselben Polity.
 */
public enum MilitaryRank {
    SOLDIER,
    OFFICER
}
