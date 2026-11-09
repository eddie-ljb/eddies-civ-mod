package de.etiennebader.civ.domain.protection;

import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.StandardParameters;

/**
 * Naturgewalten, gegen die das Gemeindegebiet geschützt ist, solange das Recht sie nicht erlaubt.
 */
public enum Hazard {
    /** Explosionen von Creepern, TNT, Betten im Nether usw. */
    EXPLOSION(StandardParameters.EXPLOSIONS_ALLOWED),
    /** Feuer, das Blöcke verbrennt. */
    FIRE(StandardParameters.FIRE_SPREAD_ALLOWED);

    private final ParameterKey<Boolean> permission;

    Hazard(ParameterKey<Boolean> permission) {
        this.permission = permission;
    }

    /** Die Regelung, die diese Naturgewalt im Gemeindegebiet erlaubt. */
    public ParameterKey<Boolean> permission() {
        return permission;
    }
}
