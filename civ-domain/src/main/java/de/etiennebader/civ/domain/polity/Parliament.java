package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Das gewählte Parlament einer Gemeinde oder eines Bundes.
 *
 * <p>Der erste Sitz gehört dem Präsidium: dem Abgeordneten mit den meisten persönlichen Stimmen in der stärksten
 * Fraktion. Fraktionen sind die Parteien der Abgeordneten; wer keiner Partei angehört, ist fraktionslos.
 *
 * @param seats     Sitze in fester Reihenfolge, das Präsidium zuerst; leer, solange kein Parlament gewählt ist
 * @param electedOn Tag der Wahl; leer ohne Parlament
 * @param until     letzter Tag der Wahlperiode; leer, wenn unbefristet oder ohne Parlament
 * @param reserves  Nachrücker: nicht gewählte Kandidaten der Parteilisten in Reihenfolge; wird ein Sitz einer Partei
 *                  frei, rückt der erste Nachrücker derselben Partei nach
 */
public record Parliament(List<Seat> seats, Optional<Day> electedOn, Optional<Day> until, List<Seat> reserves) {

    /** Kein Parlament gewählt. */
    public static final Parliament NONE = new Parliament(List.of(), Optional.empty(), Optional.empty(), List.of());

    /** Ohne Nachrücker. */
    public Parliament(List<Seat> seats, Optional<Day> electedOn, Optional<Day> until) {
        this(seats, electedOn, until, List.of());
    }

    /**
     * Ein Sitz.
     *
     * @param party Partei des Abgeordneten bei der Wahl; leer für Fraktionslose
     * @param votes persönliche Stimmen bei der Wahl, gewichtet (100 = eine Bürgerstimme)
     */
    public record Seat(ActorRef member, Optional<PolityId> party, long votes) {

        public Seat {
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(party, "party");
            if (votes < 0) {
                throw new IllegalArgumentException("Negative Stimmen: " + votes);
            }
        }
    }

    public Parliament {
        seats = List.copyOf(Objects.requireNonNull(seats, "seats"));
        reserves = List.copyOf(Objects.requireNonNull(reserves, "reserves"));
        if (reserves.stream().anyMatch(reserve -> reserve.party().isEmpty())) {
            throw new IllegalArgumentException("Nachrücker ohne Partei");
        }
        List<Seat> seated = seats;
        if (reserves.stream().anyMatch(reserve -> seated.stream()
                .anyMatch(seat -> seat.member().equals(reserve.member())))) {
            throw new IllegalArgumentException("Nachrücker hat schon einen Sitz");
        }
        Objects.requireNonNull(electedOn, "electedOn");
        Objects.requireNonNull(until, "until");
        if (seats.stream().map(Seat::member).distinct().count() != seats.size()) {
            throw new IllegalArgumentException("Doppelter Sitz im Parlament");
        }
        if (!seats.isEmpty() && electedOn.isEmpty()) {
            throw new IllegalArgumentException("Parlament ohne Wahltag");
        }
        if (until.isPresent() && electedOn.isPresent() && until.get().isBefore(electedOn.get())) {
            throw new IllegalArgumentException("Wahlperiode endet vor der Wahl");
        }
    }

    /** Ob ein Parlament mit mindestens einem Sitz besteht. */
    public boolean seated() {
        return !seats.isEmpty();
    }

    public int size() {
        return seats.size();
    }

    public boolean holdsSeat(ActorRef actor) {
        Objects.requireNonNull(actor, "actor");
        return seats.stream().anyMatch(seat -> seat.member().equals(actor));
    }

    public Optional<Seat> seatOf(ActorRef actor) {
        Objects.requireNonNull(actor, "actor");
        return seats.stream().filter(seat -> seat.member().equals(actor)).findFirst();
    }

    /** Wer das Präsidium hat; leer ohne Parlament. */
    public Optional<ActorRef> president() {
        return seats.isEmpty() ? Optional.empty() : Optional.of(seats.getFirst().member());
    }

    /** Sitze je Fraktion in der Reihenfolge ihres ersten Sitzes; Fraktionslose unter {@link Optional#empty()}. */
    public Map<Optional<PolityId>, List<Seat>> factions() {
        Map<Optional<PolityId>, List<Seat>> factions = new LinkedHashMap<>();
        for (Seat seat : seats) {
            factions.computeIfAbsent(seat.party(), ignored -> new ArrayList<>()).add(seat);
        }
        factions.replaceAll((party, members) -> List.copyOf(members));
        return factions;
    }

    /** Ob die Wahlperiode an diesem Tag abgelaufen ist. */
    public boolean expiredOn(Day day) {
        Objects.requireNonNull(day, "day");
        return until.isPresent() && !day.isBefore(until.get());
    }

    /**
     * Ohne diesen Akteur, z. B. weil er die Gemeinde verlassen hat. Hatte er einen Sitz für eine Partei, rückt der erste
     * Nachrücker derselben Partei nach, der dafür in Frage kommt; sonst bleibt der Sitz frei.
     *
     * @param eligible wer nachrücken darf, z. B. nur Mitglieder
     */
    Parliament without(ActorRef actor, Predicate<ActorRef> eligible) {
        List<Seat> remainingReserves = new ArrayList<>(reserves.stream()
                .filter(reserve -> !reserve.member().equals(actor)).toList());
        Optional<Seat> vacated = seatOf(actor);
        if (vacated.isEmpty()) {
            return new Parliament(seats, electedOn, until, remainingReserves);
        }
        List<Seat> remaining = new ArrayList<>();
        for (Seat seat : seats) {
            if (!seat.member().equals(actor)) {
                remaining.add(seat);
                continue;
            }
            Optional<Seat> successor = remainingReserves.stream()
                    .filter(reserve -> reserve.party().equals(seat.party()) && seat.party().isPresent())
                    .filter(reserve -> eligible.test(reserve.member()))
                    .findFirst();
            successor.ifPresent(next -> {
                remaining.add(next);
                remainingReserves.remove(next);
            });
        }
        return remaining.isEmpty() ? NONE : new Parliament(remaining, electedOn, until, remainingReserves);
    }

    /** Wer für einen frei gewordenen Sitz nachrückt, wenn ein Abgeordneter geht. */
    public Optional<Seat> successorOf(ActorRef member) {
        Optional<Seat> seat = seatOf(member);
        if (seat.isEmpty() || seat.get().party().isEmpty()) {
            return Optional.empty();
        }
        return reserves.stream().filter(reserve -> reserve.party().equals(seat.get().party())).findFirst();
    }

    Parliament extendedUntil(Optional<Day> newUntil) {
        return new Parliament(seats, electedOn, newUntil, reserves);
    }
}
