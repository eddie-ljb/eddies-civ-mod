package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/** Setzt einen bereits beschlossenen, zurückgehaltenen Antrag in Kraft (nach Unterschrift oder Ablauf der Frist). */
public final class CarryOutMotion {

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public CarryOutMotion(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public void execute(PolityId polityId, Motion motion, Day day) {
        Polity polity = aggregates.polity(polityId);
        Ballots.carryOut(polity, motion, ids, day);
        aggregates.commit(polity);
    }
}
