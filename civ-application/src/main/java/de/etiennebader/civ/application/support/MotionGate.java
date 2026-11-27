package de.etiennebader.civ.application.support;

import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.shared.Day;

/**
 * Wird gefragt, wenn eine Abstimmung angenommen wurde, bevor die Änderung in Kraft tritt. Hält die Gegenstelle den
 * Beschluss zurück (z. B. für das Veto des Oberhaupts), setzt sie ihn später selbst in Kraft.
 */
@FunctionalInterface
public interface MotionGate {

    /** Beschlüsse gehen sofort durch. */
    MotionGate PASS = (polity, proposal, day) -> false;

    /** @return {@code true}, wenn der Beschluss zurückgehalten wird und nicht sofort gilt */
    boolean hold(Polity polity, Proposal proposal, Day day);
}
