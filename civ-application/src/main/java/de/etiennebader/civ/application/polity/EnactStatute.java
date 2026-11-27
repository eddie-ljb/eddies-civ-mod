package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;

import java.util.List;
import java.util.Objects;

/**
 * Erlässt ein Gesetz. Abstimmungen und Organe kommen in Phase 1 als vorgeschalteter Schritt hinzu.
 */
public final class EnactStatute {

    public record Command(PolityId polity, String lawName, String partName, String title,
                          List<ParameterValue<?>> values, Day day) {

        /** Ohne Gesetz und Teil. */
        public Command(PolityId polity, String title, List<ParameterValue<?>> values, Day day) {
            this(polity, "", "", title, values, day);
        }

        public Command {
            Objects.requireNonNull(lawName, "lawName");
            Objects.requireNonNull(partName, "partName");
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(title, "title");
            values = List.copyOf(Objects.requireNonNull(values, "values"));
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public EnactStatute(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /**
     * @return die ID des neuen Gesetzes
     */
    public StatuteId execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        Statute statute = new Statute(StatuteId.generate(ids), command.lawName(), command.partName(), command.title(),
                "", command.values());
        polity.enact(statute, command.day());
        aggregates.commit(polity);
        return statute.id();
    }
}
