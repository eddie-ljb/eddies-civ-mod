package de.etiennebader.civ.domain.law;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Wahlverfahren, einstellbar im Recht einer Polity.
 */
public final class VotingMethod {

    private VotingMethod() {
    }

    /** Wie ein einzelnes Amt (Bürgermeister, Schatzmeister, Bauamt) besetzt wird. */
    public enum Office {
        /** Relative Mehrheit („First past the post“): wer die meisten Stimmen hat, gewinnt. */
        PLURALITY("plurality"),
        /** Absolute Mehrheit: Wer über 50 % hat, gewinnt, sonst Stichwahl der beiden Besten. */
        RUNOFF("runoff");

        private final String id;

        Office(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Office of(String id) {
            return find(values(), id, Office::id, PLURALITY);
        }

        public static List<String> ids() {
            return Arrays.stream(values()).map(Office::id).toList();
        }
    }

    /** Wie die Sitze eines Parlaments verteilt werden. */
    public enum Parliament {
        /** Verhältniswahl nach Sainte-Laguë (Divisoren 1, 3, 5, …), wie im Bundestag. */
        SAINTE_LAGUE("sainte_lague", true),
        /** Verhältniswahl nach D'Hondt (Divisoren 1, 2, 3, …): begünstigt große Listen. */
        DHONDT("dhondt", true),
        /** Verhältniswahl nach Hare-Niemeyer: Quote, Rest nach größten Nachkommastellen. */
        HARE_NIEMEYER("hare_niemeyer", true),
        /** Mehrheitswahl: die Kandidaten mit den meisten Stimmen ziehen ein, ohne Listenausgleich. */
        MAJORITY("majority", false),
        /** Mehrheitswahl in Wahlkreisen: Jeder Wahlkreis wählt seine Sitze nach relativer Mehrheit. */
        DISTRICTS("districts", false),
        /** Personalisierte Verhältniswahl: Wahlkreissieger sicher, übrige Sitze als Ausgleich nach Sainte-Laguë. */
        MIXED("mixed", true);

        private final String id;
        private final boolean proportional;

        Parliament(String id, boolean proportional) {
            this.id = id;
            this.proportional = proportional;
        }

        public String id() {
            return id;
        }

        /** Ob Listen nach ihrem Stimmenanteil Sitze bekommen (dann gilt auch die Sperrklausel). */
        public boolean proportional() {
            return proportional;
        }

        public static Parliament of(String id) {
            return find(values(), id, Parliament::id, SAINTE_LAGUE);
        }

        public static List<String> ids() {
            return Arrays.stream(values()).map(Parliament::id).toList();
        }
    }

    /** Wofür eine Stimme bei der Parlamentswahl gilt. */
    public enum Ballot {
        /** Personenwahl: Innerhalb einer Liste ziehen die Kandidaten mit den meisten persönlichen Stimmen ein. */
        PERSON("person"),
        /** Listenwahl: Die Stimme gilt der Liste; ihre Sitze gehen der Reihe nach an die Listenplätze. */
        LIST("list");

        private final String id;

        Ballot(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Ballot of(String id) {
            return find(values(), id, Ballot::id, PERSON);
        }

        public static List<String> ids() {
            return Arrays.stream(values()).map(Ballot::id).toList();
        }
    }

    private static <E> E find(E[] values, String id, java.util.function.Function<E, String> idOf, E fallback) {
        Objects.requireNonNull(id, "id");
        String wanted = id.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values).filter(value -> idOf.apply(value).equals(wanted)).findFirst().orElse(fallback);
    }
}
