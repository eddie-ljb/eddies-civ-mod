package de.etiennebader.civ.domain.law;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erkennt in frei formulierten Paragraphen Regelungen, die das Spiel durchsetzen kann, z. B. „Explosionen sind
 * verboten.“ oder „Die Umsatzsteuer beträgt 10 %.“ Deutsch und Englisch.
 *
 * <p>Der Text wird in Sätze und Teilsätze zerlegt. Jeder Teilsatz ergibt höchstens eine Regelung; der Subjektbezug
 * („Fremde“) und die Verneinung gelten bis zum Satzende weiter, damit „Fremde dürfen weder bauen noch Truhen öffnen“
 * zwei Regelungen ergibt. Was nicht erkannt wird, bleibt als Text gültig, wird aber nicht automatisch durchgesetzt.
 */
public final class LawRecognizer {

    /** Ein Teilsatz und die Regelung, die er setzt, falls erkannt. */
    public record Clause(String text, Optional<ParameterValue<?>> value) {

        public Clause {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(value, "value");
        }
    }

    /** Ergebnis der Erkennung eines ganzen Texts. */
    public record Recognition(List<Clause> clauses) {

        public Recognition {
            clauses = List.copyOf(clauses);
        }

        /** Erkannte Regelungen; regelt der Text dasselbe mehrfach, gilt die letzte Fundstelle. */
        public List<ParameterValue<?>> values() {
            Map<ParameterKey<?>, ParameterValue<?>> byKey = new LinkedHashMap<>();
            for (Clause clause : clauses) {
                clause.value().ifPresent(value -> {
                    byKey.remove(value.key());
                    byKey.put(value.key(), value);
                });
            }
            return List.copyOf(byKey.values());
        }

        /** Teilsätze ohne erkannte Regelung. */
        public List<String> unrecognized() {
            return clauses.stream().filter(clause -> clause.value().isEmpty()).map(Clause::text).toList();
        }
    }

    private static final Pattern SENTENCES = Pattern.compile("(?<=[.!?;:])\\s+|\\n+");
    private static final Pattern CLAUSES = Pattern.compile(",(?!\\d)|\\s+(?:und|sowie|oder|noch|and|or|nor)\\s+");
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:[.,]\\d+)?)");

    private static final Pattern NEGATION = Pattern.compile(
            "verbot|untersag|nicht erlaubt|nicht gestattet|nicht zulaessig|unzulaessig|duerfen nicht|darf nicht"
                    + "|\\bnicht\\b|\\bkein|\\bweder\\b|\\bnoch\\b|\\bniemals\\b|\\bnie\\b"
                    + "|\\bban(ned)?\\b|forbidden|prohibited|not allowed|not permitted|may not|must not|cannot"
                    + "|\\bno\\b|\\bnever\\b|\\bneither\\b|\\bnor\\b");
    private static final Pattern PERMISSION = Pattern.compile(
            "erlaubt|gestattet|zulaessig|\\bduerfen\\b|\\bdarf\\b|\\bfrei\\b"
                    + "|allowed|permitted|\\bmay\\b|\\bcan\\b");

    private static final Pattern OUTSIDERS = Pattern.compile(
            "fremd|auswaertig|nichtmitglied|nicht-mitglied|nicht mitglied|gaest|gast\\b|besucher|zugereist"
                    + "|outsider|stranger|visitor|foreigner|non-member|guest");
    private static final Pattern BUILD = Pattern.compile("\\bbau|abbau|abreiss|abbrech|\\bbuild|\\bbreak|\\bmine\\b");
    private static final Pattern CONTAINERS = Pattern.compile(
            "truhe|behaelter|kiste|\\bofen|oefen|faesser|fass\\b|chest|container|barrel|furnace");
    private static final Pattern INTERACT = Pattern.compile(
            "tuer|\\btor|knopf|knoepf|hebel|\\bbett|door|gate|button|lever|\\bbed");
    private static final Pattern EXPLOSIONS = Pattern.compile("explosi|spreng|\\btnt\\b|creeper");
    private static final Pattern FIRE = Pattern.compile("feuer|\\bbrand|braende|\\bfire\\b|flamm");
    private static final Pattern EMBASSIES = Pattern.compile("botschaft|embass");
    private static final Pattern TAX = Pattern.compile("steuer|\\btax");
    private static final Pattern TAX_FREE = Pattern.compile("steuerfrei|tax.free|tax-exempt|exempt");
    private static final Pattern BLOCKS = Pattern.compile("block|bloeck");
    private static final Pattern DAYS = Pattern.compile("\\btag|\\bday");
    private static final Pattern LAND_TAX = Pattern.compile("grundsteuer|land ?tax|property tax");
    private static final Pattern SALES_TAX = Pattern.compile(
            "umsatzsteuer|verkaufssteuer|mehrwertsteuer|ladensteuer|handelssteuer|sales ?tax|\\bvat\\b");
    private static final Pattern EMERALD = Pattern.compile("smaragd|emerald");
    private static final Pattern OUTPOSTS = Pattern.compile("aussenpost|outpost");
    private static final Pattern VILLAGERS = Pattern.compile("dorfbewohner|villager");
    private static final Pattern VOTING = Pattern.compile("waehl|wahlrecht|stimmrecht|\\bvote|\\bvoting");
    private static final Pattern TERM = Pattern.compile("amtszeit|legislatur|\\bterm\\b|term of office");
    private static final Pattern ELECTION = Pattern.compile("\\bwahl|election");
    private static final Pattern REFERENDUM = Pattern.compile("abstimmung|volksentscheid|referend|\\bvote");
    private static final Pattern LAWS = Pattern.compile("gesetz|\\brecht\\b|regel|\\blaw|\\brule");
    private static final Pattern DECREE = Pattern.compile(
            "buergermeister.*(allein|verordn|erlaesst|entscheidet)|mayor.*(alone|decree|decides)");
    private static final Pattern CONSTITUTION = Pattern.compile("verfassung|constitution");
    private static final Pattern AMEND = Pattern.compile("aender|amend|change");
    private static final Pattern FEDERAL = Pattern.compile(
            "bundesrecht|landesrecht|reichsrecht|hoeheres recht|uebergeordnet|federal law|higher law");
    private static final Pattern PVP = Pattern.compile(
            "\\bpvp\\b|spielerkampf|kaempfe zwischen spielern|duell|player.?vs|player combat|spieler angreifen");
    private static final Pattern MOBS = Pattern.compile(
            "monster|zombie|skelett|spinne|feindlich|hostile|\\bmobs?\\b");
    private static final Pattern ANIMALS = Pattern.compile(
            "\\btier|vieh|\\bkuh|kuehe|schaf|schwein|huhn|huehner|pferd|animal|cattle|livestock");
    private static final Pattern PROTECTED = Pattern.compile("geschuetzt|schutz|protected");
    private static final Pattern LEVY = Pattern.compile("umlage|abgabe an den bund|levy|federal contribution");
    private static final Pattern PARLIAMENT = Pattern.compile(
            "parlament|landtag|bundestag|gemeinderat|stadtrat|volksvertretung|abgeordnet|parliament|council|assembly");
    private static final Pattern SEATS = Pattern.compile("\\bsitz|mandat|abgeordnet|mitglieder|\\bseat|member");
    private static final Pattern SESSION = Pattern.compile("\\btagt|sitzung|\\bsession|\\bmeets?\\b|convene");
    private static final Pattern BALLOT_BOX = Pattern.compile("\\burne|wahlurne|wahllokal|ballot box|polling station");
    private static final Pattern POSTAL = Pattern.compile("briefwahl|per brief|postal vot|by mail|mail.in");
    private static final Pattern SALARY = Pattern.compile("gehalt|diaet|besoldung|salary|stipend");
    private static final Pattern PREVAILS = Pattern.compile("bricht|geht vor|vorrang|prevail|takes precedence|overrides");

    private LawRecognizer() {
    }

    public static Recognition recognize(String text) {
        Objects.requireNonNull(text, "text");
        List<Clause> clauses = new ArrayList<>();
        for (String sentence : SENTENCES.split(text.strip())) {
            if (sentence.isBlank()) {
                continue;
            }
            Context context = new Context();
            for (String part : CLAUSES.split(sentence)) {
                String clause = part.strip();
                if (clause.isEmpty()) {
                    continue;
                }
                clauses.add(new Clause(clause, recognizeClause(normalize(clause), context)));
            }
        }
        return new Recognition(clauses);
    }

    /** Was innerhalb eines Satzes von Teilsatz zu Teilsatz weitergilt. */
    private static final class Context {
        private boolean outsiders;
        private Boolean negated;
    }

    private static Optional<ParameterValue<?>> recognizeClause(String clause, Context context) {
        if (OUTSIDERS.matcher(clause).find()) {
            context.outsiders = true;
        }
        Boolean polarity = polarity(clause);
        if (polarity != null) {
            context.negated = !polarity;
        }
        boolean allowed = context.negated == null || !context.negated;
        boolean hasPolarity = context.negated != null;

        if (CONSTITUTION.matcher(clause).find() && AMEND.matcher(clause).find()) {
            return majority(clause).flatMap(percent -> value(StandardParameters.AMENDMENT_MAJORITY_PERCENT, percent));
        }
        if (FEDERAL.matcher(clause).find() && (PREVAILS.matcher(clause).find() || hasPolarity)) {
            return value(StandardParameters.FEDERAL_LAW_PREVAILS, polarity == null || polarity);
        }
        if (VILLAGERS.matcher(clause).find() && VOTING.matcher(clause).find()) {
            if (!allowed) {
                return value(StandardParameters.VILLAGER_VOTE_PERCENT, 0L);
            }
            return value(StandardParameters.VILLAGER_VOTE_PERCENT, number(clause).map(Math::round).orElse(100L));
        }
        if (TERM.matcher(clause).find()) {
            if (clause.contains("unbefrist") || clause.contains("unlimited") || clause.contains("indefinite")) {
                return value(StandardParameters.TERM_DAYS, 0L);
            }
            return days(clause).flatMap(days -> value(StandardParameters.TERM_DAYS, days));
        }
        if (PARLIAMENT.matcher(clause).find() && SESSION.matcher(clause).find() && DAYS.matcher(clause).find()) {
            if (clause.contains("taeglich") || clause.contains("jeden tag") || clause.contains("every day")
                    || clause.contains("daily")) {
                return value(StandardParameters.SESSION_DAYS, 1L);
            }
            return days(clause).flatMap(days -> value(StandardParameters.SESSION_DAYS, days));
        }
        if (POSTAL.matcher(clause).find()) {
            return value(StandardParameters.POSTAL_VOTE_ALLOWED, allowed);
        }
        if (SALARY.matcher(clause).find()) {
            if (!allowed && number(clause).isEmpty()) {
                return value(StandardParameters.OFFICE_SALARY, 0L);
            }
            boolean emeralds = EMERALD.matcher(clause).find();
            return number(clause).flatMap(amount -> value(StandardParameters.OFFICE_SALARY,
                    Math.round(emeralds ? amount * 100 : amount)));
        }
        if (BALLOT_BOX.matcher(clause).find() && (ELECTION.matcher(clause).find() || VOTING.matcher(clause).find()
                || clause.contains("gewaehlt") || clause.contains("stimm"))) {
            boolean optional = clause.contains("ohne urne") || clause.contains("nicht noetig")
                    || clause.contains("nicht erforderlich") || clause.contains("not required")
                    || clause.contains("without");
            return value(StandardParameters.BALLOT_BOX_REQUIRED, !optional);
        }
        if (PARLIAMENT.matcher(clause).find() && (SEATS.matcher(clause).find() || !allowed)) {
            if (!allowed && number(clause).isEmpty()) {
                return value(StandardParameters.PARLIAMENT_SEATS, 0L);
            }
            return number(clause).flatMap(seats -> value(StandardParameters.PARLIAMENT_SEATS, Math.round(seats)));
        }
        if (ELECTION.matcher(clause).find() && DAYS.matcher(clause).find() && !VILLAGERS.matcher(clause).find()) {
            return days(clause).flatMap(days -> value(StandardParameters.ELECTION_DAYS, days));
        }
        if (DECREE.matcher(clause).find()) {
            return value(StandardParameters.LAWS_BY_VOTE, !allowed);
        }
        if (REFERENDUM.matcher(clause).find() && DAYS.matcher(clause).find()) {
            return days(clause).flatMap(days -> value(StandardParameters.VOTE_DAYS, days));
        }
        if (REFERENDUM.matcher(clause).find() && LAWS.matcher(clause).find()) {
            return value(StandardParameters.LAWS_BY_VOTE, allowed);
        }
        if (LEVY.matcher(clause).find()) {
            if (!allowed && number(clause).isEmpty()) {
                return value(StandardParameters.FEDERAL_LEVY_PERCENT, 0L);
            }
            return number(clause).flatMap(percent -> value(StandardParameters.FEDERAL_LEVY_PERCENT, Math.round(percent)));
        }
        if (EMBASSIES.matcher(clause).find() && TAX.matcher(clause).find()) {
            boolean exempt = TAX_FREE.matcher(clause).find() || !allowed;
            return value(StandardParameters.TAX_EMBASSIES, !exempt);
        }
        if (TAX_FREE.matcher(clause).find() && DAYS.matcher(clause).find()) {
            return days(clause).flatMap(days -> value(StandardParameters.LAND_TAX_GRACE_DAYS, days));
        }
        if (TAX_FREE.matcher(clause).find() && BLOCKS.matcher(clause).find()) {
            return number(clause).flatMap(blocks -> value(StandardParameters.LAND_TAX_FREE_BLOCKS, Math.round(blocks)));
        }
        if (SALES_TAX.matcher(clause).find()) {
            if (!allowed && number(clause).isEmpty()) {
                return value(StandardParameters.SALES_TAX_PERCENT, 0L);
            }
            return number(clause).flatMap(percent -> value(StandardParameters.SALES_TAX_PERCENT, Math.round(percent)));
        }
        if (LAND_TAX.matcher(clause).find()) {
            if (!allowed && number(clause).isEmpty()) {
                return value(StandardParameters.LAND_TAX_PER_BLOCK, 0L);
            }
            boolean emeralds = EMERALD.matcher(clause).find();
            return number(clause).flatMap(amount -> value(StandardParameters.LAND_TAX_PER_BLOCK,
                    Math.round(emeralds ? amount * 100 : amount)));
        }
        if (OUTPOSTS.matcher(clause).find()) {
            if (!allowed && number(clause).isEmpty()) {
                return value(StandardParameters.MAX_OUTPOSTS, 0);
            }
            return number(clause).flatMap(count -> value(StandardParameters.MAX_OUTPOSTS, (int) Math.round(count)));
        }
        if (PVP.matcher(clause).find() && hasPolarity) {
            return value(StandardParameters.PVP_ALLOWED, allowed);
        }
        if (ANIMALS.matcher(clause).find() && PROTECTED.matcher(clause).find()) {
            return value(StandardParameters.OUTSIDERS_MAY_HURT_ANIMALS, false);
        }
        if (ANIMALS.matcher(clause).find() && context.outsiders && hasPolarity) {
            return value(StandardParameters.OUTSIDERS_MAY_HURT_ANIMALS, allowed);
        }
        if (EXPLOSIONS.matcher(clause).find() && hasPolarity) {
            return value(StandardParameters.EXPLOSIONS_ALLOWED, allowed);
        }
        if (FIRE.matcher(clause).find() && hasPolarity) {
            return value(StandardParameters.FIRE_SPREAD_ALLOWED, allowed);
        }
        if (MOBS.matcher(clause).find() && hasPolarity) {
            return value(StandardParameters.HOSTILE_MOBS_ALLOWED, allowed);
        }
        if (context.outsiders && hasPolarity) {
            if (CONTAINERS.matcher(clause).find()) {
                return value(StandardParameters.OUTSIDERS_MAY_OPEN_CONTAINERS, allowed);
            }
            if (INTERACT.matcher(clause).find()) {
                return value(StandardParameters.OUTSIDERS_MAY_INTERACT, allowed);
            }
            if (BUILD.matcher(clause).find()) {
                return value(StandardParameters.OUTSIDERS_MAY_BUILD, allowed);
            }
        }
        return Optional.empty();
    }

    /** {@code true} erlaubt, {@code false} verboten, {@code null} ohne Aussage. */
    private static Boolean polarity(String clause) {
        if (NEGATION.matcher(clause).find()) {
            return false;
        }
        return PERMISSION.matcher(clause).find() ? true : null;
    }

    private static Optional<Long> majority(String clause) {
        if (clause.contains("einstimmig") || clause.contains("unanim")) {
            return Optional.of(100L);
        }
        if (clause.contains("drei viertel") || clause.contains("dreiviertel") || clause.contains("three quarters")) {
            return Optional.of(75L);
        }
        if (clause.contains("zwei drittel") || clause.contains("zweidrittel") || clause.contains("two thirds")) {
            return Optional.of(66L);
        }
        if (clause.contains("einfache") || clause.contains("simple")) {
            return Optional.of(50L);
        }
        return number(clause).map(Math::round);
    }

    private static Optional<Long> days(String clause) {
        return number(clause).map(Math::round);
    }

    private static Optional<Double> number(String clause) {
        Matcher matcher = NUMBER.matcher(clause);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(Double.parseDouble(matcher.group(1).replace(',', '.')));
    }

    private static <T> Optional<ParameterValue<?>> value(ParameterKey<T> key, T value) {
        if (!key.accepts(value)) {
            return Optional.empty();
        }
        return Optional.of(new ParameterValue<>(key, value));
    }

    /** Kleinbuchstaben, Umlaute als ae/oe/ue/ss, damit Schreibweisen ohne Umlaut ebenso passen. */
    static String normalize(String text) {
        return text.toLowerCase(Locale.GERMAN)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replace("%", " % ");
    }
}
