package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/** Aktien einer Firma im Besitz eines Akteurs. */
public record Holding(PolityId company, ActorRef holder, long shares) {

    public Holding {
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(holder, "holder");
        if (shares <= 0) {
            throw new IllegalArgumentException("Ungültiger Bestand: " + shares);
        }
    }
}
