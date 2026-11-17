package de.etiennebader.civ.domain.production;

import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Alle Firmenbetriebe. Klein genug, um als Ganzes gespeichert zu werden.
 */
public final class ProductionRegistry extends AggregateRoot<ProductionEvent> implements ProductionView {

    private final Map<PolityId, Enterprise> enterprises = new LinkedHashMap<>();

    private ProductionRegistry() {
    }

    public static ProductionRegistry empty() {
        return new ProductionRegistry();
    }

    public static ProductionRegistry restore(Collection<Enterprise> enterprises) {
        ProductionRegistry registry = new ProductionRegistry();
        enterprises.forEach(enterprise -> registry.enterprises.put(enterprise.company(), enterprise));
        return registry;
    }

    @Override
    public Collection<Enterprise> enterprises() {
        return List.copyOf(enterprises.values());
    }

    @Override
    public Optional<Enterprise> enterpriseOf(PolityId company) {
        return Optional.ofNullable(enterprises.get(company));
    }

    /** Gibt einer Firma eine Branche; ein Wechsel halbiert die bisherige Ausstattung. */
    public Enterprise setBranch(PolityId company, Branch branch, Day day) {
        Enterprise current = enterprises.get(company);
        Enterprise next = current == null ? Enterprise.start(company, branch)
                : current.branch() == branch ? current : current.switchedTo(branch);
        return put(next, day);
    }

    public Enterprise invest(PolityId company, long amount, Day day) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Investition muss positiv sein");
        }
        return put(require(company).invested(amount), day);
    }

    public Enterprise autoSell(PolityId company, boolean auto, Day day) {
        return put(require(company).selling(auto), day);
    }

    /** Legt die Tagesproduktion ins Lager, bis zur Lagergrenze. */
    public Enterprise produce(PolityId company, Map<Commodity, Long> output, Day day) {
        Enterprise current = require(company);
        Map<Commodity, Long> stock = new TreeMap<>(current.stock());
        long total = 0;
        for (Map.Entry<Commodity, Long> entry : output.entrySet()) {
            long room = Enterprise.STOCK_LIMIT - stock.getOrDefault(entry.getKey(), 0L);
            long added = Math.max(0, Math.min(room, entry.getValue()));
            if (added > 0) {
                stock.merge(entry.getKey(), added, Long::sum);
                total += added;
            }
        }
        return put(current.withStock(stock).produced(stock, day, total, 0), day);
    }

    /** Nimmt Waren aus dem Lager, z. B. zum Verkauf oder zum Abholen. */
    public Enterprise take(PolityId company, Commodity commodity, long quantity, Day day) {
        Enterprise current = require(company);
        long have = current.stockOf(commodity);
        if (quantity <= 0 || quantity > have) {
            throw new IllegalArgumentException("Nur " + have + " auf Lager");
        }
        Map<Commodity, Long> stock = new TreeMap<>(current.stock());
        if (have == quantity) {
            stock.remove(commodity);
        } else {
            stock.put(commodity, have - quantity);
        }
        return put(current.withStock(stock), day);
    }

    /** Hält den Erlös des Tagesverkaufs fest. */
    public Enterprise recordRevenue(PolityId company, long revenue, Day day) {
        Enterprise current = require(company);
        return put(current.produced(current.stock(), current.lastDay().orElse(day), current.lastOutput(), revenue), day);
    }

    public void close(PolityId company, Day day) {
        if (enterprises.remove(company) != null) {
            raise(new ProductionEvent.EnterpriseChanged(company, day));
        }
    }

    private Enterprise require(PolityId company) {
        Enterprise found = enterprises.get(Objects.requireNonNull(company, "company"));
        if (found == null) {
            throw new IllegalStateException("Die Firma hat noch keinen Betrieb (Branche wählen)");
        }
        return found;
    }

    private Enterprise put(Enterprise enterprise, Day day) {
        enterprises.put(enterprise.company(), enterprise);
        raise(new ProductionEvent.EnterpriseChanged(enterprise.company(), day));
        return enterprise;
    }
}
