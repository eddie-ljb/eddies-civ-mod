package de.etiennebader.civ.server.road;

import de.etiennebader.civ.application.civil.Addresses;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Street;

import java.util.Objects;

/**
 * Straßenpflicht: Grundstücke werden nur an einer Straße zugeschnitten, ausgeschrieben und bebaut. Die Straße muss
 * zuerst gebaut und als solche im Grundbuch der Gemeinde angelegt sein ({@code /civ strasse anlegen}); erst dann
 * folgen Grundstücke und Häuser höchstens {@value Addresses#REACH} Blöcke daneben.
 *
 * <p>Eine angelegte Straße zählt, wenn ihr Wegebau fertig ist. Hat sie gar keinen Wegebau (ein Spieler baute den Weg
 * von Hand und trug ihn danach ein), zählt sie sofort. Die Pflicht lässt sich mit dem Gesetz
 * {@code street_required} abschalten.
 */
public final class StreetAccess {

    private final WorldQueries queries;
    private RoadService roads;
    private boolean enforced = true;

    /**
     * Schaltet die Straßenpflicht für den ganzen Server ab oder wieder an (für Tests, die nichts mit Straßen zu tun
     * haben, und Betreiber, die Altwelten ohne Straßen weiterspielen). Standard: an.
     */
    public void enforced(boolean enforced) {
        this.enforced = enforced;
    }

    public StreetAccess(WorldQueries queries) {
        this.queries = Objects.requireNonNull(queries, "queries");
    }

    /** Der Wegebau, nach dem sich richtet, ob eine Straße fertig ist. */
    public void roads(RoadService roads) {
        this.roads = roads;
    }

    /** Ob die Gemeinde die Straßenpflicht eingeführt hat (Standard: ja). */
    public boolean required(PolityView town) {
        return enforced && queries.effectiveLaws(town).valueOf(StandardParameters.STREET_REQUIRED);
    }

    /** Ob die Fläche an einer gebauten, angelegten Straße liegt, oder die Gemeinde keine Straßenpflicht hat. */
    public boolean served(PolityView town, Area area) {
        if (!required(town)) {
            return true;
        }
        return Addresses.streetsNear(town, area).stream().anyMatch(street -> built(town, street));
    }

    private boolean built(PolityView town, Street street) {
        return roads == null || !roads.hasJob(town.id(), street.name()) || roads.isBuilt(town.id(), street.name());
    }

    /** Reichweite einer Straße in Blöcken, für Meldungen. */
    public static int reach() {
        return Addresses.REACH;
    }
}
