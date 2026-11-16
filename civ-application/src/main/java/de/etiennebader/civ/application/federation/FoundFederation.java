package de.etiennebader.civ.application.federation;

import de.etiennebader.civ.application.polity.PolityNameTakenException;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.NotAMemberException;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Gründet einen Bund (z. B. ein Land) aus einer Gemeinde oder einem niedrigeren Bund, der noch keinem Bund angehört.
 * Die Leute der Gliedkörperschaft werden Mitglieder des Bundes; wer gründet, führt ihn.
 *
 * <p>Ob die Ebene höher ist als die der Gliedkörperschaft, prüft der Aufrufer, der die Ebenen kennt.
 */
public final class FoundFederation {

    public record Command(PolityName name, String level, PolityId constituent, ActorRef head, Day day) {

        public Command {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(constituent, "constituent");
            Objects.requireNonNull(head, "head");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public FoundFederation(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /**
     * @return die ID des neuen Bundes
     * @throws PolityNameTakenException     wenn es den Namen schon gibt
     * @throws AlreadyInFederationException wenn die Gliedkörperschaft schon einem Bund angehört
     * @throws NotAMemberException          wenn der Gründer nicht zur Gliedkörperschaft gehört
     */
    public PolityId execute(Command command) {
        if (new WorldQueries(aggregates.repositories()).polityNamed(command.name().value()).isPresent()) {
            throw new PolityNameTakenException(command.name());
        }
        Polity constituent = aggregates.polity(command.constituent());
        if (constituent.kind() == PolityKind.PARTY) {
            throw new NotAMunicipalityException(constituent.name());
        }
        Federations.parentOf(aggregates.repositories().polities().findAll(), constituent.id()).ifPresent(parent -> {
            throw new AlreadyInFederationException(constituent.name(), parent.name());
        });
        if (!constituent.isMember(command.head())) {
            throw new NotAMemberException(command.head());
        }
        PolityId id = PolityId.generate(ids);
        Polity federation = Polity.foundFederation(id, command.name(), command.level(), constituent.asActor(),
                command.head(), constituent.currency(), command.day());
        for (Membership person : Federations.people(constituent)) {
            if (!federation.isMember(person.member())) {
                federation.admit(person.member(), person.type(), command.day());
            }
        }
        aggregates.commit(federation);
        aggregates.commit(LandRegistry.open(id));
        return id;
    }
}
