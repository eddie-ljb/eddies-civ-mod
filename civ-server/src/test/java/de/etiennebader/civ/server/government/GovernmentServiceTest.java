package de.etiennebader.civ.server.government;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.government.Government;
import de.etiennebader.civ.domain.government.Portfolio;
import de.etiennebader.civ.domain.government.Selection;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.government.PendingLaw;
import java.util.List;
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

    @Test
    @DisplayName("Minister: Vorschlag nur im Bereich, Verordnung nur mit Erlaubnis")
    void ministerDecrees() {
        found();
        var politics = server.government().politics();
        var government = server.government();
        assertTrue(government.createMinistry(a(), "Finanzministerium", "", Selection.APPOINTMENT).success());
        assertTrue(government.appoint(a(), "Finanzministerium", "Alice").success());
        var town = server.queries().polityNamed("Nordhain").orElseThrow();
        long before = server.queries().effectiveLaws(town).valueOf(StandardParameters.INCOME_TAX_PERCENT);
        assertFalse(politics.propose(a(), "Finanzministerium", "pvp_allowed", "false").success(), "falscher Bereich");
        assertFalse(politics.propose(a(), "Finanzministerium", "gibts_nicht", "1").success());
        assertFalse(politics.setDecrees(b(), "Finanzministerium", true).success());
        assertTrue(politics.setDecrees(a(), "Finanzministerium", true).success());
        assertTrue(politics.propose(a(), "Finanzministerium", "income_tax_percent", "7").success());
        long after = server.queries().effectiveLaws(server.queries().polityNamed("Nordhain").orElseThrow())
                .valueOf(StandardParameters.INCOME_TAX_PERCENT);
        assertEquals(7, after);
        assertTrue(before != 7);
        assertTrue(politics.setPortfolio(a(), "Finanzministerium", Portfolio.FREE).success());
        assertFalse(politics.propose(a(), "Finanzministerium", "income_tax_percent", "9").success());
    }

    @Test
    @DisplayName("Förderungen anlegen, auflisten und beenden; Empfänger müssen existieren")
    void subsidies() {
        found();
        var politics = server.government().politics();
        assertTrue(server.government().createMinistry(a(), "Soziales", "", Selection.APPOINTMENT).success());
        assertFalse(politics.createSubsidy(b(), "Kindergeld", "Soziales", 5, "alle", 0, 0, 0).success());
        assertFalse(politics.createSubsidy(a(), "Kindergeld", "Soziales", 5, "Niemand", 0, 0, 0).success());
        assertFalse(politics.createSubsidy(a(), "Kindergeld", "Soziales", 0, "alle", 0, 0, 0).success());
        assertTrue(politics.createSubsidy(a(), "Kindergeld", "Soziales", 5, "alle", 10, 100, 300).success());
        assertFalse(politics.createSubsidy(a(), "Kindergeld", "Soziales", 5, "alle", 0, 0, 0).success(), "doppelt");
        assertTrue(politics.createSubsidy(a(), "Hilfe fuer Bob", "Soziales", 5, "Bob", 0, 0, 0).success());
        assertTrue(politics.listSubsidies(a()).success());
        assertFalse(politics.cancelSubsidy(b(), "Kindergeld").success());
        assertTrue(politics.cancelSubsidy(a(), "Kindergeld").success());
        server.government().endOfDay(Day.of(1));
    }

    @Test
    @DisplayName("Ministerwahl: Kandidatur, Stimme, Auszählung am Ende")
    void ministerElection() {
        found();
        var politics = server.government().politics();
        assertTrue(server.government().createMinistry(a(), "Bildung", "Schulen", Selection.ELECTION).success());
        assertTrue(server.government().createMinistry(a(), "Finanzen", "", Selection.APPOINTMENT).success());
        assertFalse(politics.callElection(a(), "Finanzen").success(), "ernannt, nicht gewählt");
        assertFalse(politics.callElection(b(), "Bildung").success());
        assertTrue(politics.callElection(a(), "Bildung").success());
        assertFalse(politics.callElection(a(), "Bildung").success(), "läuft schon");
        assertFalse(politics.castBallot(a(), "Bildung", "Alice").success(), "noch kein Kandidat");
        assertTrue(politics.stand(a(), "Bildung").success());
        assertTrue(politics.castBallot(a(), "Bildung", "Alice").success());
        server.government().endOfDay(Day.of(1_000));
        var government = server.government().registry().of(server.queries().polityNamed("Nordhain").orElseThrow().id());
        assertTrue(government.isMinister(alice, "Bildung"));
        assertTrue(government.election("bildung").isEmpty());
    }

    @Test
    @DisplayName("Regierungsbildung braucht ein Parlament")
    void formationNeedsParliament() {
        found();
        assertFalse(server.government().politics().form(a()).success());
        assertFalse(server.government().politics().form(b()).success());
        assertTrue(server.government().politics().setCoalitionRules(a(), 60, false).success());
        assertFalse(server.government().politics().addAgreement(a(), "income_tax_percent", "5").success(),
                "ohne Regierung kein Koalitionsvertrag");
    }

    private boolean pvpAllowed() {
        return server.queries().effectiveLaws(server.queries().polityNamed("Nordhain").orElseThrow())
                .valueOf(StandardParameters.PVP_ALLOWED);
    }

    private void passLaw(boolean value) {
        var town = server.queries().polityNamed("Nordhain").orElseThrow();
        assertTrue(server.voting().propose(b(), town,
                ParameterValue.of(StandardParameters.PVP_ALLOWED, value)).success());
        assertTrue(server.voting().vote(a(), town, 1, true).success());
    }

    @Test
    @DisplayName("Veto: beschlossene Gesetze warten; Unterschrift setzt sie in Kraft, Veto verwirft sie")
    void vetoHoldsLaws() {
        found();
        assertTrue(server.commands().join(b(), "Nordhain").success());
        var government = server.government();
        assertTrue(government.createMinistry(a(), "Kanzleramt", "", Selection.APPOINTMENT).success());
        assertTrue(pvpAllowed() || !pvpAllowed());
        boolean before = pvpAllowed();

        passLaw(!before);
        assertEquals(before, pvpAllowed(), "wartet auf die Unterschrift");
        assertTrue(government.veto().list(a()).success());
        assertFalse(government.veto().sign(b(), 1).success(), "nur das Oberhaupt");
        assertTrue(government.veto().sign(a(), 1).success());
        assertEquals(!before, pvpAllowed());

        passLaw(before);
        assertEquals(!before, pvpAllowed());
        assertFalse(government.veto().veto(b(), 1).success());
        assertTrue(government.veto().veto(a(), 1).success());
        assertEquals(!before, pvpAllowed(), "Veto: Gesetz verfällt");

        passLaw(before);
        assertTrue(government.setHeadSalary(a(), 0).success());
        assertTrue(government.veto().setVetoDays(a(), 2).success());
        government.endOfDay(Day.of(5_000));
        assertEquals(before, pvpAllowed(), "nach Fristablauf gilt das Gesetz ohne Unterschrift");
    }

    @Test
    @DisplayName("Veto gilt nur mit eingerichteter Regierung und Veto-Recht")
    void vetoNeedsGovernment() {
        found();
        assertTrue(server.commands().join(b(), "Nordhain").success());
        boolean before = pvpAllowed();
        passLaw(!before);
        assertEquals(!before, pvpAllowed(), "ohne Regierung kein Veto");
    }

    @Test
    @DisplayName("Zurückgehaltene Gesetze überstehen die Umwandlung")
    void pendingRoundTrip() {
        Motion change = new Motion.Change(ParameterValue.of(StandardParameters.INCOME_TAX_PERCENT, 9L));
        PendingLaw law = GovernmentVeto.toPending(change, alice, Day.of(1), Day.of(4));
        assertEquals(change, GovernmentVeto.toMotion(law));
        assertEquals(List.of("income_tax_percent=9"), law.values());
    }
}
