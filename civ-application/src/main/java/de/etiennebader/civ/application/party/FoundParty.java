package de.etiennebader.civ.application.party;

import de.etiennebader.civ.application.polity.PolityNameTakenException;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Objects;

/**
 * Gründet eine Partei. Der Gründer wird Vorsitz; weitere Gründungsmitglieder (z. B. Dorfbewohner) treten gleich bei.
 */
public final class FoundParty {

    /**
     * @param cofounders weitere Gründungsmitglieder, die noch keiner Partei angehören dürfen
     */
    public record Command(PolityName name, ActorRef founder, PartyProfile profile, CurrencyId currency,
                          List<? extends ActorRef> cofounders, Day day) {

        public Command(PolityName name, ActorRef founder, PartyProfile profile, CurrencyId currency, Day day) {
            this(name, founder, profile, currency, List.of(), day);
        }

        public Command {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(founder, "founder");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(currency, "currency");
            cofounders = List.copyOf(Objects.requireNonNull(cofounders, "cofounders"));
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public FoundParty(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /**
     * @throws PolityNameTakenException wenn es den Namen schon gibt
     * @throws AlreadyInPartyException  wenn ein Gründungsmitglied schon einer Partei angehört
     */
    public PolityId execute(Command command) {
        if (new WorldQueries(aggregates.repositories()).polityNamed(command.name().value()).isPresent()) {
            throw new PolityNameTakenException(command.name());
        }
        var all = aggregates.repositories().polities().findAll();
        Parties.partyOf(all, command.founder()).ifPresent(party -> {
            throw new AlreadyInPartyException(party.name());
        });
        for (ActorRef cofounder : command.cofounders()) {
            Parties.partyOf(all, cofounder).ifPresent(party -> {
                throw new AlreadyInPartyException(party.name());
            });
        }
        PolityId id = PolityId.generate(ids);
        Polity party = Polity.foundParty(id, command.name(), command.founder(), command.profile(), command.currency(),
                command.day());
        for (ActorRef cofounder : command.cofounders()) {
            if (!party.isMember(cofounder)) {
                party.admit(cofounder, MembershipType.CITIZEN, command.day());
            }
        }
        aggregates.commit(party);
        aggregates.commit(LandRegistry.open(id));
        return id;
    }
}
