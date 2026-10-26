package de.etiennebader.civ.application.land;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Gemeinde schneidet in ihrem Gebiet ein Grundstück zu. Es gehört zunächst ihr selbst.
 */
public final class CreatePlot {

    public record Command(PolityId polity, Area area, PlotType type, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(area, "area");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public CreatePlot(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /**
     * @return die ID des neuen Grundstücks
     */
    public PlotId execute(Command command) {
        aggregates.polity(command.polity());
        LandRegistry land = aggregates.land(command.polity());
        PlotId plotId = PlotId.generate(ids);
        land.createPlot(plotId, command.area(), command.type(), aggregates.territory(), command.day());
        aggregates.commit(land);
        return plotId;
    }
}
