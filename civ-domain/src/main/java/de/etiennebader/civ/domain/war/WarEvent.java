package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Optional;

/** Ereignisse rund um Krieg, Garnisonen und Neutralität. */
public sealed interface WarEvent extends DomainEvent {

    /** Ein Krieg wurde erklärt. */
    record WarDeclared(WarId war, PolityId aggressor, PolityId defender, Day occurredOn) implements WarEvent {
    }

    /** Ein Gefecht wurde ausgetragen; {@code winner} leer bei einem Unentschieden. */
    record BattleResolved(WarId war, Optional<PolityId> winner, Day occurredOn) implements WarEvent {
    }

    /** Ein Krieg ist beendet, durch Gefecht oder ausgehandelten Frieden. */
    record WarEnded(WarId war, Day occurredOn) implements WarEvent {
    }

    /** Ein Akteur wurde in die Garnison einer Polity aufgenommen. */
    record Enlisted(PolityId polity, ActorRef member, MilitaryRank rank, Day occurredOn) implements WarEvent {
    }

    /** Ein Akteur wurde aus der Garnison einer Polity entlassen. */
    record Discharged(PolityId polity, ActorRef member, Day occurredOn) implements WarEvent {
    }

    /** Eine Polity hat sich für begrenzte Zeit Neutralität erkauft. */
    record NeutralityPurchased(PolityId polity, Day until, Day occurredOn) implements WarEvent {
    }
}
