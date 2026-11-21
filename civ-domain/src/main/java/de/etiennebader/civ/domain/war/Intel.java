package de.etiennebader.civ.domain.war;

/**
 * Was eine Polity über die Truppe einer anderen weiß. Ohne Ausspäher ist nur die grobe Größe bekannt
 * ({@code exact = false}; {@code power} und {@code wounded} sind dann 0).
 */
public record Intel(boolean exact, int members, long power, int wounded) {
}
