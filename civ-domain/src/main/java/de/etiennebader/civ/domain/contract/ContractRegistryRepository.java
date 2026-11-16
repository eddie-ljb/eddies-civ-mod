package de.etiennebader.civ.domain.contract;

/**
 * Speicher für die Verträge; es gibt genau ein {@link ContractRegistry}.
 */
public interface ContractRegistryRepository {

    ContractRegistry load();

    void save(ContractRegistry contracts);
}
