package de.etiennebader.civ.domain.economy;

/**
 * Port zur Speicherung des weltweiten Hauptbuchs.
 */
public interface LedgerRepository {

    Ledger load();

    void save(Ledger ledger);
}
