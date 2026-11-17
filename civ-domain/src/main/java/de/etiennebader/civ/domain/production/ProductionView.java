package de.etiennebader.civ.domain.production;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Collection;
import java.util.Optional;

/** Lesender Blick auf die Firmenbetriebe. */
public interface ProductionView {

    Collection<Enterprise> enterprises();

    Optional<Enterprise> enterpriseOf(PolityId company);
}
