package de.etiennebader.civ.sim.metrics;

import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.domain.shared.DomainEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Objects;

/**
 * Schreibt jedes Event als Zeile {@code Tag<TAB>Typ<TAB>Inhalt}. Damit lässt sich zurückverfolgen,
 * welche Änderung zu einem Invariantenbruch geführt hat, z. B. mit {@code grep}.
 */
public final class EventLogWriter implements DomainEventPublisher {

    private final Writer writer;

    public EventLogWriter(Writer writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public void publish(List<? extends DomainEvent> events) {
        try {
            for (DomainEvent event : events) {
                writer.write(Long.toString(event.occurredOn().value()));
                writer.write('\t');
                writer.write(event.getClass().getSimpleName());
                writer.write('\t');
                writer.write(event.toString());
                writer.write('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
