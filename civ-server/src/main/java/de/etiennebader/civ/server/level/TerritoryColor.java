package de.etiennebader.civ.server.level;

import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Welche Polity die Farbe eines Gebiets auf Web- und Ingame-Karte bestimmt.
 *
 * <p>Ohne Filter gilt die höchste Ebene eines Bundes (Nation &gt; Land &gt; Gemeinde): Gemeinden desselben Bundes
 * erscheinen zusammenhängend in dessen Farbe. Mit Filter gilt stattdessen die Polity auf genau dieser Ebene, oder,
 * falls die Gemeinde keinem Bund dieser Ebene angehört, die Gemeinde selbst.
 */
public final class TerritoryColor {

    private TerritoryColor() {
    }

    /**
     * @param town      die Gemeinde, der das Gebiet gehört
     * @param ancestors ihre Bünde von unten nach oben, siehe {@code WorldQueries#ancestorsOf}
     * @param level     gewünschte Ebene (z. B. {@code nation}); leer für die höchste Ebene
     * @return die Polity, deren Farbe das Gebiet zeigt
     */
    public static PolityId groupOf(PolityView town, List<PolityView> ancestors, Optional<String> level) {
        Objects.requireNonNull(town, "town");
        Objects.requireNonNull(ancestors, "ancestors");
        Objects.requireNonNull(level, "level");
        if (level.isPresent()) {
            return ancestors.stream().filter(ancestor -> ancestor.level().equals(level.get())).findFirst()
                    .map(PolityView::id).orElseGet(town::id);
        }
        return ancestors.isEmpty() ? town.id() : ancestors.get(ancestors.size() - 1).id();
    }
}
