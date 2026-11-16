package de.etiennebader.civ.application.support;

import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.domain.community.Community;
import de.etiennebader.civ.domain.contract.ContractRegistry;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.exchange.StockExchange;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.market.Market;
import de.etiennebader.civ.domain.media.Press;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shop.ShopRegistry;
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

    public Market market() {
        return repositories.market().load();
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

    public ContractRegistry contracts() {
        return repositories.contracts().load();
    }

    public void commit(ContractRegistry contracts) {
        repositories.contracts().save(contracts);
        publisher.publish(contracts.pullEvents());
    }

    public StockExchange exchange() {
        return repositories.exchange().load();
    }

    public void commit(StockExchange exchange) {
        repositories.exchange().save(exchange);
        publisher.publish(exchange.pullEvents());
    }

    public Press press() {
        return repositories.press().load();
    }

    public void commit(Press press) {
        repositories.press().save(press);
        publisher.publish(press.pullEvents());
    }

    public Community community() {
        return repositories.community().load();
    }

    public void commit(Community community) {
        repositories.community().save(community);
        publisher.publish(community.pullEvents());
    }

    public ShopRegistry shops() {
        return repositories.shops().load();
    }

    public void commit(ShopRegistry shops) {
        repositories.shops().save(shops);
        publisher.publish(shops.pullEvents());
    }

    public void commit(Market market) {
        repositories.market().save(market);
        publisher.publish(market.pullEvents());
    }
}
