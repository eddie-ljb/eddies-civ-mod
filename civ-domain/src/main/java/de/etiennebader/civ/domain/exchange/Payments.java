package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.actor.ActorRef;

/**
 * Bezahlt ein Geschäft. Die Börse kennt kein Geld; der Aufrufer bucht über das Konto.
 */
@FunctionalInterface
public interface Payments {

    /**
     * @return ob die Zahlung ausgeführt wurde; {@code false}, wenn das Geld nicht reicht
     */
    boolean pay(ActorRef from, ActorRef to, long units);

    /** Wie viele Aktien zu diesem Preis der Zahler höchstens bezahlen kann; ohne Wissen unbegrenzt. */
    default long affordable(ActorRef payer, long price) {
        return Long.MAX_VALUE;
    }
}
