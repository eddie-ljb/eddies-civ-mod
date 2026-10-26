package de.etiennebader.civ.application.support;

import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;

import java.util.Objects;

/**
 * Lädt und speichert Aggregate für die Use Cases und veröffentlicht danach ihre Events.
 * Bündelt die Wiederholung „laden, prüfen, speichern, Events abholen“ an einer Stelle.
 */
public final class Aggregates {

    private final Repositories repositories;
    private final DomainEventPublisher publisher;

    public Aggregates(Repositories repositories, DomainEventPublisher publisher) {
        this.repositories = Objects.requireNonNull(repositories, "repositories");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    /**
     * @throws UnknownPolityException wenn es die Polity nicht gibt
     */
    public Polity polity(PolityId id) {
        return repositories.polities().findById(id).orElseThrow(() -> new UnknownPolityException(id));
    }

    /** Das Grundbuch einer bestehenden Polity. */
    public LandRegistry land(PolityId id) {
        return repositories.land().findByPolity(id)
                .orElseThrow(() -> new IllegalStateException("Polity ohne Grundbuch: " + id));
    }

    public TerritoryRegistry territory() {
        return repositories.territory().load();
    }

    public Ledger ledger() {
        return repositories.ledger().load();
    }

    public Repositories repositories() {
        return repositories;
    }

    /** Speichert die Polity und veröffentlicht ihre Events. */
    public void commit(Polity polity) {
        repositories.polities().save(polity);
        publisher.publish(polity.pullEvents());
    }

    public void commit(LandRegistry land) {
        repositories.land().save(land);
        publisher.publish(land.pullEvents());
    }

    public void commit(TerritoryRegistry territory) {
        repositories.territory().save(territory);
        publisher.publish(territory.pullEvents());
    }

    public void commit(Ledger ledger) {
        repositories.ledger().save(ledger);
        publisher.publish(ledger.pullEvents());
    }
}
