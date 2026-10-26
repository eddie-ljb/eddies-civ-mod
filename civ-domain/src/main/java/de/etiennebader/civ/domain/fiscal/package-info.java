/**
 * Steuern: Domain Services, die Recht, Grundbuch und Hauptbuch verbinden.
 *
 * <p>Liegt in einem eigenen Paket, damit zwischen {@code polity}, {@code law}, {@code land}
 * und {@code economy} kein Zyklus entsteht.
 *
 * <p>Darf verwenden: alle anderen Pakete des Kerns.
 */
package de.etiennebader.civ.domain.fiscal;
