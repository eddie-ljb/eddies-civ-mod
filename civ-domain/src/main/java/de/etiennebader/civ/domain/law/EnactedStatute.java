package de.etiennebader.civ.domain.law;

import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Ein geltendes Gesetz mit dem Tag seines Inkrafttretens.
 */
public record EnactedStatute(Statute statute, Day enactedOn) {

    public EnactedStatute {
        Objects.requireNonNull(statute, "statute");
        Objects.requireNonNull(enactedOn, "enactedOn");
    }
}
