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
    public static final ServerSettings SETTINGS = ServerSettings.withDayLength(1_200);

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

    public de.etiennebader.civ.server.bank.BankService bank() {
        return server.bank();
    }

    public de.etiennebader.civ.server.market.MarketService market() {
        return server.market();
    }

    public de.etiennebader.civ.server.town.TownService towns() {
        return server.towns();
    }

    public de.etiennebader.civ.server.territory.TerritoryWatcher territory() {
        return server.territory();
    }

    public de.etiennebader.civ.server.plot.PlotService plots() {
        return server.plots();
    }

    public de.etiennebader.civ.server.villager.VillagerService villagers() {
        return server.villagers();
    }

    public de.etiennebader.civ.server.vote.VotingService voting() {
        return server.voting();
    }

    public java.util.List<Announcement> takeAnnouncements() {
        return server.takeAnnouncements();
    }

    public de.etiennebader.civ.server.town.TownHallService townHalls() {
        return server.townHalls();
    }

    public de.etiennebader.civ.server.shop.ShopService shops() {
        return server.shops();
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
