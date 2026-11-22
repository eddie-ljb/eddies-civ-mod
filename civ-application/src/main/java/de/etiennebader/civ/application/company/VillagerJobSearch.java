package de.etiennebader.civ.application.company;

import de.etiennebader.civ.application.civil.ContractUseCases;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.production.Branch;
import de.etiennebader.civ.domain.production.Enterprise;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Jobsuche der Dorfbewohner: Wer ohne Arbeitsvertrag ist, sucht mit einer von der Persönlichkeit abhängigen
 * Wahrscheinlichkeit täglich eine Stelle.
 *
 * <ul>
 *     <li>Gesucht wird in allen Firmen mit freier Stelle (Kernbelegschaft plus 2 je Ausstattungsstufe), die zehn Tage
 *         Lohn bezahlen können.</li>
 *     <li>Die Branche muss zur Persönlichkeit passen: Naturnahe zieht es in Land- und Forstwirtschaft, Ordnungsliebende
 *         ins Bergwerk und in die Ziegelei, Gemeinsinnige ins Bauen; die Firma in der eigenen Gemeinde bekommt einen
 *         Heimvorteil.</li>
 *     <li>Der Lohn hängt am Anspruch: Sparsame verlangen mehr ({@link ContractUseCases#VILLAGER_MIN_WAGE} plus Sparsamkeit/4).</li>
 * </ul>
 */
public final class VillagerJobSearch {

    /** Grundwahrscheinlichkeit, dass ein Arbeitsloser an einem Tag sucht; Tatkräftige (Eigenverantwortung) suchen öfter. */
    static final double SEARCH_CHANCE = 0.5;
    /** Zusatzpunkte für eine Firma in der eigenen Gemeinde. */
    static final int HOME_BONUS = 30;

    private final Supplier<WorldQueries> queries;
    private final ContractUseCases contracts;

    public VillagerJobSearch(Supplier<WorldQueries> queries, ContractUseCases contracts) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.contracts = Objects.requireNonNull(contracts, "contracts");
    }

    /** Wie gut eine Branche zur Persönlichkeit passt (etwa -100 bis 100). */
    public static int fit(Branch branch, Personality personality) {
        return switch (branch) {
            case FARM, ORCHARD, FORESTRY, SHEEP, RANCH, FISHERY -> personality.nature();
            case MINE, BRICKWORKS -> personality.order();
            case CONSTRUCTION -> personality.community();
            default -> -personality.thrift();
        };
    }

    /** Lohnforderung eines Dorfbewohners. */
    public static long wageOf(Personality personality) {
        return ContractUseCases.VILLAGER_MIN_WAGE + Math.max(0, personality.thrift()) / 4;
    }

    /**
     * Lässt arbeitslose Dorfbewohner Stellen suchen.
     *
     * @param excluded Dorfbewohner, die gerade nicht suchen (z. B. im Dienst)
     * @return Zahl der neuen Arbeitsverträge
     */
    public int execute(Day day, Predicate<PersonRef> excluded) {
        Objects.requireNonNull(day, "day");
        WorldQueries world = queries.get();
        Set<ActorRef> employed = new HashSet<>();
        Map<ActorRef, Integer> staff = new HashMap<>();
        for (Contract contract : world.contracts().active()) {
            if (contract.kind() == ContractKind.EMPLOYMENT) {
                employed.add(contract.payee());
                staff.merge(contract.offeror(), 1, Integer::sum);
            }
        }
        List<PolityView> firms = world.companies().stream()
                .filter(firm -> world.production().enterpriseOf(firm.id()).isPresent())
                .sorted(Comparator.comparing(firm -> firm.id().value())).toList();
        int placed = 0;
        for (PolityView town : world.polities().stream().filter(polity -> polity.kind() == PolityKind.TERRITORIAL)
                .sorted(Comparator.comparing(polity -> polity.id().value())).toList()) {
            for (Membership membership : town.members()) {
                if (!(membership.member() instanceof PersonRef villager)
                        || employed.contains(villager) || excluded.test(villager)) {
                    continue;
                }
                Personality personality = Personality.of(villager);
                double chance = SEARCH_CHANCE * (1 + Math.max(0, -personality.community()) / 200.0);
                if (VillagerAmbitions.roll(villager, day, 0x90) >= chance) {
                    continue;
                }
                Optional<PolityView> best = bestFirm(world, firms, staff, town.id(), personality);
                if (best.isEmpty()) {
                    continue;
                }
                PolityView firm = best.get();
                try {
                    contracts.offerEmployment(firm.asActor(), villager,
                            Money.of(wageOf(personality), firm.currency()), 0, day);
                } catch (DomainException e) {
                    continue;
                }
                employed.add(villager);
                staff.merge(firm.asActor(), 1, Integer::sum);
                placed++;
            }
        }
        return placed;
    }

    private Optional<PolityView> bestFirm(WorldQueries world, List<PolityView> firms, Map<ActorRef, Integer> staff,
                                          PolityId home, Personality personality) {
        long wage = wageOf(personality);
        PolityView best = null;
        int bestScore = Integer.MIN_VALUE;
        for (PolityView firm : firms) {
            Enterprise enterprise = world.production().enterpriseOf(firm.id()).orElseThrow();
            int cap = VillagerBusiness.BASE_STAFF + 2 * enterprise.level();
            if (staff.getOrDefault(firm.asActor(), 0) >= cap
                    || world.ledger().balanceOf(firm.asActor(), firm.currency()).amount() < wage * 10) {
                continue;
            }
            int score = fit(enterprise.branch(), personality)
                    + (firm.officeTerm(Office.MAYOR).map(OfficeTerm::holder).flatMap(world::municipalityOf)
                    .map(PolityView::id).filter(home::equals).isPresent() ? HOME_BONUS : 0);
            if (score > bestScore) {
                best = firm;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }
}
