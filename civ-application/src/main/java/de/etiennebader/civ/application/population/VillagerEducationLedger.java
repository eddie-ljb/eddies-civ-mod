package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.PersonRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Hält {@link VillagerEducation} je jungem Dorfbewohner (Issue #21), wie {@link SocialEffects} und
 * {@link VillagerMailbox} nur im Speicher: Bildung und Intelligenz sind ein Nebenaspekt des Verhaltens, kein
 * zentraler Spielstand, der einen Server-Neustart überdauern müsste.
 */
public final class VillagerEducationLedger {

    /** 0-100, neutral, für Dorfbewohner ohne (mehr) verfolgten Bildungsstand, z. B. Erwachsene ohne Kindheitsdaten. */
    static final int NEUTRAL_BONUS = 50;

    private final Map<PersonRef, VillagerEducation> state = new HashMap<>();

    /** Bildung und Intelligenz eines Dorfbewohners; legt bei erstem Zugriff den Anfangszustand an. */
    public VillagerEducation of(PersonRef villager) {
        Objects.requireNonNull(villager, "villager");
        return state.computeIfAbsent(villager, VillagerEducation::initial);
    }

    /** Ein Tag im Kindesalter: {@code attendedSchool}, ob der Dorfbewohner an diesem Tag zur Schule gegangen ist. */
    public void recordChildDay(PersonRef villager, boolean attendedSchool) {
        state.put(villager, of(villager).afterChildDay(attendedSchool));
    }

    /** Der Dorfbewohner ist erwachsen geworden: sein Bildungsstand friert ein. */
    public void grownUp(PersonRef villager) {
        state.computeIfPresent(villager, (ignored, education) -> education.grownUp());
    }

    /**
     * Gemeinsames Maß aus Bildung und Intelligenz (0-100) zur Gewichtung von Wahl- und politischem Verhalten;
     * {@value #NEUTRAL_BONUS} (neutral), solange für den Dorfbewohner noch kein Bildungsstand verfolgt wird.
     */
    public int bonusOf(PersonRef villager) {
        VillagerEducation education = state.get(villager);
        return education == null ? NEUTRAL_BONUS : education.votingBonus();
    }
}
