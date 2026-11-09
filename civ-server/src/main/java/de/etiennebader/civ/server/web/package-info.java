/**
 * Eingebettete Webseite: Karte, Gemeinden, Marktpreise und, nach Anmeldung per Code aus dem Spiel,
 * Kontostand, Überweisungen und Abstimmungen. Nutzt nur {@code java.base}, keine zusätzlichen Bibliotheken.
 *
 * <p>Alle Zugriffe auf den Spielzustand laufen über {@link de.etiennebader.civ.server.web.GameThread} auf dem
 * Spielthread; die Webthreads warten nur auf das Ergebnis.
 */
package de.etiennebader.civ.server.web;
