package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;
import java.util.Optional;

/**
 * Wie loyal ein Dorfbewohner gegenüber einer politischen Ebene, seiner Partei, deren Vorsitz oder seinem Arbeitgeber
 * ist, von -100 bis 100.
 *
 * <p>Anders als die {@linkplain Personality Persönlichkeit} ist Loyalität kein fester Wert, sondern wird aus der
 * Mitgliedsdauer (bzw. Amts- oder Anstellungsdauer) und der seitherigen Stimmung berechnet: Neue Mitglieder haben nur
 * ihre angeborene Heimatverbundenheit als Loyalität, mit wachsender Dauer nähert sie sich an, wie zufrieden sie in
 * dieser Zeit war oder ist. Dauerhaft zufriedene Dorfbewohner werden so loyaler, dauerhaft unzufriedene weniger loyal
 * oder sogar illoyal — unabhängig von der tagesaktuellen Zufriedenheit allein. Da sich alles deterministisch aus
 * ohnehin gespeicherten Daten (Mitglied seit, Amt seit) ergibt, muss nichts zusätzlich gespeichert werden.
 */
public final class VillagerLoyalty {

    /** Nach so vielen Tagen zählt die seitherige Stimmung voll statt nur anteilig. */
    static final int FULL_WEIGHT_DAYS = 60;
    /** Wie stark die seitherige Stimmung die angeborene Heimatverbundenheit überschreiben kann. */
    static final double DRIFT_SHARE = 0.6;

    private VillagerLoyalty() {
    }

    /**
     * Die Kernformel: angeborene Heimatverbundenheit als Basis, mit der Mitgliedsdauer wachsend überlagert von der
     * seitherigen Stimmung.
     *
     * @param belonging  Heimatverbundenheit der Persönlichkeit (-100 bis 100)
     * @param tenureDays Tage der Mitgliedschaft, Amtszeit oder Anstellung (negativ wird wie 0 behandelt)
     * @param moodScore  Zufriedenheit im jeweiligen Kontext (-100 bis 100)
     * @return Loyalität, -100 bis 100
     */
    public static int derive(int belonging, int tenureDays, int moodScore) {
        int base = belonging / 2;
        double weight = Math.min(1.0, Math.max(0, tenureDays) / (double) FULL_WEIGHT_DAYS);
        int drifted = (int) Math.round(moodScore * weight * DRIFT_SHARE);
        return clamp(base + drifted);
    }

    /** Loyalität zu einer politischen Ebene (Gemeinde oder ein Bund darüber), aus Mitgliedsdauer und Zufriedenheit. */
    public static int toPolity(Personality personality, PolityView polity, ActorRef member, Day today, LawView laws) {
        Objects.requireNonNull(personality, "personality");
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(laws, "laws");
        int tenure = tenureDays(polity, member, today);
        int mood = Satisfaction.of(personality, polity, laws).score();
        return derive(personality.belonging(), tenure, mood);
    }

    /** Loyalität zur eigenen Partei, aus Mitgliedsdauer und Nähe zur Parteirichtung statt Zufriedenheit. */
    public static int toParty(Personality personality, PolityView party, ActorRef member, Day today) {
        Objects.requireNonNull(personality, "personality");
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(today, "today");
        int tenure = tenureDays(party, member, today);
        return derive(personality.belonging(), tenure, partyMood(personality, party));
    }

    /**
     * Loyalität zum aktuellen Parteivorsitzenden bzw. Bürgermeister/Vorsitz eines Bundes: an dessen Amtszeit
     * gekoppelt statt an die eigene Mitgliedschaft. 0, solange das Amt unbesetzt ist.
     */
    public static int toChairperson(Personality personality, PolityView polity, Day today) {
        Objects.requireNonNull(personality, "personality");
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(today, "today");
        return polity.officeTerm(Office.MAYOR)
                .map(term -> derive(personality.belonging(),
                        (int) Math.max(0L, today.value() - term.since().value()),
                        partyMood(personality, polity)))
                .orElse(0);
    }

    /**
     * Loyalität zum Arbeitgeber (Unternehmen), aus Anstellungsdauer. Ohne eigenes Zufriedenheitsmodell je Unternehmen
     * fließt nur die angeborene Heimatverbundenheit gewichtet mit der Dauer ein; 0 ohne Anstellung.
     */
    public static int toEmployer(Personality personality, int employmentDays) {
        Objects.requireNonNull(personality, "personality");
        return derive(personality.belonging(), employmentDays, 0);
    }

    /** Wie nahe die Persönlichkeit der Parteirichtung ist, als Stimmungs-Ersatz: -100 (fern) bis 100 (deckungsgleich). */
    private static int partyMood(Personality personality, PolityView party) {
        Optional<Integer> distance = party.partyProfile()
                .map(profile -> (int) Math.round(profile.distanceTo(personality.order(), -personality.thrift())));
        return distance.map(d -> 100 - 2 * Math.min(100, d)).orElse(0);
    }

    private static int tenureDays(PolityView polity, ActorRef member, Day today) {
        return polity.membershipOf(member)
                .map(membership -> (int) Math.max(0L, today.value() - membership.since().value()))
                .orElse(0);
    }

    private static int clamp(int value) {
        return Math.max(-100, Math.min(100, value));
    }
}
