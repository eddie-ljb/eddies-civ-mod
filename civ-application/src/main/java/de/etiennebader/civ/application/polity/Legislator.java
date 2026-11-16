package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Proposal;

import java.util.Optional;

/**
 * Wie ein Dorfbewohner mit Sitz im Parlament handelt: abstimmen und Anträge stellen.
 *
 * <p>Im Spiel entscheidet {@code PersonalityLegislator} nach Persönlichkeit und Parteilinie; {@link #PASSIVE} enthält
 * sich immer und stellt keine Anträge, z. B. für Tests.
 */
public interface Legislator {

    /** @return {@code true} dafür, {@code false} dagegen, leer bei Enthaltung */
    Optional<Boolean> decide(PersonRef member, Proposal proposal, PolityView polity);

    /** Ein Antrag, den der Abgeordnete heute stellen möchte; leer, wenn ihm nichts wichtig genug ist. */
    Optional<Motion> propose(PersonRef member, PolityView polity);

    Legislator PASSIVE = new Legislator() {
        @Override
        public Optional<Boolean> decide(PersonRef member, Proposal proposal, PolityView polity) {
            return Optional.empty();
        }

        @Override
        public Optional<Motion> propose(PersonRef member, PolityView polity) {
            return Optional.empty();
        }
    };
}
