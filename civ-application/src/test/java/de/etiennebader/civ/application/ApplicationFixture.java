package de.etiennebader.civ.application;

import de.etiennebader.civ.application.inmemory.InMemoryRepositories;
import de.etiennebader.civ.application.inmemory.RecordingEventPublisher;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.CompositeClaimPolicy;
import de.etiennebader.civ.domain.territory.DimensionId;

/**
 * Eine vollständig verdrahtete Application mit In-Memory-Adaptern für Tests.
 * Standardregeln: zusammenhängendes Kerngebiet, höchstens 2 + 1 × Mitglieder Chunks.
 */
public final class ApplicationFixture {

    public static final CurrencyId EMERALD = CurrencyId.EMERALD;
    public static final Day DAY = Day.of(1);

    public final SeededIdGenerator ids = new SeededIdGenerator(99);
    public final Repositories repositories = InMemoryRepositories.create();
    public final RecordingEventPublisher events = new RecordingEventPublisher();
    public final CivApplication app = new CivApplication(repositories, ids, CompositeClaimPolicy.standard(2, 1), events);

    public PlayerRef player() {
        return new PlayerRef(PlayerId.generate(ids));
    }

    public PersonRef villager() {
        return new PersonRef(PersonId.generate(ids));
    }

    public PolityId found(String name, ActorRef founder, int chunkX, int chunkZ) {
        return app.foundPolity().execute(new FoundPolity.Command(
                PolityName.of(name), founder, EMERALD, chunk(chunkX, chunkZ), DAY));
    }

    public static ChunkCoord chunk(int x, int z) {
        return new ChunkCoord(DimensionId.OVERWORLD, x, z);
    }

    public static Money emeralds(long amount) {
        return Money.of(amount, EMERALD);
    }
}
