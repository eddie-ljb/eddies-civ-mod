package de.etiennebader.civ.server.law;

import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.server.town.LawSetting;

import java.util.List;
import java.util.Objects;

/**
 * Ein Paragraph, wie Menü und Chat ihn zeigen.
 *
 * @param number       laufende Nummer im Gesetzbuch, ab 1
 * @param effects      Regelungen, die er setzt
 * @param unrecognized Teile des Texts, die das Spiel nicht selbst durchsetzt
 */
public record ParagraphView(int number, StatuteId id, String title, String text, long enactedOn,
                            List<LawSetting> effects, List<String> unrecognized) {

    public ParagraphView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(text, "text");
        effects = List.copyOf(effects);
        unrecognized = List.copyOf(unrecognized);
    }
}
