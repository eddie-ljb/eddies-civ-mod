package de.etiennebader.civ.domain.production;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.PolityId;

/** Ereignisse der Firmenbetriebe. */
public sealed interface ProductionEvent extends DomainEvent {

    PolityId company();

    /** Ein Betrieb wurde gegründet, geändert oder geschlossen. */
    record EnterpriseChanged(PolityId company, Day occurredOn) implements ProductionEvent {
    }
}
