package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.PolityId;

/**
 * Alle Events des {@link Polity}-Aggregats.
 */
public sealed interface PolityEvent extends DomainEvent
        permits PolityFounded, MemberAdmitted, MemberLeft, StatuteEnacted, StatuteRepealed {

    PolityId polity();
}
