package de.etiennebader.civ.server;

import de.etiennebader.civ.application.day.DayReport;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.server.command.CivCommands;
import de.etiennebader.civ.server.protection.ProtectionService;
import de.etiennebader.civ.server.save.SaveListener;

import java.util.Optional;

/** Ein {@link CivServer} mit Speicher im Arbeitsspeicher und minutenlangen Tagen für Tests anderer Pakete. */
public final class CivServerFixture {

    /** Eine Minute Echtzeit pro Tag. */
    public static final ServerSettings SETTINGS = new ServerSettings(1_200, 9, 4, CurrencyId.EMERALD);

    private final CivServer server;

    private CivServerFixture(CivServer server) {
        this.server = server;
    }

    public static CivServerFixture start(IdGenerator ids) {
        return new CivServerFixture(CivServer.start(new MemoryStateStore(), SETTINGS, ids, SaveListener.NONE));
    }

    public CivCommands commands() {
        return server.commands();
    }

    public ProtectionService protection() {
        return server.protection();
    }

    public WorldQueries queries() {
        return server.queries();
    }

    public PlayerDirectory players() {
        return server.players();
    }

    /** Tickt, bis der laufende Tag endet, und liefert dessen Bericht. */
    public DayReport tickUntilDayEnds() {
        for (long tick = 0; tick < SETTINGS.ticksPerDay(); tick++) {
            Optional<DayReport> report = server.tick();
            if (report.isPresent()) {
                return report.get();
            }
        }
        throw new IllegalStateException("Der Tag ist nicht zu Ende gegangen");
    }
}
