package de.etiennebader.civ.domain.protection;

/**
 * Wie ein Grundstücksinhaber eine Regel auf seinem Grundstück handhabt.
 */
public enum Stance {
    /** Es gilt das Recht von Gemeinde, Land und Nation. */
    INHERIT,
    /** Auf dem Grundstück erlaubt, auch wenn das Recht es verbietet. */
    ALLOW,
    /** Auf dem Grundstück verboten, auch wenn das Recht es erlaubt. */
    DENY;

    /** Das Ergebnis dieser Haltung, wenn das Recht {@code law} sagt. */
    public boolean resolve(boolean law) {
        return switch (this) {
            case INHERIT -> law;
            case ALLOW -> true;
            case DENY -> false;
        };
    }
}
