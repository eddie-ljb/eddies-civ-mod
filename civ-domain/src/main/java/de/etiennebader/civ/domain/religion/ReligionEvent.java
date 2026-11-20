package de.etiennebader.civ.domain.religion;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainEvent;

/** Ereignisse des Registers der Religionen. */
public sealed interface ReligionEvent extends DomainEvent {

    ReligionId religion();

    /** Eine Religion wurde gegründet. */
    record Founded(ReligionId religion, String name, ActorRef founder, Day occurredOn) implements ReligionEvent {
    }

    /** Konfession, Klerus oder Klerus-Wahl einer Religion haben sich geändert. */
    record Changed(ReligionId religion, String aspect, Day occurredOn) implements ReligionEvent {
    }

    /** Eine Religion wurde aufgelöst. */
    record Dissolved(ReligionId religion, Day occurredOn) implements ReligionEvent {
    }
}
