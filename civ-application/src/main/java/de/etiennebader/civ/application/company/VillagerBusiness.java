package de.etiennebader.civ.application.company;

import de.etiennebader.civ.application.civil.ContractUseCases;
import de.etiennebader.civ.application.land.BuyPlot;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.exchange.Dividend;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.production.Branch;
import de.etiennebader.civ.domain.production.Enterprise;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Dorfbewohner führen ihre Firmen nach ihrer Persönlichkeit. Je Firma geschieht höchstens eine Handlung am Tag, und
 * nicht an jedem Tag; die Reihenfolge der Prioritäten ist:
 *
 * <ol>
 *   <li><b>Entlassen:</b> Reicht die Kasse nicht mehr für wenige Tage Löhne, kündigt der Chef dem zuletzt Eingestellten.
 *       Sparsame handeln früher, Großzügige halten länger durch.</li>
 *   <li><b>Branche wählen:</b> Naturverbundene wählen Forst, Obst, Schafe oder Acker, Traditionelle Acker und
 *       Viehzucht, Fortschrittliche Bergwerk, Ziegelei und Baufirma.</li>
 *   <li><b>Grundstück und Kernbelegschaft:</b> Die ersten {@value #BASE_STAFF} Stellen besetzt der Chef zuerst; die Firma kauft
 *       ein angebotenes Gewerbe-, Industrie- oder Landwirtschaftsgrundstück der Gemeinde, wenn sie noch keines hat.</li>
 *   <li><b>Dividende:</b> Alle sieben Tage, wenn die Kasse über der Rücklage liegt; Großzügige schütten mehr aus, Sparsame
 *       weniger.</li>
 *   <li><b>Ausbauen:</b> Investition in die nächste Ausstattungsstufe, aber nur, wenn sie einen tragbaren Teil des
 *       Überschusses kostet: Fortschrittliche und Großzügige leisten sich mehr, Sparsame halten den Überschuss zurück.</li>
 *   <li><b>Einstellen:</b> Arbeitslose Dorfbewohner der Gemeinde; wer Gemeinsinn hat, zahlt mehr Lohn. Die Belegschaft ist
 *       auf {@value #BASE_STAFF} plus 2 je Ausstattungsstufe begrenzt und muss für zehn Tage bezahlbar sein.</li>
 * </ol>
 *
 * <p>Der Zufall ist fest je Chef, Tag und Handlung: dasselbe Spiel verläuft gleich. Gebäude auf dem Grundstück
 * errichtet das Spiel nicht; der Kauf ist die wirtschaftliche Seite.
 */
public final class VillagerBusiness {

    public static final int BASE_STAFF = 2;
    public static final int DIVIDEND_DAYS = 7;
    /** Tageswahrscheinlichkeit, dass ein Chef überhaupt etwas unternimmt. */
    static final double ACTS = 0.5;
    private static final long MIN_INVESTMENT = 200;

    /** Was an einem Tag geschah. */
    public record Report(int fired, int branches, int dividends, int investments, int hires, int plots) {
    }

    private final Supplier<WorldQueries> queries;
    private final CompanyUseCases companies;
    private final ProductionUseCases production;
    private final ContractUseCases contracts;
    private final BuyPlot buyPlot;

    public VillagerBusiness(Supplier<WorldQueries> queries, CompanyUseCases companies, ProductionUseCases production,
                            ContractUseCases contracts, BuyPlot buyPlot) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.companies = Objects.requireNonNull(companies, "companies");
        this.production = Objects.requireNonNull(production, "production");
        this.contracts = Objects.requireNonNull(contracts, "contracts");
        this.buyPlot = Objects.requireNonNull(buyPlot, "buyPlot");
    }

    public Report execute(Day day) {
        Objects.requireNonNull(day, "day");
        int[] counts = new int[6];
        List<PolityView> firms = queries.get().companies().stream()
                .sorted(Comparator.comparing(company -> company.id().value())).toList();
        for (PolityView firm : firms) {
            Optional<PersonRef> head = firm.officeTerm(Office.MAYOR).map(OfficeTerm::holder)
                    .filter(PersonRef.class::isInstance).map(PersonRef.class::cast);
            if (head.isEmpty() || VillagerAmbitions.roll(head.get(), day, 0x80) >= ACTS) {
                continue;
            }
            int action = act(firm.id(), head.get(), day);
            if (action >= 0) {
                counts[action]++;
            }
        }
        return new Report(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5]);
    }

    /** @return Nummer der Handlung (Index im {@link Report}) oder -1 */
    private int act(de.etiennebader.civ.domain.shared.PolityId id, PersonRef head, Day day) {
        WorldQueries world = queries.get();
        PolityView firm = world.polity(id).orElseThrow();
        Personality personality = Personality.of(head);
        long treasury = world.ledger().balanceOf(firm.asActor(), firm.currency()).amount();
        List<Contract> staff = world.contracts().active().stream()
                .filter(contract -> contract.kind() == ContractKind.EMPLOYMENT && contract.offeror().equals(firm.asActor()))
                .sorted(Comparator.comparingInt(Contract::number)).toList();
        long wages = staff.stream().mapToLong(contract -> contract.perDay().amount()).sum();
        long reserve = Math.max(MIN_INVESTMENT, wages * reserveDays(personality));
        Optional<Enterprise> enterprise = world.production().enterpriseOf(id);
        try {
            if (!staff.isEmpty() && treasury < wages * fireDays(personality)) {
                contracts.giveNotice(staff.getLast().id(), head, day);
                return 0;
            }
            if (enterprise.isEmpty()) {
                production.chooseBranch(head, id, builderNeeded(world) ? Branch.CONSTRUCTION
                        : branchFor(personality, head), day);
                return 1;
            }
            long free = treasury - reserve;
            int cap = BASE_STAFF + 2 * enterprise.get().level();
            if (staff.size() < BASE_STAFF && hire(world, firm, head, personality, free, day)) {
                return 4;
            }
            if (buyPlot(world, firm, head, enterprise.get(), free, day)) {
                return 5;
            }
            boolean dividendDue = isDividendDue(world, firm, day);
            double dividendBias = 0.5 - personality.thrift() / 200.0 + personality.community() / 400.0;
            if (dividendDue && free > 0 && VillagerAmbitions.roll(head, day, 0x81) < dividendBias
                    && payDividend(firm, head, free, personality, day)) {
                return 2;
            }
            long next = enterprise.get().toNextLevel();
            if (next > 0 && free > 0 && next <= free * Math.min(1.0, 2 * investRatio(personality))) {
                // Nur ganze Stufen, und nur wenn sie im Verhältnis zum Überschuss tragbar sind.
                production.invest(head, id, Money.of(Math.max(MIN_INVESTMENT, next), firm.currency()), day);
                return 3;
            }
            if (dividendDue && free > 0 && payDividend(firm, head, free, personality, day)) {
                return 2;
            }
            if (staff.size() < cap && hire(world, firm, head, personality, free, day)) {
                return 4;
            }
        } catch (DomainException | IllegalStateException ignored) {
            // z. B. kein Geld, Aktie nicht ausgegeben: heute geschieht nichts
        }
        return -1;
    }

    /** Tage, die die Rücklage Löhne decken soll: Sparsame 10 bis 20, Großzügige 5 bis 10. */
    static long reserveDays(Personality personality) {
        return personality.thrift() >= 0 ? 10 + personality.thrift() / 10 : 10 + personality.thrift() / 20;
    }

    /** Unter so vielen Tagesläufen Löhnen in der Kasse wird entlassen: Sparsame 5, Großzügige 2. */
    static long fireDays(Personality personality) {
        return personality.thrift() >= 0 ? 3 + personality.thrift() / 50 : 3 + personality.thrift() / 100;
    }

    /** Anteil des Überschusses, der in die Ausstattung geht: Fortschritt und Großzügigkeit erhöhen ihn. */
    static double investRatio(Personality personality) {
        double ratio = 0.35 - personality.tradition() / 400.0 - personality.thrift() / 400.0;
        return Math.max(0.1, Math.min(0.7, ratio));
    }

    /** Anteil des Überschusses, der als Dividende fließt: Großzügige mehr, Sparsame weniger. */
    static double payoutRatio(Personality personality) {
        return Math.max(0.1, Math.min(0.7, 0.4 - personality.thrift() / 300.0));
    }

    /**
     * Ob die Welt noch Baufirmen braucht: ohne sie kann niemand bauen lassen, auch die Gemeinden nicht. Es soll je
     * drei Gemeinden (mindestens eine) eine Baufirma geben; die nächste Firma, die eine Branche wählt, wird eine.
     */
    static boolean builderNeeded(WorldQueries world) {
        long towns = world.polities().stream().filter(polity -> polity.kind() == PolityKind.TERRITORIAL).count();
        long builders = world.companies().stream().filter(company -> world.production().enterpriseOf(company.id())
                .filter(enterprise -> enterprise.branch() == Branch.CONSTRUCTION).isPresent()).count();
        return builders < Math.max(1, (towns + 2) / 3);
    }

    static Branch branchFor(Personality personality, PersonRef head) {
        List<Branch> pool;
        if (personality.nature() > 20) {
            pool = List.of(Branch.FORESTRY, Branch.ORCHARD, Branch.SHEEP, Branch.FARM);
        } else if (personality.tradition() > 20) {
            pool = List.of(Branch.FARM, Branch.RANCH, Branch.FISHERY);
        } else if (personality.tradition() < -20) {
            pool = List.of(Branch.MINE, Branch.BRICKWORKS, Branch.CONSTRUCTION);
        } else {
            pool = List.of(Branch.values());
        }
        return pool.get((int) (VillagerAmbitions.roll(head, Day.of(0), 0x82) * pool.size()));
    }

    private boolean isDividendDue(WorldQueries world, PolityView firm, Day day) {
        if (world.exchange().listing(firm.id()).isEmpty()) {
            return false;
        }
        Day last = world.exchange().dividends().stream().filter(dividend -> dividend.company().equals(firm.id()))
                .map(Dividend::day).max(Comparator.comparingLong(Day::value)).orElse(firm.foundedOn());
        return day.value() - last.value() >= DIVIDEND_DAYS;
    }

    private boolean payDividend(PolityView firm, PersonRef head, long free, Personality personality, Day day) {
        long total = (long) (free * payoutRatio(personality));
        if (total <= 0) {
            return false;
        }
        companies.payDividend(head, firm.id(), Money.of(total, firm.currency()), day);
        return true;
    }

    private boolean hire(WorldQueries world, PolityView firm, PersonRef head, Personality personality, long free,
                         Day day) {
        long wage = ContractUseCases.VILLAGER_MIN_WAGE + Math.max(0, personality.community()) / 4;
        if (free < wage * 10) {
            return false;
        }
        Optional<PolityView> home = world.municipalityOf(head);
        if (home.isEmpty()) {
            return false;
        }
        List<ActorRef> employed = world.contracts().active().stream()
                .filter(contract -> contract.kind() == ContractKind.EMPLOYMENT).map(Contract::payee).toList();
        Optional<PersonRef> candidate = home.get().members().stream().map(Membership::member)
                .filter(PersonRef.class::isInstance).map(PersonRef.class::cast)
                .filter(villager -> !villager.equals(head) && !employed.contains(villager))
                .min(Comparator.comparing(PersonRef::uuid));
        if (candidate.isEmpty()) {
            return false;
        }
        contracts.offerEmployment(firm.asActor(), candidate.get(), Money.of(wage, firm.currency()), 0, day);
        return true;
    }

    private boolean buyPlot(WorldQueries world, PolityView firm, PersonRef head, Enterprise enterprise, long free,
                            Day day) {
        Optional<PolityView> home = world.municipalityOf(head);
        if (home.isEmpty() || !world.land(home.get().id()).plotsHeldBy(firm.asActor()).isEmpty()) {
            return false;
        }
        PlotType wanted = switch (enterprise.branch()) {
            case FARM, RANCH, SHEEP, ORCHARD -> PlotType.AGRICULTURAL;
            case MINE, BRICKWORKS, FORESTRY -> PlotType.INDUSTRIAL;
            default -> PlotType.COMMERCIAL;
        };
        Optional<Plot> plot = world.land(home.get().id()).plots().stream()
                .filter(Plot::isForSale)
                .filter(found -> found.type() == wanted || found.type() == PlotType.COMMERCIAL)
                .filter(found -> found.askingPrice().orElseThrow().amount() * 2 <= free)
                .min(Comparator.comparing((Plot found) -> found.askingPrice().orElseThrow().amount())
                        .thenComparing(found -> found.id().value()));
        if (plot.isEmpty()) {
            return false;
        }
        buyPlot.execute(new BuyPlot.Command(home.get().id(), plot.get().id(), firm.asActor(), day));
        return true;
    }
}
