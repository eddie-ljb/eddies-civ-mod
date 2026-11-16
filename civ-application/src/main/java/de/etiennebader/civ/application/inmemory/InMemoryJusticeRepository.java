package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.justice.Judiciary;
import de.etiennebader.civ.domain.justice.JusticeRepository;

import java.util.Objects;

/** Gerichte und Verfahren im Speicher. */
public final class InMemoryJusticeRepository implements JusticeRepository {

    private Judiciary judiciary = Judiciary.empty();

    @Override
    public Judiciary load() {
        return judiciary;
    }

    @Override
    public void save(Judiciary judiciary) {
        this.judiciary = Objects.requireNonNull(judiciary, "judiciary");
    }
}
