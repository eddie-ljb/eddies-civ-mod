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
        long high = uuid.getMostSignificantBits();
        long low = uuid.getLeastSignificantBits();
        int first = Math.floorMod(high ^ Long.rotateLeft(low, 17), FIRST_NAMES.length);
        int last = Math.floorMod(low ^ Long.rotateLeft(high, 31), LAST_NAMES.length);
        return FIRST_NAMES[first] + " " + LAST_NAMES[last];
    }
}
