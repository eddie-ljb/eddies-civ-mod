package de.etiennebader.civ.application.law;

import de.etiennebader.civ.application.office.NotOfficeHolderException;
import de.etiennebader.civ.application.polity.Majorities;
import de.etiennebader.civ.application.polity.ProposeMotion;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.LawRecognizer;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Paragraph für das Gesetzbuch: Titel und Freitext. Was im Text erkannt wird, setzt Regelungen; der Rest gilt als
 * Text. Braucht die Änderung eine Abstimmung (Abstimmungspflicht oder Verfassungsartikel nach dem Ausrufen), wird
 * ein Antrag eröffnet, sonst erlässt die Spitze den Paragraphen sofort.
 */
public final class EnactParagraph {

    public record Command(PolityId polity, ActorRef author, String title, String text, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(author, "author");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(day, "day");
        }
    }

    /**
     * @param statute     der erlassene Paragraph, wenn verordnet
     * @param proposal    der eröffnete Antrag, wenn abgestimmt wird
     * @param recognition was im Text erkannt wurde
     */
    public record Result(Optional<StatuteId> statute, Optional<ProposeMotion.Result> proposal,
                         LawRecognizer.Recognition recognition) {
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;
    private final ProposeMotion proposeMotion;

    public EnactParagraph(Aggregates aggregates, IdGenerator ids, ProposeMotion proposeMotion) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.proposeMotion = Objects.requireNonNull(proposeMotion, "proposeMotion");
    }

    /**
     * @throws NotOfficeHolderException wenn ohne Abstimmung jemand anderes als die Spitze erlassen will
     */
    public Result execute(Command command) {
        LawRecognizer.Recognition recognition = LawRecognizer.recognize(command.text());
        Motion.Enact motion = new Motion.Enact(command.title(), command.text(), recognition.values());
        Polity polity = aggregates.polity(command.polity());
        if (Majorities.voteRequired(polity, motion)) {
            ProposeMotion.Result proposed = proposeMotion.execute(new ProposeMotion.Command(command.polity(),
                    command.author(), motion, command.day()));
            return new Result(Optional.empty(), Optional.of(proposed), recognition);
        }
        if (!polity.holds(command.author(), Office.MAYOR)) {
            throw new NotOfficeHolderException(command.author(), Office.MAYOR);
        }
        Statute statute = new Statute(StatuteId.generate(ids), motion.title(), motion.text(), motion.values());
        polity.enact(statute, command.day());
        aggregates.commit(polity);
        return new Result(Optional.of(statute.id()), Optional.empty(), recognition);
    }
}
