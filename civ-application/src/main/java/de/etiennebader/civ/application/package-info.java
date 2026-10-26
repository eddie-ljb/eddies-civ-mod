/**
 * Application-Schicht: Use Cases, die Aggregate laden, fachliche Methoden aufrufen, speichern
 * und die entstandenen Events veröffentlichen. Regeln, die mehrere Aggregate betreffen,
 * liegen hier und nicht im Kern.
 *
 * <p>Einstiegspunkt ist {@link de.etiennebader.civ.application.CivApplication}. Die Schicht
 * kennt weder Minecraft noch eine konkrete Speichertechnik.
 */
package de.etiennebader.civ.application;
