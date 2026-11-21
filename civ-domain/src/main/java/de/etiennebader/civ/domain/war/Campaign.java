package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/** Ein geplantes Gefecht: {@code initiator} plant im Krieg {@code war} für den Tag {@code on} eine Schlacht. */
public record Campaign(WarId war, PolityId initiator, BattleKind kind, Day on) {

    public Campaign {
        Objects.requireNonNull(war, "war");
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(on, "on");
    }
}
