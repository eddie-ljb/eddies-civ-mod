package de.etiennebader.civ.domain.civic;

import java.util.Objects;

/**
 * Wie eine Polity ihre Heimat-Häfen öffnet: wer sich dorthin porten darf und was es kostet.
 *
 * @param access Zugangsregel
 * @param fee    Preis je Teleport in der Währung der Polity (0 = kostenlos); Bürger zahlen ihn an die Polity wie alle
 *               anderen auch
 */
public record PortSettings(PortAccess access, long fee) {

    /** Obergrenze, damit ein Tippfehler niemanden ruiniert. */
    public static final long MAX_FEE = 1_000_000L;

    public static final PortSettings DEFAULT = new PortSettings(PortAccess.CITIZENS, 0);

    public PortSettings {
        Objects.requireNonNull(access, "access");
        if (fee < 0 || fee > MAX_FEE) {
            throw new IllegalArgumentException("Der Preis muss zwischen 0 und " + MAX_FEE + " liegen");
        }
    }

    public PortSettings withAccess(PortAccess newAccess) {
        return new PortSettings(newAccess, fee);
    }

    public PortSettings withFee(long newFee) {
        return new PortSettings(access, newFee);
    }
}
