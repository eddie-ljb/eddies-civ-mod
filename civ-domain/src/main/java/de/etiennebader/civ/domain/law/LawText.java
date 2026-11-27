package de.etiennebader.civ.domain.law;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Gliedert den Freitext eines Paragraphen in Absätze, Sätze und Aufzählungen. Ein Absatz endet an einer Leerzeile;
 * eine Zeile, die mit {@code -}, {@code *}, {@code •}, {@code 1.}, {@code 2)} oder {@code a)} beginnt, ist ein Punkt
 * einer Aufzählung; der übrige Text eines Absatzes wird an {@code . ! ?} in Sätze geteilt.
 */
public final class LawText {

    private static final Pattern BULLET = Pattern.compile("^(?:[-*•]|\\d{1,3}[.)]|[a-zA-Z][.)])\\s+(.+)$");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    /**
     * Ein Absatz.
     *
     * @param number      laufende Nummer im Paragraphen, ab 1
     * @param sentences   die Sätze, ohne Aufzählungspunkte
     * @param enumeration die Punkte der Aufzählung, leer wenn es keine gibt
     */
    public record Section(int number, List<String> sentences, List<String> enumeration) {

        public Section {
            sentences = List.copyOf(sentences);
            enumeration = List.copyOf(enumeration);
        }
    }

    private LawText() {
    }

    public static List<Section> sections(String text) {
        Objects.requireNonNull(text, "text");
        List<Section> sections = new ArrayList<>();
        for (String block : text.replace("\r", "").strip().split("\\n\\s*\\n")) {
            List<String> prose = new ArrayList<>();
            List<String> items = new ArrayList<>();
            for (String line : block.split("\\n")) {
                String stripped = line.strip();
                if (stripped.isEmpty()) {
                    continue;
                }
                var bullet = BULLET.matcher(stripped);
                if (bullet.matches()) {
                    items.add(bullet.group(1).strip());
                } else {
                    prose.add(stripped);
                }
            }
            List<String> sentences = new ArrayList<>();
            for (String sentence : SENTENCE_END.split(String.join(" ", prose))) {
                if (!sentence.isBlank()) {
                    sentences.add(sentence.strip());
                }
            }
            if (!sentences.isEmpty() || !items.isEmpty()) {
                sections.add(new Section(sections.size() + 1, sentences, items));
            }
        }
        return List.copyOf(sections);
    }
}
