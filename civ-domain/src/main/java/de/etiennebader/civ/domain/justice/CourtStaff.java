package de.etiennebader.civ.domain.justice;

import de.etiennebader.civ.domain.actor.ActorRef;

import java.util.List;

/**
 * Personal eines Gerichts.
 *
 * @param judges      ernannte Richter; ohne Richter verhandelt die Spitze der Polity
 * @param prosecutors ernannte Staatsanwälte; ohne sie klagt der Anzeigende selbst an
 */
public record CourtStaff(List<ActorRef> judges, List<ActorRef> prosecutors) {

    public static final int MAX = 5;

    public CourtStaff {
        judges = List.copyOf(judges);
        prosecutors = List.copyOf(prosecutors);
    }

    public static CourtStaff empty() {
        return new CourtStaff(List.of(), List.of());
    }
}
