package de.etiennebader.civ.application.law;

import de.etiennebader.civ.application.office.NotOfficeHolderException;
import de.etiennebader.civ.application.polity.Majorities;
import de.etiennebader.civ.application.polity.ProposeMotion;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.UnknownStatuteException;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;

import java.util.Objects;
import java.util.Optional;

/**
 * Hebt einen Paragraphen auf: sofort durch die Spitze oder per Antrag, wenn abgestimmt werden muss.
 */
public final class RepealParagraph {

    public record Command(PolityId polity, ActorRef author, StatuteId statute, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(author, "author");
            Objects.requireNonNull(statute, "statute");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;
    private final ProposeMotion proposeMotion;

    public RepealParagraph(Aggregates aggregates, ProposeMotion proposeMotion) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.proposeMotion = Objects.requireNonNull(proposeMotion, "proposeMotion");
    }

    /**
     * @return der eröffnete Antrag, falls abgestimmt wird; leer, wenn sofort aufgehoben
     * @throws UnknownStatuteException  wenn der Paragraph nicht gilt
     * @throws NotOfficeHolderException wenn ohne Abstimmung jemand anderes als die Spitze aufheben will
     */
    public Optional<ProposeMotion.Result> execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        EnactedStatute enacted = polity.laws().inForce().stream()
                .filter(law -> law.statute().id().equals(command.statute()))
                .findFirst()
                .orElseThrow(() -> new UnknownStatuteException(command.statute()));
        Motion.Repeal motion = new Motion.Repeal(command.statute(), enacted.statute().title());
        if (Majorities.voteRequired(polity, motion)) {
            return Optional.of(proposeMotion.execute(new ProposeMotion.Command(command.polity(), command.author(),
                    motion, command.day())));
        }
        if (!polity.holds(command.author(), Office.MAYOR)) {
            throw new NotOfficeHolderException(command.author(), Office.MAYOR);
        }
        polity.repeal(command.statute(), command.day());
        aggregates.commit(polity);
        return Optional.empty();
    }
}
