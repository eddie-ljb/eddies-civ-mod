package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.LawRecognizer;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.Promise;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Kandidat gibt ein Wahlversprechen. Das Gesetzbuch erkennt darin messbare Regelungen; daran misst sich später
 * seine Bilanz ({@link de.etiennebader.civ.application.population.Campaign}).
 */
public final class MakePromise {

    /**
     * @param polity die Gemeinde oder der Bund, in dem gewählt wird
     */
    public record Command(PolityId polity, ActorRef candidate, String text, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public MakePromise(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @return das gespeicherte Versprechen
     * @throws NotRunningException wenn der Akteur bei keiner laufenden Wahl der Polity kandidiert
     */
    public Promise execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        Optional<Election> election = polity.elections().stream()
                .filter(open -> open.isCandidate(command.candidate()))
                .findFirst();
        if (election.isEmpty()) {
            throw new NotRunningException();
        }
        String text = command.text().strip();
        if (text.length() > Promise.MAX_TEXT) {
            text = text.substring(0, Promise.MAX_TEXT).strip();
        }
        Promise promise = new Promise(command.candidate(), election.get().office(), text,
                LawRecognizer.recognize(text).values(), command.day());
        polity.promise(promise);
        aggregates.commit(polity);
        return promise;
    }
}
