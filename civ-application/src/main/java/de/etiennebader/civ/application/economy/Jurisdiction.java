package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.polity.PolityView;

import java.util.Optional;

/** Welches Recht für einen Akteur gilt: das seiner Gemeinde, bei einer Polity ihr eigenes. */
final class Jurisdiction {

    private Jurisdiction() {
    }

    static Optional<PolityView> of(WorldQueries queries, ActorRef actor) {
        if (actor instanceof PolityRef polity) {
            return queries.polity(polity.id());
        }
        return queries.municipalityOf(actor);
    }

    /** Geltender Wert des Parameters für den Akteur; ohne Zuständigkeit der Standardwert. */
    static <T> T value(WorldQueries queries, ActorRef actor, ParameterKey<T> key) {
        return of(queries, actor).map(polity -> queries.effectiveLaws(polity).valueOf(key))
                .orElse(key.defaultValue());
    }
}
