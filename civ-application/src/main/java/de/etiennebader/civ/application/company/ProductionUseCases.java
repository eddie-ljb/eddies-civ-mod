package de.etiennebader.civ.application.company;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.SupplyChange;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.market.Market;
import de.etiennebader.civ.domain.market.PriceLevel;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.production.Branch;
import de.etiennebader.civ.domain.production.Enterprise;
import de.etiennebader.civ.domain.production.ProductionRegistry;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * Produktion der Firmen.
 *
 * <ul>
 *   <li>Jede Firma wählt eine {@link Branch}; jeden Tag erzeugen ihre Arbeitskräfte Waren: die Leitung und die Mitglieder
 *       zählen je eine halbe, Angestellte mit Arbeitsvertrag (Spieler oder Dorfbewohner) je eine Arbeitskraft. Mehr
 *       Leute bringen mehr, aber mit abnehmendem Grenzertrag.</li>
 *   <li>Investitionen aus der Firmenkasse heben die Ausstattung um Stufen, jede Stufe +25 % Ertrag.</li>
 *   <li>Die Waren landen im Lager. Mit Verkauf „automatisch“ gehen sie am Tagesende an den Servermarkt und der Erlös in
 *       die Firmenkasse; sonst holt die Leitung sie ab oder verkauft sie von Hand.</li>
 *   <li>Löhne zahlen die Arbeitsverträge selbst; Gewinn bleibt in der Kasse und kann als Dividende ausgeschüttet
 *       werden.</li>
 * </ul>
 */
public final class ProductionUseCases {

    /** Arbeitskraft eines Mitglieds der Leitung. */
    public static final double MEMBER_LABOUR = 0.5;
    /** Arbeitskraft eines Angestellten. */
    public static final double EMPLOYEE_LABOUR = 1.0;

    /** Ergebnis eines Produktionstags einer Firma. */
    public record DayResult(PolityId company, Map<Commodity, Long> produced, Money revenue) {
    }

    /** Erlös eines Verkaufs aus dem Lager. */
    public record Sale(Map<Commodity, Long> sold, Money revenue) {
    }

    private final Aggregates aggregates;
    private final CompanyUseCases companies;

    public ProductionUseCases(Aggregates aggregates, CompanyUseCases companies) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.companies = Objects.requireNonNull(companies, "companies");
    }

    public Enterprise chooseBranch(ActorRef head, PolityId company, Branch branch, Day day) {
        companies.requireHead(head, company, PolityKind.COMPANY);
        ProductionRegistry registry = aggregates.production();
        Enterprise enterprise = registry.setBranch(company, branch, day);
        aggregates.commit(registry);
        return enterprise;
    }

    /** Investiert aus der Firmenkasse in die Ausstattung. */
    public Enterprise invest(ActorRef head, PolityId company, Money amount, Day day) {
        Polity polity = companies.requireHead(head, company, PolityKind.COMPANY);
        ProductionRegistry registry = aggregates.production();
        if (registry.enterpriseOf(company).isEmpty()) {
            throw new IllegalStateException("Erst eine Branche wählen");
        }
        Ledger ledger = aggregates.ledger();
        ledger.burn(polity.asActor(), amount, SupplyChange.INVESTMENT, day);
        Enterprise enterprise = registry.invest(company, amount.amount(), day);
        aggregates.commit(ledger);
        aggregates.commit(registry);
        return enterprise;
    }

    public Enterprise autoSell(ActorRef head, PolityId company, boolean auto, Day day) {
        companies.requireHead(head, company, PolityKind.COMPANY);
        ProductionRegistry registry = aggregates.production();
        Enterprise enterprise = registry.autoSell(company, auto, day);
        aggregates.commit(registry);
        return enterprise;
    }

    /** Nimmt Waren aus dem Lager, z. B. damit die Leitung sie ins Inventar bekommt. */
    public long take(ActorRef head, PolityId company, Commodity commodity, long quantity, Day day) {
        companies.requireHead(head, company, PolityKind.COMPANY);
        ProductionRegistry registry = aggregates.production();
        long available = registry.enterpriseOf(company).map(found -> found.stockOf(commodity)).orElse(0L);
        long taken = Math.min(quantity, available);
        if (taken <= 0) {
            throw new IllegalArgumentException("Nichts davon auf Lager");
        }
        registry.take(company, commodity, taken, day);
        aggregates.commit(registry);
        return taken;
    }

    /** Verkauft das ganze Lager an den Servermarkt. */
    public Sale sellStock(ActorRef head, PolityId company, Function<Commodity, OptionalLong> basePrice,
                          PriceLevel level, Day day) {
        companies.requireHead(head, company, PolityKind.COMPANY);
        return sellAll(aggregates.polity(company), basePrice, level, day);
    }

    /** Arbeitskräfte einer Firma: Mitglieder zur Hälfte, Angestellte ganz. */
    public double workforce(PolityId company) {
        Polity polity = aggregates.polity(company);
        double members = polity.members().stream().map(Membership::member).filter(member -> !(member instanceof PersonRef))
                .count() * MEMBER_LABOUR;
        long employees = employees(company).size();
        return members + employees * EMPLOYEE_LABOUR;
    }

    /** Angestellte mit geltendem Arbeitsvertrag. */
    public List<ActorRef> employees(PolityId company) {
        ActorRef employer = aggregates.polity(company).asActor();
        return aggregates.contracts().contracts().stream()
                .filter(Contract::active)
                .filter(contract -> contract.kind() == ContractKind.EMPLOYMENT
                        && contract.offeror().equals(employer))
                .map(Contract::counterparty)
                .distinct()
                .toList();
    }

    /** Was die Firma mit ihrer heutigen Belegschaft an einem Tag erzeugen würde. */
    public Map<Commodity, Long> forecast(PolityId company) {
        return aggregates.production().enterpriseOf(company).map(found -> found.dailyOutput(workforce(company)))
                .orElse(Map.of());
    }

    /** Tagesende: alle Betriebe produzieren; mit automatischem Verkauf geht das Lager an den Markt. */
    public List<DayResult> endOfDay(Function<Commodity, OptionalLong> basePrice, PriceLevel level, Day day) {
        List<DayResult> results = new ArrayList<>();
        ProductionRegistry registry = aggregates.production();
        for (Enterprise enterprise : registry.enterprises()) {
            Optional<Polity> polity = aggregates.repositories().polities().findById(enterprise.company());
            if (polity.isEmpty()) {
                registry.close(enterprise.company(), day);
                continue;
            }
            if (enterprise.lastDay().filter(day::equals).isPresent()) {
                continue;
            }
            Map<Commodity, Long> output = enterprise.dailyOutput(workforce(enterprise.company()));
            Enterprise produced = registry.produce(enterprise.company(), output, day);
            aggregates.commit(registry);
            Money revenue = Money.of(0, polity.get().currency());
            if (produced.autoSell()) {
                revenue = sellAll(polity.get(), basePrice, level, day).revenue();
            }
            results.add(new DayResult(enterprise.company(), output, revenue));
        }
        aggregates.commit(registry);
        return results;
    }

    /** Schließt den Betrieb einer aufgelösten Firma. */
    public void close(PolityId company, Day day) {
        ProductionRegistry registry = aggregates.production();
        registry.close(company, day);
        aggregates.commit(registry);
    }

    private Sale sellAll(Polity company, Function<Commodity, OptionalLong> basePrice, PriceLevel level, Day day) {
        ProductionRegistry registry = aggregates.production();
        Enterprise enterprise = registry.enterpriseOf(company.id())
                .orElseThrow(() -> new IllegalStateException("Erst eine Branche wählen"));
        Market market = aggregates.market();
        Ledger ledger = aggregates.ledger();
        Map<Commodity, Long> sold = new LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<Commodity, Long> entry : enterprise.stock().entrySet()) {
            OptionalLong base = basePrice.apply(entry.getKey());
            if (base.isEmpty() || entry.getValue() <= 0) {
                continue;
            }
            int quantity = (int) Math.min(Integer.MAX_VALUE, entry.getValue());
            Money proceeds;
            try {
                proceeds = market.sell(company.asActor(), entry.getKey(), quantity, base.getAsLong(), level, day);
            } catch (de.etiennebader.civ.domain.shared.DomainException saturated) {
                continue;
            }
            if (proceeds.amount() > 0) {
                ledger.mint(company.asActor(), proceeds, SupplyChange.MARKET_SALE, day);
            }
            registry.take(company.id(), entry.getKey(), quantity, day);
            sold.put(entry.getKey(), (long) quantity);
            total += proceeds.amount();
        }
        registry.recordRevenue(company.id(), total, day);
        aggregates.commit(market);
        aggregates.commit(ledger);
        aggregates.commit(registry);
        return new Sale(sold, Money.of(total, company.currency()));
    }
}
