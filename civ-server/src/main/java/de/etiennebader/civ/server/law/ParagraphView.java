package de.etiennebader.civ.server.law;

import de.etiennebader.civ.domain.law.LawText;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.server.town.LawSetting;

import java.util.List;
import java.util.Objects;

/**
 * Ein Paragraph, wie Menü und Chat ihn zeigen.
 *
 * @param number       laufende Nummer im Gesetzbuch, ab 1
 * @param effects      Regelungen, die er setzt
 * @param lawName      Name des Gesetzes, leer ohne Zuordnung
 * @param partName     Name des Teils im Gesetz, leer ohne Teil
 * @param unrecognized Teile des Texts, die das Spiel nicht selbst durchsetzt
 * @param sections     Absätze mit Sätzen und Aufzählungen
 */
public record ParagraphView(int number, StatuteId id, String lawName, String partName, String title, String text,
                            long enactedOn, List<LawSetting> effects, List<String> unrecognized,
                            List<LawText.Section> sections) {

    public ParagraphView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lawName, "lawName");
        Objects.requireNonNull(partName, "partName");
        sections = List.copyOf(sections);
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(text, "text");
        effects = List.copyOf(effects);
        unrecognized = List.copyOf(unrecognized);
    }

    /** Titel mit Gesetz und Teil davor, soweit vorhanden: {@code Gesetz > Teil > Titel}. */
    public String qualifiedTitle() {
        return (lawName.isEmpty() ? "" : lawName + " > ") + (partName.isEmpty() ? "" : partName + " > ") + title;
    }
}
