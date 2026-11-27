package de.etiennebader.civ.server.government;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.government.Government;
import de.etiennebader.civ.domain.government.Selection;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernmentServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(79);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);

    private Caller a() {
        return new Caller(alice, "Alice", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));
    }

    private Caller b() {
        return new Caller(bob, "Bob", new BlockPosition(DimensionId.OVERWORLD, 800, 64, 800));
    }

    private void found() {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        assertTrue(server.commands().found(a(), "Nordhain").success());
    }

    @Test
    @DisplayName("Nur das Oberhaupt legt Ministerien an, ernennt und konfiguriert")
    void headOnly() {
        found();
        var government = server.government();
        assertFalse(government.createMinistry(b(), "Bildung", "Schulen", Selection.APPOINTMENT).success());
        assertTrue(government.createMinistry(a(), "Bildung", "Schulen", Selection.APPOINTMENT).success());
        assertFalse(government.createMinistry(a(), "bildung", "", Selection.APPOINTMENT).success(), "Doppelt");
        assertFalse(government.setTitle(b(), "Kaiser").success());
        assertTrue(government.setTitle(a(), "Bürgermeisterin").success());
        assertFalse(government.setTitle(a(), "").success(), "leerer Titel");
        assertFalse(government.appoint(a(), "Bildung", "Niemand").success());
        assertTrue(government.appoint(a(), "Bildung", "Alice").success());
        assertTrue(government.overview(a()).success());
    }

    @Test
    @DisplayName("Haushalt: Entwurf, Abstimmung des Oberhaupts, Etat und Neustart-Persistenz der Regeln")
    void budgetFlow() {
        found();
        var government = server.government();
        assertTrue(government.createMinistry(a(), "Finanzministerium", "", Selection.APPOINTMENT).success());
        assertFalse(government.vote(a(), true).success(), "ohne Entwurf");
        assertFalse(government.draft(b(), "Finanzministerium", 0).success());
        assertTrue(government.draft(a(), "Finanzministerium", 0).success());
        assertFalse(government.vote(b(), true).success(), "Bob ist nicht stimmberechtigt");
        assertTrue(government.vote(a(), true).success());
        assertTrue(government.closeBudget(a()).success());
        Government g = government.registry().of(server.queries().polityNamed("Nordhain").orElseThrow().id());
        assertTrue(g.draft().isEmpty());
        assertEquals(0, g.adopted().total());
        assertTrue(g.lastBudgetDay().isPresent());
        assertFalse(government.draft(a(), "Finanzministerium", 1_000_000).success() && government.closeBudget(a()).success(),
                "Haushalt über der Kasse wird nicht beschlossen");
    }
}
