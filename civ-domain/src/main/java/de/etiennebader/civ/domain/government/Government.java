package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regierung einer politischen Ebene: Oberhaupt, Ministerien, Haushalt. Die Regeln (Zahl der Ministerien,
 * Haushaltsrhythmus, Mehrheit, vorbereitendes Ministerium) sind die „Verfassung“ dieses Teils.
 */
public final class Government {

    public static final int DEFAULT_MAX_MINISTRIES = 12;
    public static final int DEFAULT_BUDGET_INTERVAL = 7;
    public static final int DEFAULT_APPROVAL_PERCENT = 50;
    public static final String DEFAULT_PREPARER = "finanzministerium";
    /** Vorschläge für Ministerien (Name, Zuständigkeit). */
    public static final List<String> SUGGESTIONS = List.of("Kanzleramt", "Finanzministerium", "Innenministerium",
            "Außenministerium", "Verteidigungsministerium", "Wirtschaftsministerium", "Bildungsministerium",
            "Forschungsministerium", "Landwirtschaftsministerium", "Minenministerium");

    /** Gesamter Zustand zum Speichern und Wiederherstellen. */
    public record Snapshot(HeadOfState head, List<Ministry> ministries, int maxMinistries, int budgetInterval,
                           int approvalPercent, String preparer, Budget adopted, Optional<Budget> draft,
                           Map<ActorRef, Boolean> votes, Optional<Day> lastBudgetDay) {
    }

    /** Ein fälliger Lohn. */
    public record Wage(String ministry, ActorRef employee, long amount) {
    }

    private HeadOfState head = HeadOfState.DEFAULT;
    private final Map<String, Ministry> ministries = new LinkedHashMap<>();
    private int maxMinistries = DEFAULT_MAX_MINISTRIES;
    private int budgetInterval = DEFAULT_BUDGET_INTERVAL;
    private int approvalPercent = DEFAULT_APPROVAL_PERCENT;
    private String preparer = DEFAULT_PREPARER;
    private Budget adopted = Budget.EMPTY;
    private Optional<Budget> draft = Optional.empty();
    private final Map<ActorRef, Boolean> votes = new LinkedHashMap<>();
    private Optional<Day> lastBudgetDay = Optional.empty();

    public static Government restore(Snapshot s) {
        Government g = new Government();
        g.head = s.head();
        s.ministries().forEach(m -> g.ministries.put(m.key(), m));
        g.maxMinistries = s.maxMinistries();
        g.budgetInterval = s.budgetInterval();
        g.approvalPercent = s.approvalPercent();
        g.preparer = s.preparer();
        g.adopted = s.adopted();
        g.draft = s.draft();
        g.votes.putAll(s.votes());
        g.lastBudgetDay = s.lastBudgetDay();
        return g;
    }

    public Snapshot snapshot() {
        return new Snapshot(head, ministries(), maxMinistries, budgetInterval, approvalPercent, preparer, adopted,
                draft, Map.copyOf(votes), lastBudgetDay);
    }

    // ---------------------------------------------------------------- Oberhaupt

    public HeadOfState head() {
        return head;
    }

    public void configureHead(HeadOfState newHead) {
        this.head = java.util.Objects.requireNonNull(newHead, "head");
    }

    // ---------------------------------------------------------------- Ministerien

    public List<Ministry> ministries() {
        return List.copyOf(ministries.values());
    }

    public Optional<Ministry> ministry(String keyOrName) {
        return Optional.ofNullable(ministries.get(Ministry.keyOf(keyOrName)));
    }

    public Ministry require(String keyOrName) {
        return ministry(keyOrName).orElseThrow(() -> new GovernmentException(ErrorCode.UNKNOWN_MINISTRY, keyOrName));
    }

    public Ministry create(String name, String mandate, Selection selection) {
        Ministry ministry = Ministry.create(name, mandate, selection);
        if (ministries.containsKey(ministry.key())) {
            throw new GovernmentException(ErrorCode.MINISTRY_EXISTS, name);
        }
        if (ministries.size() >= maxMinistries) {
            throw new GovernmentException(ErrorCode.TOO_MANY_MINISTRIES, maxMinistries);
        }
        ministries.put(ministry.key(), ministry);
        return ministry;
    }

    public void dissolve(String keyOrName) {
        Ministry ministry = require(keyOrName);
        ministries.remove(ministry.key());
        if (preparer.equals(ministry.key())) {
            preparer = DEFAULT_PREPARER;
        }
    }

    public Ministry update(String keyOrName, java.util.function.UnaryOperator<Ministry> change) {
        Ministry current = require(keyOrName);
        Ministry next = change.apply(current);
        ministries.put(next.key(), next);
        return next;
    }

    /** Setzt den Minister; ein Angestellter dieses Ministeriums rückt aus der Belegschaft auf. */
    public Ministry appoint(String keyOrName, ActorRef minister) {
        Ministry current = require(keyOrName);
        if (current.staff().contains(minister)) {
            current = current.fire(minister);
        } else if (current.employs(minister)) {
            throw new GovernmentException(ErrorCode.ALREADY_EMPLOYED, current.name());
        }
        Ministry next = current.withMinister(Optional.of(minister));
        ministries.put(next.key(), next);
        return next;
    }

    public Ministry dismissMinister(String keyOrName) {
        Ministry current = require(keyOrName);
        if (current.minister().isEmpty()) {
            throw new GovernmentException(ErrorCode.NOT_EMPLOYED, current.name());
        }
        Ministry next = current.withMinister(Optional.empty());
        ministries.put(next.key(), next);
        return next;
    }

    /** Das Ministerium, in dem der Akteur Minister ist. */
    public Optional<Ministry> ministryOf(ActorRef minister) {
        return ministries.values().stream().filter(m -> m.minister().filter(minister::equals).isPresent()).findFirst();
    }

    public boolean isMinister(ActorRef actor, String keyOrName) {
        return ministry(keyOrName).flatMap(Ministry::minister).filter(actor::equals).isPresent();
    }

    // ---------------------------------------------------------------- Regeln

    public int maxMinistries() {
        return maxMinistries;
    }

    public int budgetInterval() {
        return budgetInterval;
    }

    public int approvalPercent() {
        return approvalPercent;
    }

    public String preparer() {
        return preparer;
    }

    public void configureRules(int newMax, int newInterval, int newApprovalPercent) {
        if (newMax < 1 || newMax > 30 || newInterval < 1 || newInterval > 60 || newApprovalPercent < 1
                || newApprovalPercent > 99 || newMax < ministries.size()) {
            throw new GovernmentException(ErrorCode.INVALID_GOVERNMENT_VALUE, newMax);
        }
        this.maxMinistries = newMax;
        this.budgetInterval = newInterval;
        this.approvalPercent = newApprovalPercent;
    }

    public void configurePreparer(String keyOrName) {
        this.preparer = require(keyOrName).key();
    }

    // ---------------------------------------------------------------- Haushalt

    public Budget adopted() {
        return adopted;
    }

    public Optional<Budget> draft() {
        return draft;
    }

    public Map<ActorRef, Boolean> votes() {
        return Map.copyOf(votes);
    }

    public Optional<Day> lastBudgetDay() {
        return lastBudgetDay;
    }

    /** Ob am {@code day} ein neuer Haushalt beraten werden muss. */
    public boolean budgetDue(Day day) {
        return lastBudgetDay.map(last -> day.value() - last.value() >= budgetInterval).orElse(true);
    }

    /** Setzt den Etat eines Ministeriums im Entwurf (legt den Entwurf an, falls es noch keinen gibt). */
    public Budget draftAllocation(String keyOrName, long amount) {
        String key = require(keyOrName).key();
        Budget next = draft.orElse(Budget.EMPTY).with(key, amount);
        draft = Optional.of(next);
        votes.clear();
        return next;
    }

    public void vote(ActorRef voter, boolean inFavour) {
        if (draft.isEmpty()) {
            throw new GovernmentException(ErrorCode.NO_BUDGET_DRAFT);
        }
        votes.put(voter, inFavour);
    }

    /**
     * Schließt die Abstimmung. Angenommen, wenn mehr als {@code approvalPercent} Prozent der Stimmberechtigten
     * zugestimmt haben und der Entwurf die verfügbaren Mittel nicht übersteigt. Dann erhält jedes Ministerium
     * seinen Etat für die neue Periode.
     *
     * @return ob der Haushalt angenommen wurde
     */
    public boolean closeBudget(int eligible, long available, Day day) {
        Budget proposal = draft.orElseThrow(() -> new GovernmentException(ErrorCode.NO_BUDGET_DRAFT));
        if (proposal.total() > available) {
            throw new GovernmentException(ErrorCode.BUDGET_EXCEEDS_FUNDS, proposal.total(), available);
        }
        long yes = votes.values().stream().filter(Boolean::booleanValue).count();
        boolean accepted = eligible > 0 && yes * 100 > (long) approvalPercent * eligible;
        draft = Optional.empty();
        votes.clear();
        if (accepted) {
            adopted = proposal.adopted(day);
            lastBudgetDay = Optional.of(day);
            for (Ministry ministry : ministries()) {
                ministries.put(ministry.key(), ministry.withEtat(adopted.allocationOf(ministry.key())));
            }
        }
        return accepted;
    }

    // ---------------------------------------------------------------- Löhne

    /** Alle heute fälligen Löhne, je Ministerium begrenzt auf den verbleibenden Etat (Minister zuerst). */
    public List<Wage> dueWages() {
        List<Wage> wages = new ArrayList<>();
        for (Ministry ministry : ministries.values()) {
            long left = ministry.etat();
            if (ministry.minister().isPresent() && ministry.ministerWage() > 0 && left >= ministry.ministerWage()) {
                wages.add(new Wage(ministry.key(), ministry.minister().get(), ministry.ministerWage()));
                left -= ministry.ministerWage();
            }
            for (ActorRef employee : ministry.staff()) {
                if (ministry.staffWage() > 0 && left >= ministry.staffWage()) {
                    wages.add(new Wage(ministry.key(), employee, ministry.staffWage()));
                    left -= ministry.staffWage();
                }
            }
        }
        return wages;
    }

    /** Bucht einen gezahlten Betrag vom Etat ab. */
    public void spend(String ministryKey, long amount) {
        Ministry ministry = require(ministryKey);
        if (amount < 0 || amount > ministry.etat()) {
            throw new GovernmentException(ErrorCode.ETAT_EXHAUSTED, ministry.name());
        }
        ministries.put(ministry.key(), ministry.withEtat(ministry.etat() - amount));
    }
}
