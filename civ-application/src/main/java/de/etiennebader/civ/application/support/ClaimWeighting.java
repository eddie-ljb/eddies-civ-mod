package de.etiennebader.civ.application.support;

import de.etiennebader.civ.domain.polity.PolityView;

/**
 * Wie die Mitglieder einer Polity für die Größe ihres Gebiets zählen.
 */
public enum ClaimWeighting {
    /** Jedes Mitglied zählt voll, auch Personen (z. B. in der Simulation). */
    ALL_MEMBERS {
        @Override
        public int weightOf(PolityView polity) {
            return polity.memberCount();
        }
    },
    /**
     * Personen wie Dorfbewohner zählen nur zu einem Viertel, siehe {@link PolityView#claimWeight()}. Dazu kommen
     * je Siedlungsstufe über der Gemeinde {@value #TIER_BONUS} Mitglieder als Bonus.
     */
    PERSONS_QUARTER {
        @Override
        public int weightOf(PolityView polity) {
            return polity.claimWeight() + TIER_BONUS * polity.tier().ordinal();
        }
    };

    /** Zusätzliches Gewicht je Siedlungsstufe über der Gemeinde. */
    public static final int TIER_BONUS = 2;

    public abstract int weightOf(PolityView polity);
}
