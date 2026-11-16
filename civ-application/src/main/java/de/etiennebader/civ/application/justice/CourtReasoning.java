package de.etiennebader.civ.application.justice;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.justice.CourtCase;
import de.etiennebader.civ.domain.justice.Evidence;
import de.etiennebader.civ.domain.justice.Role;
import de.etiennebader.civ.domain.justice.Side;
import de.etiennebader.civ.domain.justice.Statement;
import de.etiennebader.civ.domain.population.Personality;

import java.util.List;
import java.util.Locale;

/**
 * Wie Dorfbewohner als Richter, Geschworene und Zeugen urteilen: nach der Beweislage, gefärbt von ihrer
 * Persönlichkeit. Im Zweifel für den Angeklagten.
 */
public final class CourtReasoning {

    /** Ab dieser Beweislage gilt eine Anklage als bewiesen. */
    static final double PROOF = 1.0;

    private static final List<String> INCRIMINATING = List.of("schuldig", "gesehen", "beobachtet", "hat es getan",
            "gestohlen", "zerstört", "beweis", "eindeutig", "war es", "lügt", "guilty", "saw", "stole", "destroyed");
    private static final List<String> EXCULPATORY = List.of("unschuldig", "alibi", "nicht da", "war nicht",
            "hat nichts", "irrtum", "falsch", "zweifel", "unbewiesen", "freispruch", "innocent", "not there", "doubt");

    private CourtReasoning() {
    }

    /**
     * Die Beweislage: positiv belastet sie den Angeklagten bzw. stützt die Klage. Beweise zählen mit ihrer Beweiskraft,
     * Zeugenaussagen mit ihrer Tendenz, Plädoyers halb; ein Verteidiger mildert, ein Staatsanwalt verschärft.
     */
    public static double weight(CourtCase found) {
        double score = 0;
        for (Evidence evidence : found.evidence()) {
            score += evidence.side() == Side.PROSECUTION ? evidence.weight() : -evidence.weight();
        }
        for (Statement statement : found.statements()) {
            double factor = switch (statement.role()) {
                case WITNESS -> 1.0;
                case PROSECUTOR, PLAINTIFF, DEFENDANT, DEFENSE -> 0.5;
                default -> 0;
            };
            score += statement.tendency() * factor;
        }
        if (found.defense().isPresent()) {
            score -= 0.5;
        }
        if (found.prosecutor().isPresent()) {
            score += 0.5;
        }
        return score;
    }

    /** Ob ein Dorfbewohner als Geschworener für schuldig stimmt. */
    public static boolean jurorFindsGuilty(PersonRef juror, CourtCase found) {
        Personality personality = Personality.of(juror);
        double bias = personality.order() / 100.0 * 0.6 - personality.community() / 100.0 * 0.3
                + noise(juror, found) * 0.6;
        return weight(found) + bias > PROOF;
    }

    /** Ob ein Richter ohne Jury die Anklage bzw. Klage für begründet hält. */
    public static boolean judgeUpholds(CourtCase found) {
        return weight(found) > PROOF;
    }

    /** Tendenz einer Aussage aus ihren Worten: -2 entlastet stark bis 2 belastet stark. */
    public static int tendency(String text, Role role) {
        String lower = text.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String word : INCRIMINATING) {
            if (lower.contains(word)) {
                score++;
            }
        }
        for (String word : EXCULPATORY) {
            if (lower.contains(word)) {
                score--;
            }
        }
        if (role == Role.PROSECUTOR || role == Role.PLAINTIFF) {
            score = Math.max(score, 1);
        } else if (role == Role.DEFENDANT || role == Role.DEFENSE) {
            score = Math.min(score, -1);
        }
        return Math.max(-2, Math.min(2, score));
    }

    /** Was ein Dorfbewohner als Zeuge aussagt, und wie es wirkt. */
    public static Statement villagerTestimony(PersonRef witness, CourtCase found, de.etiennebader.civ.domain.shared.Day day) {
        double lean = noise(witness, found) + Personality.of(witness).order() / 200.0;
        String text;
        int tendency;
        if (lean > 0.35) {
            text = "Ich habe gesehen, wie es passiert ist. Für mich ist die Sache eindeutig.";
            tendency = 2;
        } else if (lean > 0.05) {
            text = "Ich war in der Nähe und habe einiges beobachtet, aber nicht alles.";
            tendency = 1;
        } else if (lean > -0.3) {
            text = "Ich weiß nichts Genaues und will niemanden zu Unrecht beschuldigen.";
            tendency = 0;
        } else {
            text = "Ich habe den Angeklagten zur fraglichen Zeit woanders gesehen. Da muss ein Irrtum vorliegen.";
            tendency = -2;
        }
        return new Statement(witness, Role.WITNESS, text, tendency, day);
    }

    /** Ein fester Wert von -1 bis 1 je Person und Verfahren. */
    static double noise(PersonRef person, CourtCase found) {
        long mixed = person.uuid().getMostSignificantBits() ^ found.id().value().getLeastSignificantBits() * 31;
        mixed ^= mixed >>> 33;
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= mixed >>> 33;
        return (mixed & 0xFFFF) / 32_767.5 - 1;
    }
}
