package de.etiennebader.civ.sim.agent;

/**
 * Alles, was ein Akteur an einem Tag tun kann. Einkommen und Lebenshaltung laufen zusätzlich automatisch.
 */
public enum AgentAction {
    /** Nichts Besonderes tun. */
    REST,
    /** Eine eigene Gemeinde gründen. */
    FOUND_POLITY,
    /** Der attraktivsten Gemeinde beitreten. */
    JOIN_POLITY,
    /** Die eigene Gemeinde verlassen und Grundstücke zurückverkaufen. */
    LEAVE_POLITY,
    /** Ein freies Wohngrundstück der eigenen Gemeinde kaufen. */
    BUY_PLOT,
    /** Bargeld abheben. */
    WITHDRAW_CASH,
    /** Bargeld einzahlen. */
    DEPOSIT_CASH,
    /** Einem anderen Akteur Bargeld geben, z. B. für eine Ware. */
    PAY_IN_CASH
}
