package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.Repositories;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.inmemory.InMemoryRepositories;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.CompositeClaimPolicy;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.chunk;
import static de.etiennebader.civ.application.ApplicationFixture.seat;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeTrackerTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(5);
    private final ChangeTracker tracker = new ChangeTracker();
    private final Repositories repositories = InMemoryRepositories.create();
    private final CivApplication app = new CivApplication(repositories, ids, CompositeClaimPolicy.standard(4, 1), tracker);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));

    @Test
    @DisplayName("Gründung, Grundstück und Geldbewegungen werden als Änderungen erkannt")
    void tracksChangesFromEvents() {
        PolityId polity = app.foundPolity().execute(new FoundPolity.Command(PolityName.of("Nordhain"), alice, EMERALD, seat(0, 0), DAY));
        PlotId plot = app.createPlot().execute(new CreatePlot.Command(polity,
                new Area(DimensionId.OVERWORLD, 0, 0, 3, 3), PlotType.RESIDENTIAL, DAY));
        app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(100), DAY));
        app.transferFunds().execute(new TransferFunds.Command(alice, new PolityRef(polity), emeralds(10), TransferReason.GRANT, DAY));

        ChangeSet changes = tracker.takeChanges();

        assertEquals(Set.of(polity), changes.polities());
        assertEquals(Set.of(chunk(0, 0)), changes.chunks());
        assertEquals(Set.of(new PlotKey(polity, plot)), changes.plots());
        assertEquals(List.of(new AccountKey(alice, EMERALD), new AccountKey(new PolityRef(polity), EMERALD)),
                List.copyOf(changes.accounts()));
        assertEquals(Set.of(EMERALD), changes.currencies());
        assertEquals(5, changes.events().size());
    }

    @Test
    @DisplayName("Nach dem Abholen ist der Tracker leer")
    void takeChangesResets() {
        app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(1), DAY));

        assertTrue(tracker.hasChanges());
        tracker.takeChanges();

        assertFalse(tracker.hasChanges());
        assertTrue(tracker.takeChanges().isEmpty());
    }

    @Test
    @DisplayName("Zurückgegebene Änderungen stehen vor neueren")
    void requeuedChangesComeFirst() {
        app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(1), DAY));
        ChangeSet failed = tracker.takeChanges();
        app.depositEmeralds().execute(new DepositEmeralds.Command(alice, emeralds(2), DAY.next()));

        tracker.requeue(failed);
        ChangeSet retried = tracker.takeChanges();

        assertEquals(2, retried.events().size());
        assertEquals(failed.events().getFirst(), retried.events().getFirst());
    }
}
