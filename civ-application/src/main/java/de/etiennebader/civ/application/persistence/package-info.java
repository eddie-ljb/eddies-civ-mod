/**
 * Speichern und Laden des Weltzustands, unabhängig von der Speichertechnik.
 *
 * <p>Im laufenden Spiel liegt der Arbeitsstand in den Repositories im Speicher. Der
 * {@link de.etiennebader.civ.application.persistence.ChangeTracker} merkt sich anhand der Domain Events,
 * was sich geändert hat. Beim Speichern erfasst
 * {@link de.etiennebader.civ.application.persistence.WorldPersistence#prepareSave()} davon eine
 * unveränderliche Kopie ({@link de.etiennebader.civ.application.persistence.StateDelta}), die der Port
 * {@link de.etiennebader.civ.application.persistence.StateStore} auch in einem eigenen Thread schreiben kann.
 * Gespeichert wird zusammen mit der Weltspeicherung von Minecraft, damit Datenbank und Welt zusammenpassen.
 */
package de.etiennebader.civ.application.persistence;
