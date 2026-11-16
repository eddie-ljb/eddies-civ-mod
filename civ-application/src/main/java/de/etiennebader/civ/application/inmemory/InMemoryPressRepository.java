package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.media.Press;
import de.etiennebader.civ.domain.media.PressRepository;

import java.util.Objects;

/**
 * Hält die Presse im Arbeitsspeicher.
 */
public final class InMemoryPressRepository implements PressRepository {

    private Press value = Press.empty();

    @Override
    public Press load() {
        return value;
    }

    @Override
    public void save(Press value) {
        this.value = Objects.requireNonNull(value, "value");
    }
}
