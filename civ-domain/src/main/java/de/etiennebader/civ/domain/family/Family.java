package de.etiennebader.civ.domain.family;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.PersonId;

import java.util.Objects;
import java.util.UUID;

/**
 * Regeln für Heirat, Scheidung und Kinder von Dorfbewohnern (Issue #27, Nachtrag). Alles ist reine Berechnung aus
 * Persönlichkeit, Beziehung und Bildung; der Zufall kommt von außen, damit Tests deterministisch bleiben.
 */
public final class Family {

    /** Ab dieser Verträglichkeit (0–100) wächst die Beziehung eines Paares, darunter schwindet sie. */
    public static final int GOOD_MATCH = 45;
    /** Beziehungswert (0–100), ab dem ein unverheiratetes Paar heiratet. */
    public static final int MARRY_BOND = 20;
    /** Beziehungswert, ab dem ein Ehepaar Kinder bekommt. */
    public static final int CHILD_BOND = 40;
    /** Mindestabstand zwischen zwei Geburten in Tagen. */
    public static final int BIRTH_GAP_DAYS = 10;
    /** Höchstzahl gemeinsamer Kinder eines Paares. */
    public static final int MAX_CHILDREN = 4;

    private Family() {
    }

    /** Verträglichkeit zweier Dorfbewohner von 0 (gegensätzlich) bis 100 (sehr ähnlich). */
    public static int compatibility(PersonRef first, PersonRef second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Personality a = Personality.of(first);
        Personality b = Personality.of(second);
        int distance = Math.abs(a.order() - b.order()) + Math.abs(a.thrift() - b.thrift())
                + Math.abs(a.belonging() - b.belonging()) + Math.abs(a.community() - b.community())
                + Math.abs(a.nature() - b.nature());
        // Höchstabstand je Achse 200, fünf Achsen; Ähnlichkeit gegenüber dem Durchschnitt bei Zufallspaaren (~100/200).
        int similarity = 100 - distance / 10;
        return Math.max(0, Math.min(100, similarity));
    }

    /**
     * Bildung von 0 bis 100: stabiler Grundwert aus der Identität, dazu je volle 30 Tage Aufenthalt im Dorf ein Punkt
     * (bis 20). Schulen und Bücher gibt es noch nicht; so wächst Bildung mit der Lebenserfahrung.
     */
    public static int education(PersonRef person, long residentDays) {
        UUID id = person.uuid();
        long mixed = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ Long.rotateLeft(id.getLeastSignificantBits(), 13);
        mixed ^= mixed >>> 31;
        int base = (int) Math.floorMod(mixed, 81L);
        return Math.min(100, base + (int) Math.min(20, Math.max(0, residentDays) / 30));
    }

    /** Wie viel Beziehung ein Paar an einem Tag gewinnt (positiv) oder verliert (negativ). */
    public static int dailyBondChange(int compatibility, boolean married) {
        if (compatibility >= GOOD_MATCH) {
            return 1 + (compatibility - GOOD_MATCH) / 25 + (married ? 0 : 1);
        }
        return -(1 + (GOOD_MATCH - compatibility) / 20);
    }

    /** Wahrscheinlichkeit in Promille, dass ein unglückliches Paar sich an einem Tag scheiden lässt. */
    public static int divorceChancePerMille(int bond, int compatibility, int educationA, int educationB) {
        if (bond > 10 || compatibility >= GOOD_MATCH) {
            return 0;
        }
        int base = 40 + (GOOD_MATCH - compatibility);
        int educated = (educationA + educationB) / 2;
        // Gebildete reden miteinander: seltener Scheidung; Bindung bei Paaren mit Kindern ist größer (außen geprüft).
        return Math.max(5, base - educated / 4);
    }

    /** Wunschzahl an Kindern des Paares (0–{@value #MAX_CHILDREN}): Gemeinsinn und Heimat dafür, Bildung dagegen. */
    public static int desiredChildren(PersonRef first, PersonRef second, int educationA, int educationB) {
        Personality a = Personality.of(first);
        Personality b = Personality.of(second);
        int warmth = (a.community() + b.community() + a.belonging() + b.belonging()) / 4;
        int educated = (educationA + educationB) / 2;
        int wish = 2 + warmth / 40 - educated / 50;
        return Math.max(0, Math.min(MAX_CHILDREN, wish));
    }

    /** Wahrscheinlichkeit in Promille für eine Geburt an einem Tag, wenn das Paar noch Kinder möchte. */
    public static int birthChancePerMille(int bond) {
        return bond < CHILD_BOND ? 0 : 30 + (bond - CHILD_BOND) / 2;
    }

    /** Stabile Kennung eines Kindes aus den Eltern und seiner Nummer, damit Neustarts nichts verschieben. */
    public static PersonId childId(PersonRef first, PersonRef second, int number) {
        PersonRef low = first.uuid().compareTo(second.uuid()) <= 0 ? first : second;
        PersonRef high = low == first ? second : first;
        return new PersonId(UUID.nameUUIDFromBytes((low.uuid() + "|" + high.uuid() + "|" + number)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
