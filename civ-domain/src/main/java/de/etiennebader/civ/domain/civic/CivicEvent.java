package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.PolityId;

/** Ereignisse des Registers für Wahlkreise und Hoheitszeichen. */
public sealed interface CivicEvent extends DomainEvent {

    PolityId polity();

    /** Ein Wahlkreis wurde angelegt, geändert oder aufgelöst. */
    record ConstituencyChanged(PolityId polity, ConstituencyId constituency, boolean removed, Day occurredOn)
            implements CivicEvent {
    }

    /** Flagge oder Hymne einer Polity wurde gesetzt oder entfernt. */
    record SymbolChanged(PolityId polity, String symbol, Day occurredOn) implements CivicEvent {
    }
}
