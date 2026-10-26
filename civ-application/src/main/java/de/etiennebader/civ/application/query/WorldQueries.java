package de.etiennebader.civ.application.query;

import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.TerritoryView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lesender Zugriff auf den gesamten Zustand.
 */
public final class WorldQueries {

    private final Repositories repositories;

    public WorldQueries(Repositories repositories) {
        this.repositories = Objects.requireNonNull(repositories, "repositories");
    }

    /** Alle Polities in der Reihenfolge des Repositorys. */
    public List<PolityView> polities() {
        return List.copyOf(repositories.polities().findAll());
    }

    public Optional<PolityView> polity(PolityId id) {
        return repositories.polities().findById(id).map(PolityView.class::cast);
    }

    /** Die Gemeinde, der ein Akteur angehört, falls es eine gibt. */
    public Optional<PolityView> municipalityOf(ActorRef actor) {
        Objects.requireNonNull(actor, "actor");
        return repositories.polities().findAll().stream()
                .filter(polity -> polity.kind() == PolityKind.TERRITORIAL && polity.isMember(actor))
                .map(PolityView.class::cast)
                .findFirst();
    }

    public TerritoryView territory() {
        return repositories.territory().load();
    }

    /**
     * @throws IllegalArgumentException wenn es für die Polity kein Grundbuch gibt
     */
    public LandView land(PolityId polity) {
        return repositories.land().findByPolity(polity)
                .orElseThrow(() -> new IllegalArgumentException("Kein Grundbuch für " + polity));
    }

    public LedgerView ledger() {
        return repositories.ledger().load();
    }
}
