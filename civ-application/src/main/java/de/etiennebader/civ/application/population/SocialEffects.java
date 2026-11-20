package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Wie Spieler und Dorfbewohner sich gegenseitig in ihrer Stimmung beeinflussen (Issue #18): ein kleiner,
 * begrenzter Zuschlag auf {@link Satisfaction}, der täglich etwas abklingt.
 *
 * <p>Wie {@code departures}/{@code lastSeen} in {@code VillagerService} wird dieser Zuschlag nur im
 * Arbeitsspeicher gehalten, nicht in der Datenbank: Nach einem Neustart beginnt er wieder bei 0. Das ist für einen
 * vergänglichen Stimmungs-Nebeneffekt bewusst in Ordnung und spart eine Migration für einen rein kosmetischen Wert.
 */
public final class SocialEffects {

    /** Wie viele Tage zwischen zwei politischen Beeinflussungen desselben Spielers auf denselben Dorfbewohner liegen müssen. */
    public static final int INFLUENCE_COOLDOWN_DAYS = 3;
    private static final int INFLUENCE_POINTS = 6;
    private static final int INTERACTION_POINTS = 2;
    private static final int CAP = 20;

    private final Map<PersonRef, Integer> bonus = new HashMap<>();
    private final Map<String, Day> lastInfluence = new HashMap<>();

    /** Zuschlag aus Politik und Dorfbewohner-Kontakten; 0 ohne beides. */
    public int pointsOf(PersonRef villager) {
        return bonus.getOrDefault(Objects.requireNonNull(villager, "villager"), 0);
    }

    /**
     * Versucht, einen Dorfbewohner politisch zu beeinflussen.
     *
     * @return ob es gewirkt hat; {@code false}, wenn die Abklingzeit für dieses Spieler-Dorfbewohner-Paar noch
     *     nicht um ist
     */
    public boolean influence(PlayerRef player, PersonRef villager, Day today) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(today, "today");
        String key = key(player, villager);
        Day last = lastInfluence.get(key);
        if (last != null && today.value() - last.value() < INFLUENCE_COOLDOWN_DAYS) {
            return false;
        }
        lastInfluence.put(key, today);
        add(villager, INFLUENCE_POINTS);
        return true;
    }

    /**
     * Tagesende: Der Zuschlag klingt um einen Punkt in Richtung 0 ab, und je zwei Dorfbewohner derselben Gemeinde
     * tauschen sich aus und beeinflussen sich dabei gegenseitig leicht (deterministisch nach Tag und den beiden
     * Dorfbewohnern, damit nichts zusätzlich gespeichert werden muss).
     *
     * @param villagersByTown je Gemeinde ihre Dorfbewohner
     */
    public void endOfDay(Collection<List<PersonRef>> villagersByTown, Day today) {
        Objects.requireNonNull(villagersByTown, "villagersByTown");
        Objects.requireNonNull(today, "today");
        bonus.replaceAll((villager, points) -> points - Integer.signum(points));
        bonus.values().removeIf(points -> points == 0);
        for (List<PersonRef> villagers : villagersByTown) {
            interact(villagers, today);
        }
    }

    private void interact(List<PersonRef> villagers, Day today) {
        for (int i = 0; i + 1 < villagers.size(); i += 2) {
            PersonRef a = villagers.get(i);
            PersonRef b = villagers.get(i + 1);
            if (pairRoll(a, b, today)) {
                add(a, INTERACTION_POINTS);
                add(b, INTERACTION_POINTS);
            }
        }
    }

    /** Deterministisch: dasselbe Paar am selben Tag ergibt immer dasselbe Ergebnis. */
    private static boolean pairRoll(PersonRef a, PersonRef b, Day today) {
        long mix = a.uuid().getMostSignificantBits() ^ b.uuid().getLeastSignificantBits()
                ^ Long.rotateLeft(today.value() + 1, 17);
        return Math.floorMod(mix * 0x9E3779B97F4A7C15L, 100L) < 40;
    }

    private void add(PersonRef villager, int points) {
        int updated = Math.max(-CAP, Math.min(CAP, pointsOf(villager) + points));
        if (updated == 0) {
            bonus.remove(villager);
        } else {
            bonus.put(villager, updated);
        }
    }

    private static String key(PlayerRef player, PersonRef villager) {
        return player.uuid() + ":" + villager.uuid();
    }
}
