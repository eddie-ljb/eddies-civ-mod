package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.LedgerRepository;

import java.util.Objects;

/**
 * Hält das weltweite Hauptbuch im Speicher.
 */
public final class InMemoryLedgerRepository implements LedgerRepository {

    private Ledger ledger = Ledger.empty();

    @Override
    public Ledger load() {
        return ledger;
    }

    @Override
    public void save(Ledger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }
}
