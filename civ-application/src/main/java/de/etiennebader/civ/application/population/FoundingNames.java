package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.PersonRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Namen für alles, was Dorfbewohner gründen: Gemeinden, Straßen, Firmen, Zeitungen, Parteien und Religionen.
 * Die Namen sind richtige deutsche Wörter ohne Zahlen und Kennungen. Je Dorfbewohner ist die Wahl fest (aus seiner UUID);
 * ist ein Name vergeben, wird der nächste Vorschlag genommen. Erst wenn alle Vorschläge vergeben sind, kommt ein Zusatz
 * wie „Alt“, „Neu“ oder „Ober“ davor, nie eine Zahl.
 */
public final class FoundingNames {

    private static final int MAX = 32;

    private static final List<String> TOWN_STEMS = List.of("Eichen", "Linden", "Birken", "Buchen", "Erlen", "Weiden",
            "Tannen", "Rosen", "Lilien", "Wiesen", "Mühlen", "Bach", "Berg", "Tal", "Feld", "Wald", "See", "Fluss",
            "Stein", "Sonnen", "Mond", "Stern", "Wolken", "Nebel", "Hirsch", "Falken", "Adler", "Raben", "Fuchs",
            "Bären", "Schwanen", "Gold", "Silber", "Kupfer", "Eisen", "Salz", "Kirsch", "Apfel", "Haselnuss", "Hopfen",
            "Korn", "Weizen", "Klee", "Heide", "Moor", "Fels", "Hügel", "Kiesel", "Quell", "Furt");
    private static final List<String> TOWN_ENDINGS = List.of("au", "bach", "berg", "burg", "dorf", "feld", "hain",
            "hausen", "heim", "hof", "holz", "kamp", "mark", "stedt", "tal", "wald", "weiler", "brunn", "furt", "lohe",
            "ried", "stein", "wiese", "dorn", "see", "horst");
    private static final List<String> TOWN_PREFIXES = List.of("Alt", "Neu", "Ober", "Unter", "Groß", "Klein", "Hinter",
            "Vorder", "Mittel", "Nieder");

    private static final List<String> STREET_NAMES = List.of("Hauptstraße", "Marktstraße", "Dorfstraße",
            "Kirchstraße", "Schulstraße", "Bahnhofstraße", "Mühlenweg", "Lindenallee", "Gartenstraße", "Bachstraße",
            "Brunnenstraße", "Wiesenweg", "Rosenstraße", "Feldweg", "Waldstraße", "Birkenweg", "Eichenallee",
            "Am Markt", "Am Brunnen", "An der Mühle", "Am Bach", "Am Hang", "Im Winkel", "Schmiedegasse", "Bäckergasse",
            "Fischergasse", "Gerbergasse", "Töpfergasse", "Sonnenweg", "Mondweg", "Sternenweg", "Kastanienallee",
            "Ahornweg", "Tannenweg", "Heckenweg", "Kornweg", "Obstgartenweg", "Hirtenweg", "Pilgerweg", "Fährweg");

    private static final List<String> TRADE_NAMES = List.of("Handelshaus", "Kontor", "Warenhaus", "Handelsgesellschaft",
            "Handelskompanie", "Werkstätten", "Manufaktur", "Gutshof", "Betriebe", "Lieferdienst");
    private static final List<String> TRADE_ADJECTIVES = List.of("Goldener", "Silberner", "Roter", "Blauer", "Grüner",
            "Fleißiger", "Ehrbarer", "Rechter", "Guter", "Alter");

    private static final List<String> PAPER_TITLES = List.of("Anzeiger", "Bote", "Kurier", "Tagblatt", "Rundschau",
            "Chronik", "Zeitung", "Echo");
    private static final List<String> PAPER_ADJECTIVES = List.of("Neuer", "Freier", "Täglicher", "Unabhängiger",
            "Ehrlicher", "Wahrer", "Schneller", "Aufrechter");

    private static final List<String> PARTY_ADJECTIVES = List.of("Neue", "Freie", "Unabhängige", "Vereinigte",
            "Demokratische", "Bürgerliche", "Ehrliche", "Gemeinsame", "Offene", "Starke");

    private static final List<String> HOLY_EPITHETS = List.of("des Lichts", "der Stille", "der Quelle", "des Weges",
            "der Ernte", "der Sterne", "des Windes", "der Morgenröte", "der Wahrheit", "der Gnade", "des Friedens",
            "des Feuers", "der Tiefe", "der Ahnen", "der Hoffnung");
    private static final List<String> GREAT_THINGS = List.of("Lichts", "Weges", "Friedens", "Ursprungs", "Himmels",
            "Ganzen", "Ewigen", "Wortes", "Wunders", "Anfangs");

    private FoundingNames() {
    }

    private static <T> T pick(List<T> list, PersonRef who, int salt) {
        long mixed = who.uuid().getLeastSignificantBits() * 0x9E3779B97F4A7C15L
                + who.uuid().getMostSignificantBits() * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        mixed ^= mixed >>> 29;
        mixed *= 0xbf58476d1ce4e5b9L;
        mixed ^= mixed >>> 32;
        return list.get((int) Math.floorMod(mixed, (long) list.size()));
    }

    /** Erstes freies Element der Kandidaten; sonst die Kandidaten mit Vorsilbe, nie mit Nummer. */
    private static String firstFree(List<String> candidates, Predicate<String> taken, List<String> prefixes) {
        for (String candidate : candidates) {
            if (fits(candidate) && !taken.test(candidate)) {
                return candidate;
            }
        }
        for (String prefix : prefixes) {
            for (String candidate : candidates) {
                String name = prefix + (prefix.endsWith("-") ? "" : " ") + candidate;
                if (fits(name) && !taken.test(name)) {
                    return name;
                }
            }
        }
        return candidates.getFirst();
    }

    private static boolean fits(String name) {
        return name.length() >= 2 && name.length() <= MAX;
    }

    /** Ein Ortsname wie „Lindenau“ oder „Eichenhain“; mit Vorsilbe „Alt“/„Neu“ nur, wenn alles vergeben ist. */
    public static String town(PersonRef founder, Predicate<String> taken) {
        List<String> candidates = new ArrayList<>();
        int first = (int) Math.floorMod(founder.uuid().getLeastSignificantBits(), (long) TOWN_STEMS.size());
        int ending = (int) Math.floorMod(founder.uuid().getMostSignificantBits(), (long) TOWN_ENDINGS.size());
        for (int step = 0; step < TOWN_STEMS.size() * 2; step++) {
            String stem = TOWN_STEMS.get((first + step) % TOWN_STEMS.size());
            String end = TOWN_ENDINGS.get((ending + step / TOWN_STEMS.size() + step) % TOWN_ENDINGS.size());
            candidates.add(stem + end);
        }
        return firstFree(candidates, taken, TOWN_PREFIXES);
    }

    /** Ein Straßenname; {@code taken} sagt, welche in der Gemeinde schon vergeben sind (Groß-/Kleinschreibung egal). */
    public static String street(PersonRef founder, Set<String> taken, String townName) {
        List<String> candidates = new ArrayList<>();
        int start = (int) Math.floorMod(founder.uuid().getLeastSignificantBits() >>> 7, (long) STREET_NAMES.size());
        for (int step = 0; step < STREET_NAMES.size(); step++) {
            candidates.add(STREET_NAMES.get((start + step) % STREET_NAMES.size()));
        }
        Set<String> lower = new java.util.HashSet<>();
        taken.forEach(name -> lower.add(name.toLowerCase(java.util.Locale.ROOT)));
        for (String suffix : new String[] {"", " Nord", " Süd", " Ost", " West"}) {
            for (String candidate : candidates) {
                String name = candidate + suffix;
                if (fits(name) && !lower.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                    return name;
                }
            }
        }
        return (townName + "er Allee").substring(0, Math.min(MAX, (townName + "er Allee").length()));
    }

    /** Ein Firmenname wie „Eichenau Handelshaus“ oder „Goldener Kontor“. */
    public static String company(PersonRef founder, String townName, Predicate<String> taken) {
        List<String> candidates = new ArrayList<>();
        int start = (int) Math.floorMod(founder.uuid().getLeastSignificantBits() >>> 3, (long) TRADE_NAMES.size());
        for (int step = 0; step < TRADE_NAMES.size(); step++) {
            String trade = TRADE_NAMES.get((start + step) % TRADE_NAMES.size());
            candidates.add(townName + " " + trade);
        }
        for (String adjective : TRADE_ADJECTIVES) {
            candidates.add(adjective + " " + pick(TRADE_NAMES, founder, 3));
        }
        return firstFree(candidates, taken, List.of("Neue", "Alte"));
    }

    /** Ein Zeitungsname wie „Dorfbote Eichenau“ oder „Freier Anzeiger“. */
    public static String newspaper(PersonRef founder, String townName, Predicate<String> taken) {
        String stem = pick(PAPER_TITLES, founder, 5);
        List<String> candidates = new ArrayList<>();
        candidates.add(stem + " " + townName);
        for (String adjective : PAPER_ADJECTIVES) {
            candidates.add(adjective + " " + stem);
            candidates.add(adjective + " " + stem + " " + townName);
        }
        return firstFree(candidates, taken, List.of("Der", "Die"));
    }

    /** Ein Parteiname wie „Freie Ordnungspartei Eichenau“. */
    public static String party(PersonRef founder, String base, String townName, Predicate<String> taken) {
        List<String> candidates = new ArrayList<>();
        candidates.add(base + " " + townName);
        candidates.add(base);
        for (String adjective : PARTY_ADJECTIVES) {
            candidates.add(adjective + " " + base);
            candidates.add(adjective + " " + base + " " + townName);
        }
        return firstFree(candidates, taken, List.of("Alte", "Echte"));
    }

    /** Ein Name für eine Religion, z. B. „Hain der Quelle“ oder „Kirche des Einen Lichts“. */
    public static String religion(PersonRef founder, String stem, boolean monotheistic, Predicate<String> taken) {
        List<String> candidates = new ArrayList<>();
        int start = (int) Math.floorMod(founder.uuid().getLeastSignificantBits() >>> 5, (long) HOLY_EPITHETS.size());
        for (int step = 0; step < HOLY_EPITHETS.size(); step++) {
            String epithet = HOLY_EPITHETS.get((start + step) % HOLY_EPITHETS.size());
            candidates.add(monotheistic ? stem + " " + epithet : stem + " " + epithet.replace("des ", "der ")
                    .replace("der Wahrheit", "der Wahrheiten"));
        }
        return firstFree(candidates, taken, List.of("Alter", "Neuer", "Wahrer"));
    }

    /** Der Name der Heiligen Schrift. */
    public static String scripture(PersonRef founder, boolean monotheistic, Predicate<String> taken) {
        List<String> candidates = new ArrayList<>();
        int start = (int) Math.floorMod(founder.uuid().getMostSignificantBits() >>> 5, (long) GREAT_THINGS.size());
        for (int step = 0; step < GREAT_THINGS.size(); step++) {
            String thing = GREAT_THINGS.get((start + step) % GREAT_THINGS.size());
            candidates.add(monotheistic ? "Das Buch des " + thing : "Die Sagen des " + thing);
        }
        return firstFree(candidates, taken, List.of("Das Erste", "Das Zweite"));
    }
}
