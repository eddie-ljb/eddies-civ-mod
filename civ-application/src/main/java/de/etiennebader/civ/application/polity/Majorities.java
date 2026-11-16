package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.PolityView;

import java.util.List;

/**
 * Welche Mehrheit eine Rechtsänderung braucht.
 */
public final class Majorities {

    /** Wer über Anträge abstimmt: die Abgeordneten, wenn ein Parlament gewählt ist, sonst die Bürger ohne Dorfbewohner. */
    public static long electorate(PolityView polity) {
        if (polity.parliament().seated()) {
            return polity.parliament().size();
        }
        return polity.members().stream()
                .filter(membership -> membership.type() == MembershipType.CITIZEN)
                .filter(membership -> !(membership.member() instanceof PersonRef))
                .count();
    }

    /** Einfache Mehrheit. */
    public static final long SIMPLE = 50;

    private Majorities() {
    }

    /**
     * Prozent der Stimmen, die eine Änderung dieser Regelung braucht: die Verfassungsmehrheit bei Verfassungsartikeln
     * einer ausgerufenen Verfassung, sonst {@link #SIMPLE}.
     */
    public static long requiredPercent(PolityView polity, ParameterKey<?> key) {
        if (polity.constitution().proclaimed() && StandardParameters.isConstitutional(key)) {
            return polity.laws().valueOf(StandardParameters.AMENDMENT_MAJORITY_PERCENT);
        }
        return SIMPLE;
    }

    /**
     * Prozent, die eine Abstimmung braucht: das Maximum über alle Regelungen, die sie setzt oder (bei Aufhebung)
     * wegnimmt.
     */
    public static long requiredPercent(PolityView polity, Motion motion) {
        return affected(polity, motion).stream().mapToLong(key -> requiredPercent(polity, key)).max().orElse(SIMPLE);
    }

    /**
     * Ob dieser Antrag eine Abstimmung braucht, statt von der Spitze verordnet zu werden: bei Abstimmungspflicht, mit
     * gewähltem Parlament immer, sonst bei Verfassungsartikeln einer ausgerufenen Verfassung.
     */
    public static boolean voteRequired(PolityView polity, Motion motion) {
        if (polity.laws().valueOf(StandardParameters.LAWS_BY_VOTE) || polity.parliament().seated()) {
            return true;
        }
        return affected(polity, motion).stream().anyMatch(key -> voteRequired(polity, key));
    }

    /** Regelungen, die der Antrag berührt; bei Aufhebung die des aufzuhebenden Paragraphen. */
    public static List<ParameterKey<?>> affected(PolityView polity, Motion motion) {
        List<ParameterValue<?>> values = motion instanceof Motion.Repeal repeal
                ? polity.laws().inForce().stream()
                .filter(enacted -> enacted.statute().id().equals(repeal.statute()))
                .flatMap(enacted -> enacted.statute().values().stream())
                .toList()
                : motion.values();
        return values.stream().<ParameterKey<?>>map(ParameterValue::key).toList();
    }

    /** Ob die Änderung dieser Regelung eine Abstimmung braucht, statt verordnet zu werden. */
    public static boolean voteRequired(PolityView polity, ParameterKey<?> key) {
        return polity.laws().valueOf(StandardParameters.LAWS_BY_VOTE) || polity.parliament().seated()
                || polity.constitution().proclaimed() && StandardParameters.isConstitutional(key);
    }
}
