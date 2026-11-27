package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.List;
import java.util.Objects;

/**
 * Eine aufgestellte Flagge oder ein aufgestelltes Wappen im Gebiet einer Polity. Hoheitszeichen stärken die Treue der
 * Dorfbewohner zu dieser Polity, allerdings nur bis zu einer Obergrenze, damit man die Treue nicht mit Flaggen zukleistert.
 *
 * @param position Ort des Blocks
 * @param polity   Polity, in deren Gebiet es steht und der es nützt
 * @param kind     Flagge oder Wappen
 * @param placedOn Tag des Aufstellens
 */
public record Emblem(BlockPosition position, PolityId polity, Kind kind, Day placedOn) {

    /** Höchstens so viele Hoheitszeichen je Polity zählen und werden gespeichert. */
    public static final int MAX_PER_POLITY = 24;
    /** Treue-Punkte je Flagge. */
    public static final int FLAG_LOYALTY = 1;
    /** Treue-Punkte je Wappen. */
    public static final int ARMS_LOYALTY = 2;
    /** Mehr als so viele Punkte bringen alle Hoheitszeichen zusammen nicht. */
    public static final int LOYALTY_CAP = 8;

    /** Art des Hoheitszeichens. */
    public enum Kind {
        FLAG, COAT_OF_ARMS
    }

    public Emblem {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(placedOn, "placedOn");
    }

    /** Treue-Bonus aller diesen Hoheitszeichen zusammen, gedeckelt auf {@link #LOYALTY_CAP}. */
    public static int loyaltyBonus(List<Emblem> emblems) {
        int points = emblems.stream()
                .mapToInt(emblem -> emblem.kind() == Kind.FLAG ? FLAG_LOYALTY : ARMS_LOYALTY).sum();
        return Math.min(LOYALTY_CAP, points);
    }
}
