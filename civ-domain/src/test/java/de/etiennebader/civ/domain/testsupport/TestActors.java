package de.etiennebader.civ.domain.testsupport;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;

/**
 * Erzeugt reproduzierbare Akteure für Tests.
 */
public final class TestActors {

    private final IdGenerator ids;

    public TestActors(long seed) {
        this.ids = new SeededIdGenerator(seed);
    }

    public TestActors() {
        this(42);
    }

    public IdGenerator ids() {
        return ids;
    }

    public PlayerRef player() {
        return new PlayerRef(PlayerId.generate(ids));
    }

    public PersonRef villager() {
        return new PersonRef(PersonId.generate(ids));
    }

    public PolityId polityId() {
        return PolityId.generate(ids);
    }

    public PolityRef polity() {
        return new PolityRef(polityId());
    }
}
