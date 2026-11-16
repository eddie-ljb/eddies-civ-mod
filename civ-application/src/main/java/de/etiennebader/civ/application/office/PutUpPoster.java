package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.federation.Federations;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Poster;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.Claim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Wahlplakate (Litfaßsäulen) aufstellen und abbauen.
 *
 * <p>Ein Plakat wirbt für den, der es aufstellt, wenn er bei einer laufenden Wahl der Gemeinde oder eines ihrer Bünde
 * kandidiert, sonst für seine Partei. Außerhalb eines Gemeindegebiets oder ohne beides ist es nur Deko.
 */
public final class PutUpPoster {

    /** Das aufgestellte Plakat und die Gemeinde, in der es steht. */
    public record Result(PolityId town, Poster poster) {
    }

    private final Aggregates aggregates;

    public PutUpPoster(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /** @return leer, wenn das Plakat für niemanden wirbt */
    public Optional<Result> execute(BlockPosition position, ActorRef owner, Day day) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(day, "day");
        Optional<PolityId> town = aggregates.territory().claimAt(position.chunk()).map(Claim::polity);
        if (town.isEmpty()) {
            return Optional.empty();
        }
        Polity polity = aggregates.polity(town.get());
        Optional<ActorRef> subject = subjectFor(polity, owner);
        if (subject.isEmpty()) {
            return Optional.empty();
        }
        Poster poster = new Poster(position, subject.get(), owner, day);
        if (!polity.putUpPoster(poster)) {
            return Optional.empty();
        }
        aggregates.commit(polity);
        return Optional.of(new Result(polity.id(), poster));
    }

    /** Baut das Plakat an dieser Stelle ab, falls dort eines stand. */
    public boolean takeDown(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        for (Polity polity : aggregates.repositories().polities().findAll()) {
            if (polity.takeDownPoster(position)) {
                aggregates.commit(polity);
                return true;
            }
        }
        return false;
    }

    private Optional<ActorRef> subjectFor(Polity town, ActorRef owner) {
        List<PolityView> electing = new ArrayList<>();
        electing.add(town);
        electing.addAll(Federations.ancestorsOf(aggregates.repositories().polities().findAll(), town.id()));
        boolean candidate = electing.stream()
                .flatMap(polity -> polity.elections().stream())
                .anyMatch(election -> election.isCandidate(owner));
        if (candidate) {
            return Optional.of(owner);
        }
        return new WorldQueries(aggregates.repositories()).partyOf(owner)
                .filter(party -> party.kind() == PolityKind.PARTY)
                .map(PolityView::asActor);
    }
}
