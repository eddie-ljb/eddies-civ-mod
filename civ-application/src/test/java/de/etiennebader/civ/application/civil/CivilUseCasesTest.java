package de.etiennebader.civ.application.civil;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.land.AssignPlot;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.community.ChatMessage;
import de.etiennebader.civ.domain.community.Letter;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractStatus;
import de.etiennebader.civ.domain.contract.EndReason;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilUseCasesTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    private PlotId alicesPlot(int minX, int minZ) {
        PlotId plot = fixture.app.createPlot().execute(new CreatePlot.Command(nordhain,
                new Area(DimensionId.OVERWORLD, minX, minZ, minX + 3, minZ + 3), PlotType.RESIDENTIAL, DAY));
        fixture.app.assignPlot().execute(new AssignPlot.Command(nordhain, plot, alice, DAY));
        return plot;
    }

    private long balance(de.etiennebader.civ.domain.actor.ActorRef actor) {
        return fixture.app.queries().ledger().balanceOf(actor, EMERALD).amount();
    }

    @Test
    @DisplayName("Miete: Kaution beim Unterschreiben, tägliche Miete, Mieter darf das Grundstück nutzen, Kaution zurück")
    void lease() {
        PlotId plot = alicesPlot(0, 0);
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(bob, emeralds(20), DAY));
        Contract offer = fixture.app.contracts().offerLease(alice, nordhain, plot, bob, emeralds(2), emeralds(5), 2, DAY);
        assertEquals(1, offer.number());
        assertFalse(fixture.app.queries().isTenant(bob, nordhain, plot));

        fixture.app.contracts().sign(offer.id(), bob, DAY);
        assertEquals(15L, balance(bob));
        assertTrue(fixture.app.queries().isTenant(bob, nordhain, plot));
        assertTrue(fixture.app.queries().isTenant(bob, nordhain, plot));

        fixture.app.runContracts().execute(DAY.plus(1));
        assertEquals(13L, balance(bob));
        List<RunContracts.Outcome> outcomes = fixture.app.runContracts().execute(DAY.plus(2));
        RunContracts.Ended ended = assertInstanceOf(RunContracts.Ended.class, outcomes.getFirst());
        assertEquals(EndReason.EXPIRED, ended.reason());
        assertEquals(18L, balance(bob), "Kaution kommt zurück");
        assertFalse(fixture.app.queries().isTenant(bob, nordhain, plot));
    }

    @Test
    @DisplayName("Vertragsbruch: bleibt eine Rate aus, endet der Vertrag und die Kaution verfällt")
    void breach() {
        PlotId plot = alicesPlot(0, 0);
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(bob, emeralds(6), DAY));
        Contract offer = fixture.app.contracts().offerLease(alice, nordhain, plot, bob, emeralds(3), emeralds(4), 0, DAY);
        fixture.app.contracts().sign(offer.id(), bob, DAY);
        RunContracts.Ended ended = assertInstanceOf(RunContracts.Ended.class,
                fixture.app.runContracts().execute(DAY.plus(1)).getFirst());
        assertEquals(EndReason.BREACH, ended.reason());
        assertEquals(2L, balance(bob));
    }

    @Test
    @DisplayName("Fremdes Grundstück kann man nicht vermieten; Kündigung beendet am nächsten Tag")
    void noticeAndForeignPlot() {
        PlotId plot = alicesPlot(0, 0);
        assertEquals(ErrorCode.PLOT_NOT_LEASABLE, assertThrows(DomainException.class, () -> fixture.app.contracts()
                .offerLease(bob, nordhain, plot, alice, emeralds(1), emeralds(0), 0, DAY)).code());
        Contract offer = fixture.app.contracts().offerCustom(alice, bob, emeralds(0), emeralds(0), "Freundschaft",
                "Wir helfen uns.", 0, DAY);
        fixture.app.contracts().sign(offer.id(), bob, DAY);
        Contract noticed = fixture.app.contracts().giveNotice(offer.id(), bob, DAY.plus(1));
        assertEquals(Optional.of(DAY.plus(2)), noticed.endsOn());
        RunContracts.Ended ended = assertInstanceOf(RunContracts.Ended.class,
                fixture.app.runContracts().execute(DAY.plus(2)).getFirst());
        assertEquals(EndReason.NOTICE, ended.reason());
    }

    @Test
    @DisplayName("Dorfbewohner nehmen Arbeit nur ab Mindestlohn an und unterschreiben sofort")
    void villagerEmployment() {
        PersonRef villager = fixture.villager();
        assertEquals(ErrorCode.VILLAGER_DECLINES, assertThrows(DomainException.class, () -> fixture.app.contracts()
                .offerEmployment(alice, villager, de.etiennebader.civ.domain.shared.Money.of(10, EMERALD), 0, DAY))
                .code());
        Contract hired = fixture.app.contracts().offerEmployment(alice, villager,
                de.etiennebader.civ.domain.shared.Money.of(ContractUseCases.VILLAGER_MIN_WAGE, EMERALD), 0, DAY);
        assertEquals(ContractStatus.ACTIVE, hired.status());
    }

    @Test
    @DisplayName("Bündnis: Bürger der verbündeten Gemeinde gelten als Verbündete")
    void alliance() {
        PolityId suedtal = fixture.found("Suedtal", bob, 20, 20);
        Contract offer = fixture.app.contracts().offerAlliance(alice, nordhain, suedtal, DAY);
        PolityView north = fixture.app.queries().polity(nordhain).orElseThrow();
        assertFalse(fixture.app.queries().isAlly(bob, north));
        fixture.app.contracts().sign(offer.id(), bob, DAY);
        assertTrue(fixture.app.queries().isAlly(bob, north));
    }

    @Test
    @DisplayName("Gehalt: Amtsträger bekommen täglich das gesetzliche Gehalt aus der Gemeindekasse")
    void salaries() {
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice,
                ParameterValue.of(StandardParameters.OFFICE_SALARY, 5L), DAY));
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(
                fixture.app.queries().polity(nordhain).orElseThrow().asActor(), emeralds(10), DAY));
        RunContracts.SalariesPaid paid = fixture.app.runContracts().execute(DAY.plus(1)).stream()
                .filter(RunContracts.SalariesPaid.class::isInstance).map(RunContracts.SalariesPaid.class::cast)
                .findFirst().orElseThrow();
        assertEquals(1, paid.recipients());
        assertEquals(5L, balance(alice));
    }

    @Test
    @DisplayName("Straßen und Adressen: Hausnummern entlang der Straße, ungerade auf der einen Seite")
    void addresses() {
        assertEquals(ErrorCode.NOT_OFFICE_HOLDER, assertThrows(DomainException.class, () -> fixture.app.streets()
                .lay(bob, nordhain, "Hauptstraße", new Area(DimensionId.OVERWORLD, 0, 6, 15, 7), DAY)).code());
        assertTrue(fixture.app.streets().lay(alice, nordhain, "Hauptstraße",
                new Area(DimensionId.OVERWORLD, 0, 6, 15, 7), DAY));
        PlotId north = alicesPlot(0, 0);
        PlotId south = alicesPlot(0, 9);
        PlotId north2 = alicesPlot(8, 0);
        PolityView town = fixture.app.queries().polity(nordhain).orElseThrow();
        List<Address> all = fixture.app.addresses().all(town);
        assertEquals(3, all.size());
        Address first = all.stream().filter(a -> a.plot().equals(north)).findFirst().orElseThrow();
        Address opposite = all.stream().filter(a -> a.plot().equals(south)).findFirst().orElseThrow();
        Address next = all.stream().filter(a -> a.plot().equals(north2)).findFirst().orElseThrow();
        assertEquals(1, first.number() % 2);
        assertEquals(0, opposite.number() % 2);
        assertTrue(next.number() > first.number());
        assertTrue(first.line().contains("Hauptstraße") && first.line().contains(town.postalCode()));
        assertEquals(Optional.of(first.line()), fixture.app.addresses()
                .at(new BlockPosition(DimensionId.OVERWORLD, 1, 64, 1)).map(Address::line));
        assertTrue(fixture.app.addresses().residenceOf(alice).isPresent());
        assertTrue(fixture.app.streets().remove(alice, nordhain, "hauptstraße", DAY));
        assertTrue(fixture.app.addresses().all(fixture.app.queries().polity(nordhain).orElseThrow()).isEmpty());
    }

    @Test
    @DisplayName("Post kommt am nächsten Tag; Freundschaft braucht beide Seiten; Direktnachrichten nur an Freunde")
    void community() {
        Letter letter = fixture.app.community().send(alice, bob, "Hallo", "Komm vorbei!", DAY);
        assertTrue(fixture.app.queries().community().inbox(bob, DAY).isEmpty());
        assertEquals(List.of(letter), fixture.app.queries().community().inbox(bob, DAY.plus(1)));
        assertTrue(fixture.app.community().read(letter.id(), bob, DAY.plus(1)).orElseThrow().read());

        assertEquals(ErrorCode.NOT_FRIENDS, assertThrows(DomainException.class, () -> fixture.app.community()
                .post(alice, Optional.of(bob), "psst", 1, "web", DAY)).code());
        assertFalse(fixture.app.community().befriend(alice, bob, DAY));
        assertTrue(fixture.app.community().befriend(bob, alice, DAY));
        ChatMessage direct = fixture.app.community().post(alice, Optional.of(bob), "psst", 2, "web", DAY);
        fixture.app.community().post(bob, Optional.empty(), "Hallo alle", 3, "game", DAY);
        PlayerRef carol = fixture.player();
        assertEquals(1, fixture.app.queries().community().chat(carol, 0, 100).size());
        assertEquals(2, fixture.app.queries().community().chat(bob, 0, 100).size());
        assertEquals(1, fixture.app.queries().community().chat(bob, direct.seq(), 100).size());
        assertTrue(fixture.app.community().unfriend(alice, bob, DAY));
        assertTrue(fixture.app.queries().community().friendsOf(alice).isEmpty());
    }
}
