package de.etiennebader.civ.server.construction;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.DimensionId;

import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.TreeMap;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Ein Bauauftrag: der Bauherr beauftragt eine Baufirma, einen Plan auf seinem Grundstück zu errichten.
 *
 * @param number   laufende Nummer, die Spieler in Befehlen nennen
 * @param originX  Ecke des Plans (kleinste Koordinaten), meist die Ecke des Grundstücks
 * @param originY  Höhe der untersten Ebene; leer, bis das Spiel die Geländehöhe festgelegt hat
 * @param total    Zahl der zu setzenden Blöcke
 * @param price    Gesamtpreis in kleinster Währungseinheit, inklusive Material und Lohn der Bauarbeiter
 * @param paid     bisher an die Firma gezahlt
 * @param rotation Drehung des Plans in Vierteln im Uhrzeigersinn (0 bis 3)
 * @param selfSupplied ob der Bauherr das Material selbst liefert; dann kostet nur die Arbeit
 * @param depot    vom Bauherrn gelieferte, noch nicht verbaute Baustoffe (Blockname mit Anzahl)
 */
public record ConstructionProject(UUID id, int number, PolityId town, PlotId plot, ActorRef client, PolityId builder,
                                  String blueprint, DimensionId dimension, int originX, OptionalInt originY,
                                  int originZ, Status status, int placed, int total, long price, long paid,
                                  Day orderedOn, int rotation, boolean selfSupplied, Map<String, Integer> depot,
                                  Optional<PlayerRef> contractor, Contractor contractorState) {

    /**
     * Wer baut: die Baufirma mit ihren Bauarbeitern ({@link #NONE}) oder ein Spieler als Auftragnehmer der Firma, der
     * selbst baut und nach verifiziertem Baufortschritt bezahlt wird.
     */
    public enum Contractor {
        NONE,
        /** Der Bauherr hat den Auftrag so gestellt; die Firma muss zustimmen. */
        FIRM_REVIEW,
        /** Die Firma hat einen Spieler als Auftragnehmer vorgesehen; er muss annehmen. */
        PLAYER_REVIEW,
        /** Der Spieler hat angenommen und baut. */
        ACTIVE
    }

    /** Auftrag ohne Auftragnehmer: die Firma baut. */
    public ConstructionProject(UUID id, int number, PolityId town, PlotId plot, ActorRef client, PolityId builder,
                               String blueprint, DimensionId dimension, int originX, OptionalInt originY, int originZ,
                               Status status, int placed, int total, long price, long paid, Day orderedOn,
                               int rotation, boolean selfSupplied, Map<String, Integer> depot) {
        this(id, number, town, plot, client, builder, blueprint, dimension, originX, originY, originZ, status, placed,
                total, price, paid, orderedOn, rotation, selfSupplied, depot, Optional.empty(), Contractor.NONE);
    }

    /** Stand des Auftrags. */
    public enum Status {
        /** Wartet auf die Genehmigung des Bauamts. */
        AWAITING_PERMIT,
        BUILDING,
        DONE,
        REJECTED,
        CANCELLED;

        /** Ob noch gebaut wird oder gebaut werden soll. */
        public boolean isActive() {
            return this == AWAITING_PERMIT || this == BUILDING;
        }
    }

    public ConstructionProject {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(town, "town");
        Objects.requireNonNull(plot, "plot");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(originY, "originY");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(orderedOn, "orderedOn");
        Objects.requireNonNull(contractor, "contractor");
        Objects.requireNonNull(contractorState, "contractorState");
        depot = Map.copyOf(new TreeMap<>(depot));
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("rotation");
        }
        if (placed < 0 || placed > total || price < 0 || paid < 0) {
            throw new IllegalArgumentException("Werte außerhalb des erlaubten Bereichs");
        }
    }

    public ConstructionProject withStatus(Status next) {
        return new ConstructionProject(id, number, town, plot, client, builder, blueprint, dimension, originX,
                originY, originZ, next, placed, total, price, paid, orderedOn, rotation, selfSupplied, depot, contractor, contractorState);
    }

    public ConstructionProject withOriginY(int y) {
        return new ConstructionProject(id, number, town, plot, client, builder, blueprint, dimension, originX,
                OptionalInt.of(y), originZ, status, placed, total, price, paid, orderedOn, rotation, selfSupplied, depot, contractor, contractorState);
    }

    public ConstructionProject withDepot(Map<String, Integer> next) {
        return new ConstructionProject(id, number, town, plot, client, builder, blueprint, dimension, originX,
                originY, originZ, status, placed, total, price, paid, orderedOn, rotation, selfSupplied, next, contractor, contractorState);
    }

    /** Nach {@code count} weiteren Blöcken und einer Zahlung; fertig, wenn alle Blöcke stehen. */
    public ConstructionProject progressed(int count, long payment, Map<String, Integer> newDepot) {
        int next = placed + count;
        return new ConstructionProject(id, number, town, plot, client, builder, blueprint, dimension, originX,
                originY, originZ, next >= total ? Status.DONE : status, next, total, price, paid + payment, orderedOn, rotation, selfSupplied,
                newDepot, contractor, contractorState);
    }

    /** Mit Auftragnehmer in dem angegebenen Stand; ohne Spieler heißt es: die Firma baut. */
    public ConstructionProject withContractor(Optional<PlayerRef> player, Contractor state) {
        return new ConstructionProject(id, number, town, plot, client, builder, blueprint, dimension, originX,
                originY, originZ, status, placed, total, price, paid, orderedOn, rotation, selfSupplied, depot, player,
                state);
    }

    /** Nach Fortschritt eines Spieler-Baus: weitere Blöcke und Zahlung, ohne dass das Spiel Blöcke setzt. */
    public ConstructionProject verified(int count, long payment) {
        return progressed(count, payment, depot);
    }
}
