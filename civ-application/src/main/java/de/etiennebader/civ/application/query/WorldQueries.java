package de.etiennebader.civ.application.query;

import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.market.MarketView;
import de.etiennebader.civ.domain.market.PriceLevel;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.protection.Denial;
import de.etiennebader.civ.domain.protection.Hazard;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.protection.ProtectionContext;
import de.etiennebader.civ.domain.protection.ProtectionRules;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shop.ShopRegistryView;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.TerritoryView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lesender Zugriff auf den gesamten Zustand.
 */
public final class WorldQueries {

    private static final ProtectionRules PROTECTION_RULES = new ProtectionRules();

    private final Repositories repositories;
    private final ProtectionContext protectionContext = new ProtectionContext() {
        @Override
        public TerritoryView territory() {
            return WorldQueries.this.territory();
        }

        @Override
        public Optional<? extends PolityView> polity(PolityId id) {
            return WorldQueries.this.polity(id);
        }

        @Override
        public Optional<? extends LandView> land(PolityId polity) {
            return repositories.land().findByPolity(polity);
        }
    };

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

    /** Sucht eine Polity nach ihrem Namen, ohne Groß- und Kleinschreibung zu beachten. */
    public Optional<PolityView> polityNamed(String name) {
        Objects.requireNonNull(name, "name");
        String wanted = name.strip();
        return repositories.polities().findAll().stream()
                .filter(polity -> polity.name().value().equalsIgnoreCase(wanted))
                .map(PolityView.class::cast)
                .findFirst();
    }

    /** Die Gemeinde, der ein Akteur angehört, falls es eine gibt. */
    public Optional<PolityView> municipalityOf(ActorRef actor) {
        Objects.requireNonNull(actor, "actor");
        return repositories.polities().findAll().stream()
                .filter(polity -> polity.kind() == PolityKind.TERRITORIAL && polity.isMember(actor))
                .map(PolityView.class::cast)
                .findFirst();
    }

    /** Die Gemeinde, deren Gemeindehaus an dieser Stelle steht. */
    public Optional<PolityView> polityWithSeat(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        return repositories.polities().findAll().stream()
                .filter(polity -> polity.seat().filter(position::equals).isPresent())
                .map(PolityView.class::cast)
                .findFirst();
    }

    /**
     * Ob ein Akteur an einem Block handeln darf (Gebietsschutz).
     *
     * @return die Verweigerung samt Grund; leer, wenn die Handlung erlaubt ist
     */
    public Optional<Denial> access(ActorRef actor, ProtectedAction action, BlockPosition position) {
        return PROTECTION_RULES.check(actor, action, position, protectionContext);
    }

    /** Ob eine Naturgewalt wie eine Explosion an diesem Block wirken darf. */
    public boolean allows(Hazard hazard, BlockPosition position) {
        return PROTECTION_RULES.allows(hazard, position, protectionContext);
    }

    public TerritoryView territory() {
        return repositories.territory().load();
    }

    public ShopRegistryView shops() {
        return repositories.shops().load();
    }

    public MarketView market() {
        return repositories.market().load();
    }

    /**
     * Das Preisniveau aus Geldmenge und Bevölkerung.
     *
     * @param population Zahl der Wirtschaftsteilnehmer, z. B. bekannte Spieler
     * @param reference  Geldmenge je Kopf, bei der Preise ihrem Grundpreis entsprechen
     */
    public PriceLevel priceLevel(int population, Money reference) {
        return PriceLevel.of(ledger().totalSupply(reference.currency()), population, reference);
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
