package de.etiennebader.civ.server.plot;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(21);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final PlotService plots = server.plots();

    @Test
    @DisplayName("Bürger wählt zwei Ecken und legt ein Grundstück an")
    void createsPlotFromSelection() {
        server.commands().found(at(alice, 8, 8), "Nordhain");

        plots.selectCorner(at(alice, 1, 1), 1);
        Reply second = plots.selectCorner(at(alice, 10, 6), 2);
        assertEquals(2, second.lines().size());

        Reply created = plots.createFromSelection(at(alice, 5, 5), "wohnen");
        assertTrue(created.success());
        PlotView plot = plots.plotAt(alice, pos(3, 3)).orElseThrow();
        assertEquals(PlotType.RESIDENTIAL, plot.type());
        assertEquals(60, plot.area().size());
        assertTrue(plot.townLand());
        assertTrue(plots.selection(alice).area().isEmpty());
    }

    @Test
    @DisplayName("Ohne Auswahl, mit falscher Art oder als Nicht-Bürger wird abgelehnt")
    void rejectsInvalidCreation() {
        server.commands().found(at(alice, 8, 8), "Nordhain");

        assertEquals(Reply.failure(Message.of(MessageKey.PLOT_SELECTION_INCOMPLETE)),
                plots.createFromSelection(at(alice, 5, 5), "wohnen"));
        assertFalse(plots.createChunk(at(alice, 5, 5), "schloss").success());
        assertFalse(plots.createChunk(at(bob, 5, 5), "wohnen").success());
    }

    @Test
    @DisplayName("Gemeindeland anbieten, Fremder kauft, Geld geht in die Gemeindekasse")
    void sellsTownLand() {
        server.commands().found(at(alice, 8, 8), "Nordhain");
        plots.createChunk(at(alice, 5, 5), "gewerbe");
        server.bank().deposit(at(bob, 0, 0), 10);

        assertTrue(plots.offer(at(alice, 5, 5), "2,5").success());
        assertEquals(MessageKey.PLOT_ENTERED_FOR_SALE.key(), plots.moved(bob, pos(5, 5)).orElseThrow().key());
        assertTrue(plots.moved(bob, pos(6, 6)).isEmpty());

        assertTrue(plots.buyHere(at(bob, 5, 5)).success());
        assertEquals(750, server.bank().balanceOf(bob).amount());
        PlotView plot = plots.plotAt(bob, pos(5, 5)).orElseThrow();
        assertTrue(plot.heldByViewer());
        assertTrue(plot.askingPrice().isEmpty());
        assertFalse(plots.buyHere(at(bob, 5, 5)).success());
    }

    @Test
    @DisplayName("Inhaber bietet selbst an; Fremde dürfen nicht anbieten oder vergeben")
    void onlyHolderOffers() {
        server.commands().found(at(alice, 8, 8), "Nordhain");
        server.players().record(bob, "Bob");
        plots.createChunk(at(alice, 5, 5), "wohnen");

        assertFalse(plots.offer(at(bob, 5, 5), "1").success());
        assertFalse(plots.assign(at(bob, 5, 5), "Bob").success());
        assertTrue(plots.assign(at(alice, 5, 5), "Bob").success());
        assertTrue(plots.offer(at(bob, 5, 5), "1").success());
        assertTrue(plots.withdrawOffer(at(bob, 5, 5)).success());
        assertFalse(plots.dissolve(at(alice, 5, 5)).success());
        assertEquals(1, plots.plotsOf(plots.plotAt(alice, pos(5, 5)).orElseThrow().town(), alice).size());
    }

    private static BlockPosition pos(int x, int z) {
        return new BlockPosition(DimensionId.OVERWORLD, x, 64, z);
    }

    private static Caller at(PlayerRef player, int x, int z) {
        return new Caller(player, "Spieler", pos(x, z));
    }
}
