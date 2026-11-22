package de.etiennebader.civ.domain.population;

import de.etiennebader.civ.domain.actor.PersonRef;

import java.math.BigInteger;
import java.util.UUID;

/** Bürgerlicher Name und eindeutige Registernummer, abgeleitet aus der unveränderten Minecraft-UUID. */
public record VillagerIdentity(String fullName, String registerNumber) {

    // Die Reihenfolge ist Teil der Namenszuordnung vorhandener Welten.
    private static final String[] FIRST_NAMES = {
            "Ada", "Adrian", "Alina", "Amir", "Anna", "Anton", "Aylin", "Ben", "Clara", "David", "Dilan", "Elif",
            "Emil", "Emma", "Felix", "Finn", "Frieda", "Hannah", "Ida", "Jana", "Johann", "Jonas", "Klara", "Lea",
            "Leonie", "Leyla", "Linus", "Lotte", "Luca", "Mara", "Mats", "Mila", "Mina", "Moritz", "Nele", "Noah",
            "Nora", "Oskar", "Paul", "Rosa", "Samira", "Selin", "Sofia", "Theo", "Tobias", "Umut", "Yara", "Zoe"
    };
    // Gleiche Reihenfolge wie FIRST_NAMES: true = weiblich. Das Geschlecht folgt dem Vornamen, braucht also keine
    // Speicherung und bleibt in vorhandenen Welten stabil.
    private static final boolean[] FEMALE = {
            true, false, true, false, true, false, true, false, true, false, true, true,
            false, true, false, false, true, true, true, true, false, false, true, true,
            true, true, false, true, false, true, false, true, true, false, true, false,
            true, false, false, true, true, true, true, false, false, false, true, true
    };
    private static final String[] LAST_NAMES = {
            "Aydin", "Bach", "Bauer", "Becker", "Berger", "Brandt", "Braun", "Demir", "Engel", "Fischer", "Franke",
            "Fuchs", "Graf", "Hahn", "Hartmann", "Hoffmann", "Kaya", "Keller", "Koch", "Kraus", "Kuhn", "Lehmann",
            "Maier", "Meyer", "Neumann", "Nguyen", "Peters", "Richter", "Roth", "Schmidt", "Schneider", "Schulz",
            "Seidel", "Sommer", "Stein", "Vogel", "Walter", "Weber", "Werner", "Winkler", "Wolf", "Yilmaz", "Zimmermann"
    };

    public static VillagerIdentity of(PersonRef person) {
        UUID uuid = person.uuid();
        // Alle 128 Bits bleiben erhalten. Auch gleiche Namen oder ehemalige Kurz-IDs kollidieren nicht.
        String number = new BigInteger(uuid.toString().replace("-", ""), 16).toString();
        return new VillagerIdentity(nameOf(uuid), "V-" + number);
    }

    public static String nameOf(UUID uuid) {
        int last = Math.floorMod(uuid.getLeastSignificantBits() ^ Long.rotateLeft(uuid.getMostSignificantBits(), 31),
                LAST_NAMES.length);
        return FIRST_NAMES[firstNameIndex(uuid)] + " " + LAST_NAMES[last];
    }

    /** Geschlecht eines Dorfbewohners, passend zu seinem Vornamen. */
    public static Gender genderOf(UUID uuid) {
        return FEMALE[firstNameIndex(uuid)] ? Gender.FEMALE : Gender.MALE;
    }

    /**
     * Äußeres Erscheinungsbild eines Dorfbewohners, stabil aus der UUID abgeleitet: {@code skin} (Hautton),
     * {@code hair} (Haarfarbe) und {@code style} (Frisur, abhängig vom Geschlecht) sind Indizes in
     * {@link #SKIN_TONES}, {@link #HAIR_COLORS} und {@link #HAIR_STYLES}.
     */
    public record Look(Gender gender, int skin, int hair, int style) {
    }

    public static final int SKIN_TONES = 5;
    public static final int HAIR_COLORS = 7;
    public static final int HAIR_STYLES = 3;

    public static Look lookOf(UUID uuid) {
        // Andere Bits als beim Namen, damit Aussehen und Name unabhängig variieren.
        long mixed = uuid.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 7);
        mixed ^= mixed >>> 29;
        int skin = Math.floorMod(mixed, SKIN_TONES);
        int hair = Math.floorMod(mixed / SKIN_TONES, HAIR_COLORS);
        int style = Math.floorMod(mixed / (SKIN_TONES * HAIR_COLORS), HAIR_STYLES);
        return new Look(genderOf(uuid), skin, hair, style);
    }

    private static int firstNameIndex(UUID uuid) {
        long high = uuid.getMostSignificantBits();
        long low = uuid.getLeastSignificantBits();
        return Math.floorMod(high ^ Long.rotateLeft(low, 17), FIRST_NAMES.length);
    }
}
