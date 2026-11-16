package de.etiennebader.civ.domain.justice;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainEvent;

/** Ereignisse der Justiz; jede Änderung an Verfahren oder Gerichtspersonal. */
public sealed interface JusticeEvent extends DomainEvent {

    /** Ein Verfahren wurde angelegt oder geändert. */
    record CaseChanged(CaseId id, int number, Stage stage, Day occurredOn) implements JusticeEvent {
    }

    /** Richter, Staatsanwälte oder Verbannte eines Gerichts haben sich geändert. */
    record CourtChanged(de.etiennebader.civ.domain.shared.PolityId court, Day occurredOn) implements JusticeEvent {
    }
}
