package de.etiennebader.civ.domain.shared;

import java.util.UUID;

/**
 * Erzeugt neue Identitäten. Austauschbar, damit Tests und die Headless-Simulation
 * reproduzierbare IDs erhalten.
 */
@FunctionalInterface
public interface IdGenerator {

    UUID next();
}
