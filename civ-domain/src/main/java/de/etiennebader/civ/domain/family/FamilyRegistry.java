package de.etiennebader.civ.domain.family;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PersonId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Wer mit wem verheiratet ist, wie gut sich Paare verstehen und wessen Kind wer ist. Zustand ohne Zufall und ohne
 * Regeln über die Wahrscheinlichkeit: die stecken in {@link Family}.
 */
public final class FamilyRegistry {

    /** Ein Ehepaar; {@code first} ist die Person mit der kleineren Kennung. */
    public record Marriage(PersonRef first, PersonRef second, Day since, Day lastBirth, int children) {

        public Marriage {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            Objects.requireNonNull(since, "since");
            Objects.requireNonNull(lastBirth, "lastBirth");
        }

        public boolean involves(PersonRef person) {
            return first.equals(person) || second.equals(person);
        }

        public PersonRef partnerOf(PersonRef person) {
            return first.equals(person) ? second : first;
        }
    }

    /** Ein Kind mit seinen Eltern; erwachsen ist es, sobald das Spiel es als Dorfbewohner meldet. */
    public record Child(PersonId id, PersonRef parent1, PersonRef parent2, Day born) {

        public Child {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(parent1, "parent1");
            Objects.requireNonNull(parent2, "parent2");
            Objects.requireNonNull(born, "born");
        }

        public PersonRef ref() {
            return new PersonRef(id);
        }
    }

    /** Ein beendetes Paar, für die Chronik. */
    public record Divorce(PersonRef first, PersonRef second, Day married, Day divorced) {
    }

    /** Alles zum Speichern und Wiederherstellen. */
    public record Snapshot(List<Marriage> marriages, Map<String, Integer> bonds, List<Child> children,
                           List<Divorce> divorces) {
    }

    private final Map<UUID, Marriage> marriageOf = new LinkedHashMap<>();
    private final Map<String, Integer> bonds = new TreeMap<>();
    private final List<Child> children = new ArrayList<>();
    private final List<Divorce> divorces = new ArrayList<>();

    public static FamilyRegistry empty() {
        return new FamilyRegistry();
    }

    public static FamilyRegistry restore(Snapshot snapshot) {
        FamilyRegistry registry = new FamilyRegistry();
        for (Marriage marriage : snapshot.marriages()) {
            registry.marriageOf.put(marriage.first().uuid(), marriage);
            registry.marriageOf.put(marriage.second().uuid(), marriage);
        }
        registry.bonds.putAll(snapshot.bonds());
        registry.children.addAll(snapshot.children());
        registry.divorces.addAll(snapshot.divorces());
        return registry;
    }

    public Snapshot snapshot() {
        return new Snapshot(marriages(), Map.copyOf(bonds), List.copyOf(children), List.copyOf(divorces));
    }

    // ------------------------------------------------------------------ Abfragen

    public Optional<Marriage> marriageOf(PersonRef person) {
        return Optional.ofNullable(marriageOf.get(person.uuid()));
    }

    public Optional<PersonRef> spouseOf(PersonRef person) {
        return marriageOf(person).map(marriage -> marriage.partnerOf(person));
    }

    public boolean isMarried(PersonRef person) {
        return marriageOf.containsKey(person.uuid());
    }

    public List<Marriage> marriages() {
        return marriageOf.values().stream().distinct().toList();
    }

    public List<Child> children() {
        return List.copyOf(children);
    }

    public List<Child> childrenOf(PersonRef parent) {
        return children.stream().filter(child -> child.parent1().equals(parent) || child.parent2().equals(parent))
                .toList();
    }

    public Optional<Child> child(PersonRef person) {
        return children.stream().filter(child -> child.id().equals(person.id())).findFirst();
    }

    public List<Divorce> divorces() {
        return List.copyOf(divorces);
    }

    /** Beziehung (0–100) zweier Personen, 0 wenn sie sich noch nicht näher kennen. */
    public int bond(PersonRef first, PersonRef second) {
        return bonds.getOrDefault(key(first, second), 0);
    }

    // ------------------------------------------------------------------ Änderungen

    /** Verändert die Beziehung um {@code delta}, begrenzt auf 0–100; gibt den neuen Wert zurück. */
    public int changeBond(PersonRef first, PersonRef second, int delta) {
        int value = Math.max(0, Math.min(100, bond(first, second) + delta));
        if (value == 0) {
            bonds.remove(key(first, second));
        } else {
            bonds.put(key(first, second), value);
        }
        return value;
    }

    /** Heiratet zwei ledige, verschiedene Personen. */
    public Marriage marry(PersonRef first, PersonRef second, Day day) {
        if (first.equals(second)) {
            throw new IllegalArgumentException("Niemand heiratet sich selbst");
        }
        if (isMarried(first) || isMarried(second)) {
            throw new IllegalStateException("Schon verheiratet");
        }
        boolean order = first.uuid().compareTo(second.uuid()) <= 0;
        Marriage marriage = new Marriage(order ? first : second, order ? second : first, day, day, 0);
        marriageOf.put(first.uuid(), marriage);
        marriageOf.put(second.uuid(), marriage);
        return marriage;
    }

    /** Scheidet die Ehe von {@code person}. */
    public Divorce divorce(PersonRef person, Day day) {
        Marriage marriage = marriageOf(person).orElseThrow(() -> new IllegalStateException("Nicht verheiratet"));
        marriageOf.remove(marriage.first().uuid());
        marriageOf.remove(marriage.second().uuid());
        Divorce divorce = new Divorce(marriage.first(), marriage.second(), marriage.since(), day);
        divorces.add(divorce);
        return divorce;
    }

    /** Ein Kind des Ehepaares kommt zur Welt. */
    public Child birth(PersonRef person, Day day) {
        Marriage marriage = marriageOf(person).orElseThrow(() -> new IllegalStateException("Nicht verheiratet"));
        Child child = new Child(Family.childId(marriage.first(), marriage.second(), marriage.children()),
                marriage.first(), marriage.second(), day);
        Marriage updated = new Marriage(marriage.first(), marriage.second(), marriage.since(), day,
                marriage.children() + 1);
        marriageOf.put(marriage.first().uuid(), updated);
        marriageOf.put(marriage.second().uuid(), updated);
        children.add(child);
        return child;
    }

    /** Eine Person ist gestorben oder fortgezogen: die Ehe endet (Witwenstand), die Beziehung verfällt. */
    public void gone(PersonRef person, Day day) {
        if (isMarried(person)) {
            divorce(person, day);
            divorces.remove(divorces.size() - 1);
        }
        bonds.keySet().removeIf(key -> key.contains(person.uuid().toString()));
    }

    private static String key(PersonRef first, PersonRef second) {
        return first.uuid().compareTo(second.uuid()) <= 0 ? first.uuid() + "|" + second.uuid()
                : second.uuid() + "|" + first.uuid();
    }
}
