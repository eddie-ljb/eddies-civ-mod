package de.etiennebader.civ.application.office;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.PolityView;

import java.util.Comparator;
import java.util.Optional;

/**
 * Entscheidet, wen ein wahlberechtigter Dorfbewohner wählt.
 *
 * <p>Im Spiel entscheidet {@code PersonalityVoter} nach Persönlichkeit und Zufriedenheit. {@link #STABLE} wählt
 * dagegen ohne politische Vorliebe immer denselben Kandidaten, abhängig nur von beider Identität; für Tests.
 */
@FunctionalInterface
public interface VillagerVoter {

    /** @return leer, wenn der Dorfbewohner nicht wählt */
    Optional<ActorRef> choose(PersonRef villager, Election election, PolityView polity);

    VillagerVoter STABLE = (villager, election, polity) -> election.candidates().stream()
            .max(Comparator.comparingLong(candidate -> affinity(villager, candidate)));

    private static long affinity(PersonRef villager, ActorRef candidate) {
        long mixed = villager.uuid().getMostSignificantBits() ^ candidate.uuid().getLeastSignificantBits()
                ^ Long.rotateLeft(villager.uuid().getLeastSignificantBits() ^ candidate.uuid().getMostSignificantBits(),
                        17);
        // SplitMix64-Finalizer: verteilt ähnliche UUIDs gleichmäßig
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
