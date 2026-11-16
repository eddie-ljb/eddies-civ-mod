package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Die Verfassung wurde ausgerufen; ihre Artikel ändern sich ab jetzt nur durch Abstimmung.
 */
public record ConstitutionProclaimed(PolityId polity, Day occurredOn) implements PolityEvent {

    public ConstitutionProclaimed {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
