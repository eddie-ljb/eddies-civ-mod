package de.etiennebader.civ.domain.civic;

import java.util.Locale;
import java.util.Optional;

/**
 * Wer sich zu den Heimat-Häfen einer Polity porten darf. Die Polity entscheidet das selbst; die Mitglieder
 * ihrer eigenen Gemeinde bzw. Ebene dürfen immer, solange der Zugang nicht {@link #NOBODY} ist.
 */
public enum PortAccess {

    /** Niemand außer der Spitze der Polity selbst. */
    NOBODY,
    /** Nur Bürger der Polity (Mitglieder der Gemeinde bzw. Bürger der Gemeinden eines Bundes). */
    CITIZENS,
    /** Bürger und Bürger verbündeter Polities. */
    CITIZENS_AND_ALLIES,
    /** Jeder Spieler. */
    EVERYONE;

    /** Kurzname für Befehle und Anzeige, klein geschrieben. */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<PortAccess> parse(String input) {
        String wanted = input.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (wanted) {
            case "keiner", "niemand", "nobody", "none" -> Optional.of(NOBODY);
            case "buerger", "bürger", "citizens", "citizen" -> Optional.of(CITIZENS);
            case "verbuendete", "verbündete", "allies", "buerger-verbuendete", "citizens-and-allies" ->
                    Optional.of(CITIZENS_AND_ALLIES);
            case "alle", "jeder", "everyone", "all" -> Optional.of(EVERYONE);
            default -> Optional.empty();
        };
    }
}
