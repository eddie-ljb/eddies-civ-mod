package de.etiennebader.civ.server.land;

import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.territory.ChunkCoord;

import java.util.Objects;
import java.util.Optional;

/**
 * Land vor Wasser: Gemeinden beanspruchen erst Land, und Straßen, Grundstücke und Häuser entstehen nur an Land. Wasser
 * ist erlaubt für Wassergebäude (Leuchttürme, Schiffe, Stege; siehe {@code Blueprint.isWaterBuilding}).
 *
 * <p>Wie viel Wasser eine Fläche hat, weiß nur das Spiel in der Welt: Es setzt dafür eine {@link Survey}. Ohne eine
 * ist alles Land (Tests, Simulation). Ist das Gelände nicht geladen, kennt man den Wasseranteil nicht und die Fläche
 * gilt vorerst nicht als Land; die Dorfbewohner versuchen es später wieder.
 */
public final class LandRules {

    /** Höchster Wasseranteil eines Chunks (Prozent), den eine Gemeinde beansprucht oder in dem sie gegründet wird. */
    public static final int CHUNK_MAX_WATER = 35;
    /** Höchster Wasseranteil eines Baufelds oder Grundstücks. */
    public static final int PLOT_MAX_WATER = 5;
    /** Höchster Wasseranteil einer Straße. */
    public static final int STREET_MAX_WATER = 10;

    /** Was das Spiel über das Gelände weiß. */
    @FunctionalInterface
    public interface Survey {

        /** @return Anteil der Wasserflächen an der Fläche in Prozent; leer, wenn das Gelände nicht geladen ist */
        Optional<Integer> waterPercent(Area area);
    }

    private volatile Survey survey = area -> Optional.of(0);

    /** Das Spiel in der Welt liefert hier den Wasseranteil von Flächen. */
    public void survey(Survey survey) {
        this.survey = Objects.requireNonNull(survey, "survey");
    }

    /** Ob die Fläche höchstens {@code maxWaterPercent} Prozent Wasser hat; unbekannt gilt als nein. */
    public boolean allows(Area area, int maxWaterPercent) {
        return survey.waterPercent(area).map(percent -> percent <= maxWaterPercent).orElse(false);
    }

    /** Ob ein Chunk genug Land ist, um ihn zu beanspruchen. */
    public boolean isLandChunk(ChunkCoord chunk) {
        return allows(Area.ofChunk(chunk), CHUNK_MAX_WATER);
    }

    /** Ob ein Grundstück oder Baufeld auf Land liegt. */
    public boolean isLandPlot(Area area) {
        return allows(area, PLOT_MAX_WATER);
    }

    /** Ob eine Straße auf Land liegt. */
    public boolean isLandStreet(Area area) {
        return allows(area, STREET_MAX_WATER);
    }
}
