package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Die politische Richtung, das Wappen und das Geltungsgebiet einer Partei.
 *
 * @param order   -100 (Freiheit) bis +100 (Ordnung)
 * @param economy -100 (niedrige Steuern) bis +100 (Ausbau und Investitionen)
 * @param emblem  Item-ID als Logo, z. B. {@code minecraft:red_banner}
 * @param area    Polity, auf deren Ebene und Gebiet (sie selbst und alle untergeordneten Glieder) die Partei
 *                wählbar ist; leer heißt überall wählbar (Standard, unverändertes Verhalten).
 */
public record PartyProfile(int order, int economy, String emblem, Optional<PolityId> area) {

    public static final int MAX = 100;
    public static final String DEFAULT_EMBLEM = "minecraft:white_banner";
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
    /** Unterhalb dieser Ausprägung gilt eine Richtung als Mitte. */
    private static final int CENTRE = 20;

    public PartyProfile {
        if (Math.abs(order) > MAX || Math.abs(economy) > MAX) {
            throw new IllegalArgumentException("Richtung außerhalb von ±" + MAX + ": " + order + ", " + economy);
        }
        Objects.requireNonNull(emblem, "emblem");
        if (!ITEM_ID.matcher(emblem).matches()) {
            throw new IllegalArgumentException("Ungültiges Wappen: " + emblem);
        }
        Objects.requireNonNull(area, "area");
    }

    /** Ohne Geltungsgebiet (überall wählbar), für bestehenden Code und neu gegründete Parteien. */
    public PartyProfile(int order, int economy, String emblem) {
        this(order, economy, emblem, Optional.empty());
    }

    public PartyProfile withDirection(int newOrder, int newEconomy) {
        return new PartyProfile(newOrder, newEconomy, emblem, area);
    }

    public PartyProfile withEmblem(String newEmblem) {
        return new PartyProfile(order, economy, newEmblem, area);
    }

    /** Geltungsgebiet setzen oder mit {@link Optional#empty()} auf "überall" zurücksetzen. */
    public PartyProfile withArea(Optional<PolityId> newArea) {
        return new PartyProfile(order, economy, emblem, newArea);
    }

    /** Das Wahlprogramm, das der stärksten Richtung entspricht; in der Mitte {@link Platform#NONE}. */
    public Platform platform() {
        if (Math.max(Math.abs(order), Math.abs(economy)) < CENTRE) {
            return Platform.NONE;
        }
        if (Math.abs(order) >= Math.abs(economy)) {
            return order > 0 ? Platform.ORDER : Platform.FREEDOM;
        }
        return economy > 0 ? Platform.INVESTMENT : Platform.LOW_TAXES;
    }

    /** Abstand zu einer Position auf denselben Achsen. */
    public double distanceTo(int otherOrder, int otherEconomy) {
        return Math.hypot(order - otherOrder, economy - otherEconomy);
    }
}
