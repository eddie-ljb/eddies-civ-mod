package de.etiennebader.civ.domain.protection;

import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.StandardParameters;

/**
 * Regeln, die ein Gesetz für das ganze Gebiet festlegt und die ein Grundstücksinhaber auf seinem Grundstück
 * abweichend einstellen darf, sofern das Gesetz {@link StandardParameters#PLOT_RIGHTS_OVERRIDE} es erlaubt.
 */
public enum PlotFlag {
    /** Ob Spieler einander verletzen dürfen. */
    PVP(StandardParameters.PVP_ALLOWED),
    /** Ob feindliche Monster dort leben dürfen. */
    HOSTILE_MOBS(StandardParameters.HOSTILE_MOBS_ALLOWED),
    /** Ob Explosionen Blöcke zerstören. */
    EXPLOSIONS(StandardParameters.EXPLOSIONS_ALLOWED),
    /** Ob Feuer Blöcke verbrennt. */
    FIRE_SPREAD(StandardParameters.FIRE_SPREAD_ALLOWED),
    /** Ob Nicht-Mitglieder der Gemeinde Tiere verletzen dürfen. */
    OUTSIDERS_HURT_ANIMALS(StandardParameters.OUTSIDERS_MAY_HURT_ANIMALS);

    private final ParameterKey<Boolean> law;

    PlotFlag(ParameterKey<Boolean> law) {
        this.law = law;
    }

    /** Das Gesetz, das diese Regel im Gebiet festlegt. */
    public ParameterKey<Boolean> law() {
        return law;
    }

    /** Die Regel zu einer Naturgewalt. */
    public static PlotFlag of(Hazard hazard) {
        return switch (hazard) {
            case EXPLOSION -> EXPLOSIONS;
            case FIRE -> FIRE_SPREAD;
        };
    }
}
