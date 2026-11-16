package de.etiennebader.civ.domain.justice;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lesender Zugriff auf Gerichte und Verfahren. */
public interface JudiciaryView {

    /** Alle Verfahren, das neueste zuletzt. */
    List<CourtCase> cases();

    Map<PolityId, CourtStaff> staff();

    List<Exile> exiles();

    default Optional<CourtCase> caseNumbered(int number) {
        return cases().stream().filter(found -> found.number() == number).findFirst();
    }

    default Optional<CourtCase> courtCase(CaseId id) {
        return cases().stream().filter(found -> found.id().equals(id)).findFirst();
    }

    default CourtStaff staffOf(PolityId court) {
        return staff().getOrDefault(court, CourtStaff.empty());
    }

    default List<CourtCase> casesOf(PolityId court) {
        return cases().stream().filter(found -> found.court().equals(court)).toList();
    }

    /** Verfahren, an denen der Akteur beteiligt ist. */
    default List<CourtCase> casesInvolving(ActorRef actor) {
        return cases().stream().filter(found -> found.roleOf(actor).isPresent()).toList();
    }

    default boolean isExiled(PolityId polity, ActorRef actor, Day today) {
        return exiles().stream().anyMatch(exile -> exile.polity().equals(polity) && exile.actor().equals(actor)
                && !today.isAfter(exile.until()));
    }
}
