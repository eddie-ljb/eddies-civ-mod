package de.etiennebader.civ.application.company;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.economy.DepositEmeralds;
import de.etiennebader.civ.application.media.ArticleAnalysis;
import de.etiennebader.civ.application.media.PressInfluence;
import de.etiennebader.civ.application.media.PressUseCases;
import de.etiennebader.civ.application.media.VillagerEditors;
import de.etiennebader.civ.application.office.CallElection;
import de.etiennebader.civ.application.office.StandForElection;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.exchange.Dividend;
import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.domain.exchange.StockExchange;
import de.etiennebader.civ.domain.media.Article;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static de.etiennebader.civ.application.ApplicationFixture.EMERALD;
import static de.etiennebader.civ.application.ApplicationFixture.emeralds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyAndPressTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    private long balance(ActorRef actor) {
        return fixture.app.queries().ledger().balanceOf(actor, EMERALD).amount();
    }

    private void deposit(ActorRef actor, long amount) {
        fixture.app.depositEmeralds().execute(new DepositEmeralds.Command(actor, emeralds(amount), DAY));
    }

    private PolityId company() {
        deposit(alice, 2_000);
        return fixture.app.companies().foundCompany(alice, PolityName.of("Holz AG"), emeralds(1_000), DAY);
    }

    @Test
    @DisplayName("Gründung: Kapital in die Firmenkasse, 1000 Aktien an den Gründer, Ausgabepreis Kapital/Aktien")
    void found() {
        assertEquals(ErrorCode.INVALID_ORDER, assertThrows(DomainException.class, () -> fixture.app.companies()
                .foundCompany(alice, PolityName.of("Klein"), emeralds(5), DAY)).code());
        PolityId id = company();
        PolityView firm = fixture.app.queries().polity(id).orElseThrow();
        assertEquals(PolityKind.COMPANY, firm.kind());
        assertTrue(firm.holds(alice, Office.MAYOR));
        assertEquals(1_000L, balance(firm.asActor()));
        assertEquals(1_000L, balance(alice));
        assertEquals(1_000L, fixture.app.queries().exchange().sharesOf(id, alice));
        assertEquals(1L, fixture.app.queries().exchange().price(id));
        assertTrue(fixture.app.queries().municipalityOf(alice).isPresent(), "die Firma ist keine Gemeinde");
    }

    @Test
    @DisplayName("Handel über das Konto: nur so viel, wie der Käufer zahlen kann; Dividende; Auflösung verteilt die Kasse")
    void tradeDividendDissolve() {
        PolityId id = company();
        PolityView firm = fixture.app.queries().polity(id).orElseThrow();
        fixture.app.trading().place(alice, id, Side.SELL, 250, 2, DAY);
        deposit(bob, 300);
        StockExchange.Placement bought = fixture.app.trading().place(bob, id, Side.BUY, 300, 2, DAY);
        assertEquals(150L, bought.filled());
        assertTrue(bought.stoppedForFunds());
        assertEquals(1_300L, balance(alice));
        assertEquals(0L, balance(bob));
        assertEquals(100L, fixture.app.queries().exchange().ordersOf(alice).getFirst().remaining());

        assertEquals(ErrorCode.NOT_OFFICE_HOLDER, assertThrows(DomainException.class, () -> fixture.app.companies()
                .payDividend(bob, id, emeralds(100), DAY)).code());
        assertEquals(ErrorCode.INVALID_ORDER, assertThrows(DomainException.class, () -> fixture.app.companies()
                .payDividend(alice, id, emeralds(100), DAY)).code(), "unter einer Einheit je Aktie");
        Dividend dividend = fixture.app.companies().payDividend(alice, id, emeralds(1_000), DAY);
        assertEquals(1L, dividend.perShare());
        assertEquals(2_150L, balance(alice));
        assertEquals(150L, balance(bob));
        assertEquals(0L, balance(firm.asActor()));

        deposit(firm.asActor(), 500);
        fixture.app.companies().dissolve(alice, id, DAY);
        assertEquals(2_575L, balance(alice));
        assertEquals(225L, balance(bob));
        assertTrue(fixture.app.queries().polity(id).isEmpty());
        assertTrue(fixture.app.queries().exchange().listings().isEmpty());
    }

    @Test
    @DisplayName("Kapitalerhöhung: neue Aktien der Firma, Erlös in die Firmenkasse")
    void issue() {
        PolityId id = company();
        PolityView firm = fixture.app.queries().polity(id).orElseThrow();
        deposit(bob, 500);
        fixture.app.trading().place(bob, id, Side.BUY, 100, 3, DAY);
        StockExchange.Placement placement = fixture.app.companies().issue(alice, id, 200, 3, DAY);
        assertEquals(100L, placement.filled());
        assertEquals(1_300L, balance(firm.asActor()));
        assertEquals(1_200L, fixture.app.queries().exchange().listing(id).orElseThrow().totalShares());
    }

    @Test
    @DisplayName("Sparsame Dorfbewohner kaufen Aktien mit gutem Buchwert")
    void villagerInvestors() {
        PolityId id = company();
        fixture.app.trading().place(alice, id, Side.SELL, 500, 1, DAY);
        PersonRef saver = villagerWith(personality -> personality.thrift() >= 40);
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, saver, MembershipType.RESIDENT, DAY));
        deposit(saver, 1_000);
        VillagerInvestors.Report report = fixture.app.villagerInvestors().execute(DAY);
        assertEquals(1, report.buyOrders());
        assertTrue(fixture.app.queries().exchange().sharesOf(id, saver) > 0);
    }

    @Test
    @DisplayName("Presse: nur die Redaktion schreibt; Gegenstand und Haltung werden erkannt; Leser ändern ihre Wahl")
    void press() {
        PolityId paper = fixture.app.companies().found(alice, PolityName.of("Nordhainer Bote"), PolityKind.NEWSPAPER,
                EMERALD, DAY);
        for (int i = 0; i < 12; i++) {
            fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, fixture.villager(),
                    MembershipType.RESIDENT, DAY));
        }
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, bob, MembershipType.CITIZEN, DAY));
        Map<String, ActorRef> names = Map.of("Bob", bob);
        ArticleAnalysis critical = fixture.app.press().analyse("Skandal um Bob",
                "Bob ist korrupt und hat versagt. Ein Fehler nach dem anderen.", names);
        assertEquals(Optional.of(bob), critical.subject());
        assertEquals(-2, critical.tendency());
        assertEquals(ErrorCode.NOT_AN_EDITOR, assertThrows(DomainException.class, () -> fixture.app.press()
                .publish(bob, paper, "Hallo", "", critical, DAY)).code());

        Article article = fixture.app.press().publish(alice, paper, "Skandal um Bob", "Bob ist korrupt.", critical,
                DAY);
        assertEquals(List.of(nordhain), article.towns());
        assertTrue(article.readers() > 0);

        var election = fixture.app.callElection().execute(new CallElection.Command(nordhain, alice, Office.MAYOR, DAY));
        fixture.app.standForElection().execute(new StandForElection.Command(nordhain, election, bob, DAY));
        PolityView town = fixture.app.queries().polity(nordhain).orElseThrow();
        Election running = town.electionFor(Office.MAYOR).orElseThrow();
        PressInfluence influence = fixture.app.pressInfluence();
        influence.today(DAY);
        PersonRef reader = town.members().stream().map(member -> member.member())
                .filter(PersonRef.class::isInstance).map(PersonRef.class::cast)
                .filter(villager -> PressInfluence.reads(villager, article)).findFirst().orElseThrow();
        assertTrue(influence.bonus(reader, bob, running, town) < 0);
        influence.today(DAY.plus(3));
        assertEquals(0.0, influence.bonus(reader, bob, running, town), 1e-9);

        influence.today(DAY);
        ArticleAnalysis praise = fixture.app.press().analyse("Nordhain ist großartig",
                "Nordhain ist ein Vorbild, alles gelungen und gut.", Map.of());
        assertEquals(Optional.of(town.asActor()), praise.subject());
        Article good = fixture.app.press().publish(alice, paper, "Nordhain ist großartig", "Ein Vorbild.", praise, DAY);
        PersonRef goodReader = town.members().stream().map(member -> member.member())
                .filter(PersonRef.class::isInstance).map(PersonRef.class::cast)
                .filter(villager -> PressInfluence.reads(villager, good)).findFirst().orElseThrow();
        assertTrue(influence.satisfactionPoints(goodReader, town) > 0);

        for (int i = 0; i < 3; i++) {
            fixture.app.press().publish(alice, paper, "Kurz " + i, "", new ArticleAnalysis(Optional.empty(), 0,
                    Platform.NONE, 0, 0), DAY);
        }
        assertEquals(ErrorCode.PRESS_LIMIT, assertThrows(DomainException.class, () -> fixture.app.press()
                .publish(alice, paper, "Zu viel", "", critical, DAY)).code());
    }

    @Test
    @DisplayName("Dorfbewohner in der Redaktion schreiben aus Vorlagen über Kandidaten oder die Gemeinde")
    void villagerEditors() {
        PolityId paper = fixture.app.companies().found(alice, PolityName.of("Bote"), PolityKind.NEWSPAPER, EMERALD,
                DAY);
        PersonRef editor = fixture.villager();
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, editor, MembershipType.RESIDENT, DAY));
        fixture.app.companies().admit(alice, paper, PolityKind.NEWSPAPER, editor, DAY);
        VillagerEditors editors = new VillagerEditors(() -> fixture.app.queries(), fixture.app.press(),
                actor -> actor.equals(alice) ? "Alice" : "Jemand");
        int written = 0;
        for (int day = 1; day <= 3; day++) {
            written += editors.execute(DAY.plus(day)).size();
        }
        assertEquals(1, written);
        Article article = fixture.app.queries().press().articles().getFirst();
        assertEquals(editor, article.author());
        assertEquals(Optional.of(fixture.app.queries().polity(nordhain).orElseThrow().asActor()), article.subject());
        assertFalse(article.title().isBlank());
    }

    private PersonRef villagerWith(java.util.function.Predicate<de.etiennebader.civ.domain.population.Personality> wanted) {
        while (true) {
            PersonRef villager = fixture.villager();
            if (wanted.test(de.etiennebader.civ.domain.population.Personality.of(villager))) {
                return villager;
            }
        }
    }
}
