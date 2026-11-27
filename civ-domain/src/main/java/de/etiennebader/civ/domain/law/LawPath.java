package de.etiennebader.civ.domain.law;

import java.util.Objects;

/**
 * Die Schreibweise {@code Gesetz > Teil > Paragraph} für Titelfelder (Befehl, Buch, Menü, Web). Mit zwei Namen ist
 * der erste das Gesetz und der zweite der Paragraph; mit drei Namen kommt der Teil in die Mitte.
 *
 * @param lawName  leer ohne Gesetz
 * @param partName leer ohne Teil
 */
public record LawPath(String lawName, String partName, String title) {

    public static final String SEPARATOR = ">";

    public LawPath {
        Objects.requireNonNull(lawName, "lawName");
        Objects.requireNonNull(partName, "partName");
        Objects.requireNonNull(title, "title");
    }

    public static LawPath parse(String input) {
        String[] parts = Objects.requireNonNull(input, "input").split(SEPARATOR, -1);
        return switch (parts.length) {
            case 1 -> new LawPath("", "", input.strip());
            case 2 -> new LawPath(parts[0].strip(), "", parts[1].strip());
            case 3 -> new LawPath(parts[0].strip(), parts[1].strip(), parts[2].strip());
            // Weitere Trenner gehören zum Titel: so geht kein Text verloren.
            default -> new LawPath(parts[0].strip(), parts[1].strip(),
                    input.substring(input.indexOf(SEPARATOR, input.indexOf(SEPARATOR) + 1) + 1).strip());
        };
    }
}
