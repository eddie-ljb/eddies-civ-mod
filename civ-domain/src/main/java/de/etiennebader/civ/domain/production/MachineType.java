package de.etiennebader.civ.domain.production;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maschinentypen: je {@link Branch} mehrere. Jede Maschine ist ein eigener Block; ist sie aufgestellt und
 * intakt, steigert sie den Ertrag der Firma um {@link #outputPercent()} Prozent je Ausbaustufe. Sie verschleißt
 * täglich und muss gewartet werden ({@link #maintenanceCost()}).
 */
public enum MachineType {

    SAWMILL(Branch.FORESTRY, "saegewerk", 3_000, 12, 4),
    LOG_SPLITTER(Branch.FORESTRY, "holzspalter", 2_000, 8, 3),
    SHEARING_STATION(Branch.SHEEP, "schurstation", 2_500, 10, 3),
    WOOL_LOOM(Branch.SHEEP, "webstuhl", 3_500, 13, 4),
    SEED_DRILL(Branch.FARM, "saemaschine", 2_500, 10, 3),
    HARVESTER(Branch.FARM, "erntemaschine", 4_500, 16, 5),
    ORE_CRUSHER(Branch.MINE, "erzbrecher", 4_000, 14, 5),
    MINE_DRILL(Branch.MINE, "bohrer", 3_000, 11, 4),
    NET_WINCH(Branch.FISHERY, "netzwinde", 2_500, 10, 3),
    FISH_SMOKER(Branch.FISHERY, "raeucherofen", 3_000, 12, 3),
    MILKING_MACHINE(Branch.RANCH, "melkmaschine", 3_000, 11, 3),
    FEED_MIXER(Branch.RANCH, "futtermischer", 2_500, 9, 3),
    CLAY_PRESS(Branch.BRICKWORKS, "tonpresse", 3_000, 12, 4),
    BRICK_KILN(Branch.BRICKWORKS, "brennofen", 4_000, 15, 5),
    FRUIT_PRESS(Branch.ORCHARD, "obstpresse", 2_500, 10, 3),
    HONEY_EXTRACTOR(Branch.ORCHARD, "honigschleuder", 3_000, 12, 3),
    CONCRETE_MIXER(Branch.CONSTRUCTION, "betonmischer", 3_500, 12, 4),
    CRANE(Branch.CONSTRUCTION, "baukran", 5_000, 18, 5),
    WORKBENCH_RIG(Branch.MAINTENANCE, "werkbank", 2_500, 10, 2),
    DIAGNOSTIC_STAND(Branch.MAINTENANCE, "pruefstand", 3_500, 14, 2);

    /** Höchste Ausbaustufe einer Maschine. */
    public static final int MAX_LEVEL = 5;
    /** Verschleiß, ab dem eine Maschine ausfällt. */
    public static final int BROKEN = 100;
    /** So viele Maschinen darf eine Firma höchstens besitzen. */
    public static final int MAX_PER_COMPANY = 8;

    private final Branch branch;
    private final String id;
    private final long price;
    private final int outputPercent;
    private final int wearPerDay;

    MachineType(Branch branch, String id, long price, int outputPercent, int wearPerDay) {
        this.branch = branch;
        this.id = id;
        this.price = price;
        this.outputPercent = outputPercent;
        this.wearPerDay = wearPerDay;
    }

    public Branch branch() {
        return branch;
    }

    /** Kennung für Befehle, z. B. {@code saegewerk}. */
    public String id() {
        return id;
    }

    /** Kennung des Blocks und Items, z. B. {@code machine_sawmill}. */
    public String blockId() {
        return "machine_" + name().toLowerCase(Locale.ROOT);
    }

    /** Kaufpreis (kleinste Währungseinheit); der Ausbau auf Stufe n+1 kostet {@code n} mal so viel. */
    public long price() {
        return price;
    }

    public long upgradeCost(int fromLevel) {
        return price * fromLevel;
    }

    /** Ertragssteigerung in Prozent je Ausbaustufe. */
    public int outputPercent() {
        return outputPercent;
    }

    public int wearPerDay() {
        return wearPerDay;
    }

    /** Preis einer vollständigen Wartung: ein Fünftel des Kaufpreises, mehr bei höherer Stufe. */
    public long maintenanceCost(int level, int wear) {
        return Math.max(1, price / 5 * level * wear / BROKEN);
    }

    public static List<MachineType> of(Branch branch) {
        return Arrays.stream(values()).filter(type -> type.branch == branch).toList();
    }

    public static Optional<MachineType> parse(String text) {
        String key = text.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.id.equals(key) || type.name().equalsIgnoreCase(key)).findFirst();
    }
}
