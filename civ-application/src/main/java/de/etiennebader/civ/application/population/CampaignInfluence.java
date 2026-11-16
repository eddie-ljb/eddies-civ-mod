package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.PolityView;

/**
 * Wie der Wahlkampf die Wahl eines Dorfbewohners beeinflusst: ein Zuschlag auf den Nutzen eines Kandidaten.
 */
@FunctionalInterface
public interface CampaignInfluence {

    double bonus(PersonRef villager, ActorRef candidate, Election election, PolityView polity);

    /** Ohne Wahlkampf. */
    CampaignInfluence NONE = (villager, candidate, election, polity) -> 0;
}
