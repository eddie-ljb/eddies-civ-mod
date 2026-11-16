package de.etiennebader.civ.application.federation;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Gemeinde oder ein Bund tritt einem höheren Bund bei. Ob der Bund das will (Einladung) und ob die Ebenen passen,
 * prüft der Aufrufer.
 */
public final class JoinFederation {

    public record Command(PolityId federation, PolityId constituent, Day day) {

        public Command {
            Objects.requireNonNull(federation, "federation");
            Objects.requireNonNull(constituent, "constituent");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public JoinFederation(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws NotAFederationException      wenn das Ziel kein Bund ist
     * @throws AlreadyInFederationException wenn die Gliedkörperschaft schon einem Bund angehört
     * @throws FederationCycleException     wenn der Bund selbst in der Gliedkörperschaft steckt
     */
    public void execute(Command command) {
        Polity federation = aggregates.polity(command.federation());
        Polity constituent = aggregates.polity(command.constituent());
        if (federation.kind() != PolityKind.FEDERATION) {
            throw new NotAFederationException(federation.name());
        }
        if (constituent.kind() == PolityKind.PARTY) {
            throw new NotAMunicipalityException(constituent.name());
        }
        var all = aggregates.repositories().polities().findAll();
        Federations.parentOf(all, constituent.id()).ifPresent(parent -> {
            throw new AlreadyInFederationException(constituent.name(), parent.name());
        });
        boolean cycle = federation.id().equals(constituent.id())
                || Federations.ancestorsOf(all, federation.id()).stream()
                .anyMatch(ancestor -> ancestor.id().equals(constituent.id()));
        if (cycle) {
            throw new FederationCycleException(constituent.name(), federation.name());
        }
        Federations.attach(aggregates, federation, constituent, command.day());
    }
}
