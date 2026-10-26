package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.application.Repositories;

/**
 * Erzeugt einen leeren Satz In-Memory-Repositories.
 */
public final class InMemoryRepositories {

    private InMemoryRepositories() {
    }

    public static Repositories create() {
        return new Repositories(new InMemoryPolityRepository(), new InMemoryTerritoryRepository(),
                new InMemoryLandRepository(), new InMemoryLedgerRepository());
    }
}
