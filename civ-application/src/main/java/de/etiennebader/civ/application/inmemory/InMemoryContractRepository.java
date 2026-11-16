package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.contract.ContractRegistry;
import de.etiennebader.civ.domain.contract.ContractRegistryRepository;

import java.util.Objects;

/**
 * Hält die Verträge im Arbeitsspeicher.
 */
public final class InMemoryContractRepository implements ContractRegistryRepository {

    private ContractRegistry value = ContractRegistry.empty();

    @Override
    public ContractRegistry load() {
        return value;
    }

    @Override
    public void save(ContractRegistry value) {
        this.value = Objects.requireNonNull(value, "value");
    }
}
