package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.shared.StatuteId;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Worüber eine Abstimmung entscheidet: eine einzelne Regelung, einen neuen Paragraphen oder die Aufhebung eines
 * Paragraphen.
 */
public sealed interface Motion permits Motion.Change, Motion.Enact, Motion.Repeal {

    /** Die Regelungen, die eine Annahme setzt; bei einer Aufhebung leer. */
    List<ParameterValue<?>> values();

    /** Gegenstand der Abstimmung; über denselben Gegenstand läuft höchstens eine Abstimmung. */
    String subject();

    /** Eine einzelne Regelung ändern, z. B. „Fremde dürfen bauen: ja“. */
    record Change(ParameterValue<?> change) implements Motion {

        public Change {
            Objects.requireNonNull(change, "change");
        }

        @Override
        public List<ParameterValue<?>> values() {
            return List.of(change);
        }

        @Override
        public String subject() {
            return change.key().id();
        }
    }

    /** Einen Paragraphen erlassen: Titel, Freitext und die daraus erkannten Regelungen. */
    record Enact(String title, String text, List<ParameterValue<?>> values) implements Motion {

        private static final StatuteId CHECK = new StatuteId(new UUID(0, 0));

        public Enact {
            Statute checked = new Statute(CHECK, title, text, values);
            title = checked.title();
            text = checked.text();
            values = checked.values();
        }

        @Override
        public String subject() {
            return "§" + title.toLowerCase(Locale.ROOT);
        }
    }

    /** Einen geltenden Paragraphen aufheben. */
    record Repeal(StatuteId statute, String title) implements Motion {

        public Repeal {
            Objects.requireNonNull(statute, "statute");
            Objects.requireNonNull(title, "title");
        }

        @Override
        public List<ParameterValue<?>> values() {
            return List.of();
        }

        @Override
        public String subject() {
            return "repeal:" + statute.value();
        }
    }
}
