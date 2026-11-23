package de.etiennebader.civ.domain.religion;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Register aller Religionen. Klein genug, um als Ganzes gespeichert zu werden, nach dem Vorbild von
 * {@code CivicRegistry}.
 */
public final class ReligionRegistry extends AggregateRoot<ReligionEvent> implements ReligionView {

    public static final int MAX_RELIGIONS = 200;

    private final List<Religion> religions = new ArrayList<>();

    private ReligionRegistry() {
    }

    public static ReligionRegistry empty() {
        return new ReligionRegistry();
    }

    public static ReligionRegistry restore(Collection<Religion> religions) {
        ReligionRegistry registry = new ReligionRegistry();
        registry.religions.addAll(religions);
        return registry;
    }

    /** @throws IllegalArgumentException wenn der Name schon vergeben oder {@link #MAX_RELIGIONS} erreicht ist */
    public Religion found(ReligionId id, String name, ActorRef founder, boolean monotheistic, String scriptureName,
                          String templeName, String language, Day day) {
        Objects.requireNonNull(day, "day");
        if (religionNamed(name).isPresent()) {
            throw new IllegalArgumentException("Religion gibt es schon: " + name);
        }
        if (religions.size() >= MAX_RELIGIONS) {
            throw new IllegalStateException("Höchstens " + MAX_RELIGIONS + " Religionen");
        }
        Religion religion = Religion.found(id, name, founder, monotheistic, scriptureName, templeName, language, day);
        religions.add(religion);
        raise(new ReligionEvent.Founded(id, name, founder, day));
        return religion;
    }

    /** Fügt eine Konfession hinzu. */
    public Religion addDenomination(ReligionId id, Denomination denomination, Day day) {
        return replace(require(id).withDenomination(denomination), day);
    }

    /**
     * Setzt das Klerus-Oberhaupt und seinen Machtgrad. Nur der Gründer oder das aktuelle Oberhaupt dürfen das.
     *
     * @throws IllegalArgumentException wenn {@code appointer} keine Befugnis dazu hat
     */
    public Religion appointClergy(ReligionId id, ActorRef appointer, ActorRef head, int power, Day day) {
        Objects.requireNonNull(head, "head");
        Religion found = require(id);
        requireAuthority(found, appointer);
        return replace(found.withClergy(Optional.of(head), power), day);
    }

    /** Macht das Klerusamt vakant. */
    public Religion vacateClergy(ReligionId id, ActorRef remover, Day day) {
        Religion found = require(id);
        requireAuthority(found, remover);
        return replace(found.withClergy(Optional.empty(), 0), day);
    }

    /**
     * Ruft eine Wahl um das Klerusamt aus, offen für jeden Spieler oder Dorfbewohner.
     *
     * @throws IllegalStateException wenn schon eine Wahl läuft
     */
    public Religion callClergyElection(ReligionId id, Day closesOn, Day day) {
        Religion found = require(id);
        if (found.election().isPresent()) {
            throw new IllegalStateException("Klerus-Wahl läuft bereits: " + found.name());
        }
        return replace(found.withElection(Optional.of(ClergyElection.open(closesOn))), day);
    }

    /** @throws IllegalStateException wenn keine Wahl läuft oder er schon kandidiert */
    public Religion nominateClergy(ReligionId id, ActorRef candidate, Day day) {
        Religion found = require(id);
        return replace(found.withElection(Optional.of(requireElection(found).withCandidate(candidate))), day);
    }

    /** @throws IllegalArgumentException wenn der Kandidat nicht kandidiert */
    public Religion castClergyVote(ReligionId id, ActorRef voter, ActorRef candidate, Day day) {
        Religion found = require(id);
        ClergyElection election = requireElection(found);
        if (!election.candidates().contains(candidate)) {
            throw new IllegalArgumentException("Kandidiert nicht: " + candidate);
        }
        return replace(found.withElection(Optional.of(election.withBallot(voter, candidate))), day);
    }

    /**
     * Beendet die Klerus-Wahl. Bei einem eindeutigen Sieger übernimmt er das Amt mit dem bisherigen Machtgrad; bei
     * Gleichstand oder ohne Stimmen bleibt das Amt, wie es ist.
     *
     * @throws IllegalStateException wenn keine Wahl läuft
     */
    public Religion closeClergyElection(ReligionId id, Day day) {
        Religion found = require(id);
        ClergyElection election = requireElection(found);
        Religion withResult = election.winner().map(winner -> found.withClergy(Optional.of(winner), found.clergyPower()))
                .orElse(found);
        return replace(withResult.withElection(Optional.empty()), day);
    }

    /** @return ob es die Religion gab */
    public boolean dissolve(ReligionId id, Day day) {
        Objects.requireNonNull(day, "day");
        Optional<Religion> found = religion(Objects.requireNonNull(id, "id"));
        if (found.isEmpty()) {
            return false;
        }
        religions.remove(found.get());
        raise(new ReligionEvent.Dissolved(id, day));
        return true;
    }

    private static void requireAuthority(Religion religion, ActorRef actor) {
        Objects.requireNonNull(actor, "actor");
        boolean allowed = religion.founder().equals(actor) || religion.clergyHead().filter(actor::equals).isPresent();
        if (!allowed) {
            throw new IllegalArgumentException("Keine Befugnis in dieser Religion: " + actor);
        }
    }

    private Religion require(ReligionId id) {
        return religion(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("Unbekannte Religion: " + id));
    }

    private ClergyElection requireElection(Religion religion) {
        return religion.election().orElseThrow(() -> new IllegalStateException("Keine laufende Klerus-Wahl: " + religion.name()));
    }

    private Religion replace(Religion updated, Day day) {
        Objects.requireNonNull(day, "day");
        for (int index = 0; index < religions.size(); index++) {
            if (religions.get(index).id().equals(updated.id())) {
                religions.set(index, updated);
                break;
            }
        }
        raise(new ReligionEvent.Changed(updated.id(), "state", day));
        return updated;
    }

    @Override
    public List<Religion> religions() {
        return List.copyOf(religions);
    }
}
