/**
 * Speichern und Laden des Weltzustands, unabhängig von der Speichertechnik.
 *
 * <p>Im laufenden Spiel liegt der Arbeitsstand in den Repositories im Speicher. Der
 * {@link de.etiennebader.civ.application.persistence.ChangeTracker} merkt sich anhand der Domain Events,
 * was sich geändert hat. {@link de.etiennebader.civ.application.persistence.WorldPersistence#flush()}
 * schreibt genau das über den Port {@link de.etiennebader.civ.application.persistence.StateStore}, und zwar
 * zusammen mit der Weltspeicherung von Minecraft. So bleiben Datenbank und Welt konsistent.
 */
package de.etiennebader.civ.application.persistence;
