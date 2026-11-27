package de.etiennebader.civ.domain.police;

/** Art einer Straftat mit Schwere und üblicher Haftdauer in Tagen. */
public enum CrimeKind {
    THEFT(1, 1),
    TRESPASS(1, 1),
    ASSAULT(2, 3),
    MURDER(4, 10);

    private final int severity;
    private final int detentionDays;

    CrimeKind(int severity, int detentionDays) {
        this.severity = severity;
        this.detentionDays = detentionDays;
    }

    public int severity() {
        return severity;
    }

    public int detentionDays() {
        return detentionDays;
    }
}
