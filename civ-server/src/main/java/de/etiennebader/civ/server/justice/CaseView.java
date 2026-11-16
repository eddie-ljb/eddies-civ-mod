package de.etiennebader.civ.server.justice;

import de.etiennebader.civ.server.command.Message;

import java.util.List;
import java.util.Optional;

/**
 * Ein Verfahren für Menü, Chat und Webseite.
 *
 * @param kind    {@code CRIMINAL}, {@code CIVIL} oder {@code CONSTITUTIONAL}
 * @param stage   Abschnitt, z. B. {@code EVIDENCE}
 * @param myRole  die Rolle des Betrachters oder leer
 * @param jurors  Namen der Geschworenen mit Stimme ({@code ✔}, {@code ✘} oder {@code ?})
 */
public record CaseView(int number, String court, String kind, String plaintiff, String defendant, String charge,
                       String target, String stage, long stageUntil, Optional<String> judge,
                       Optional<String> prosecutor, Optional<String> defense, List<String> witnesses,
                       List<String> jurors, List<EvidenceLine> evidence, List<StatementLine> statements,
                       Optional<Message> verdict, Optional<String> myRole, boolean upheld, Optional<Integer> appealOf) {

    public CaseView {
        witnesses = List.copyOf(witnesses);
        jurors = List.copyOf(jurors);
        evidence = List.copyOf(evidence);
        statements = List.copyOf(statements);
    }

    /** Ein Beweismittel. */
    public record EvidenceLine(int number, String by, String side, String kind, String title, String content, int weight) {
    }

    /** Eine Aussage. */
    public record StatementLine(String speaker, String role, String text, int tendency) {
    }
}
