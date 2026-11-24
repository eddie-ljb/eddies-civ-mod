package de.etiennebader.civ.server.construction;

import de.etiennebader.civ.application.economy.CreditTownIncome;
import de.etiennebader.civ.application.land.AssignPlot;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.territory.ClaimChunk;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.server.command.Caller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dorfbewohner als Bauamt, Firmenchefs und Bauherren beauftragen Baufirmen und schreiben Grundstücke aus. */
class VillagerUrbanismTest {

    private static final DimensionId OW = DimensionId.OVERWORLD;

    private final SeededIdGenerator ids = new SeededIdGenerator(77);
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final List<PersonRef> villagers = new ArrayList<>();
    private PolityView town;

    /** Eine Gemeinde nur mit Dorfbewohnern: Alice gründet, erweitert das Gebiet und geht. */
    private void setUp(int residents) {
        server.playerJoined(alice, "Alice");
        server.playerJoined(bob, "Bob");
        Caller aliceAt = new Caller(alice, "Alice", new BlockPosition(OW, 8, 64, 8));
        assertTrue(server.commands().found(aliceAt, "Nordhain").success());
        town = server.queries().polityNamed("Nordhain").orElseThrow();
        for (ChunkCoord chunk : List.of(new ChunkCoord(OW, 1, 0), new ChunkCoord(OW, 2, 0), new ChunkCoord(OW, 0, 1),
                new ChunkCoord(OW, 1, 1))) {
            server.app().claimChunk().execute(new ClaimChunk.Command(town.id(), chunk, ClaimType.CORE, Day.of(1)));
        }
        for (int i = 0; i < residents; i++) {
            PersonRef villager = new PersonRef(PersonId.generate(ids));
            villagers.add(villager);
            server.villagers().seen(villager, new BlockPosition(OW, 2, 64, 2), Day.of(1));
        }
        assertTrue(server.commands().leave(aliceAt).success());
        town = server.queries().polity(town.id()).orElseThrow();
        assertTrue(town.mayor(true).filter(PersonRef.class::isInstance).isPresent(), "ein Dorfbewohner führt kommissarisch");
        // Baufirma eines Spielers
        server.bank().deposit(new Caller(bob, "Bob", new BlockPosition(OW, 900, 64, 900)), 6_000);
        assertTrue(server.companies().found(new Caller(bob, "Bob", new BlockPosition(OW, 900, 64, 900)), "50",
                "Bau AG").success());
        assertTrue(server.companies().chooseBranch(new Caller(bob, "Bob", new BlockPosition(OW, 900, 64, 900)),
                "Bau AG", "bau").success());
    }

    private void fund(PersonRef villager, long amount) {
        server.app().creditTownIncome().creditEmployer(villager, Money.of(amount, town.currency()), Day.of(1));
    }

    @Test
    @DisplayName("Ein Dorfbewohner als Bauamt schneidet Grundstücke zu und schreibt sie zum Verkauf aus")
    void villagerPlannerOffersPlots() {
        setUp(8);
        for (int day = 2; day < 80; day++) {
            server.construction().villagersOrder(Day.of(day));
        }
        var plots = server.queries().land(town.id()).plots();
        assertFalse(plots.isEmpty(), "das Bauamt hat Grundstücke zugeschnitten");
        assertTrue(plots.stream().anyMatch(plot -> plot.isForSale()), "mindestens eines steht zum Verkauf");
        assertTrue(plots.stream().anyMatch(plot -> plot.type() == PlotType.EDUCATION || plot.type() == PlotType.PUBLIC
                || plot.type() == PlotType.RELIGIOUS), "und die Gemeinde plant kommunale Flächen");
        long offered = plots.stream().filter(plot -> plot.holder().equals(town.asActor()) && plot.isForSale()).count();
        assertTrue(offered <= TownPlanning.MAX_OFFERED);
        assertTrue(plots.stream().noneMatch(plot -> plot.area().minX() / 16 == 0 && plot.area().minZ() / 16 == 0),
                "der Kern mit dem Gemeindehaus bleibt frei");
    }

    @Test
    @DisplayName("Das Bauamt aus Dorfbewohnern gründet Straßen mit richtigen Namen und lässt Wege bauen")
    void villagerPlannerFoundsStreetsAndRoads() {
        setUp(8);
        for (int day = 2; day < 120; day++) {
            server.construction().villagersOrder(Day.of(day));
        }
        PolityView updated = server.queries().polity(town.id()).orElseThrow();
        assertFalse(updated.streets().isEmpty(), "das Bauamt hat eine Straße angelegt");
        updated.streets().forEach(street -> assertTrue(street.name().chars().noneMatch(Character::isDigit),
                "Straßenname ohne Zahl: " + street.name()));
        assertTrue(updated.streets().stream().noneMatch(street ->
                server.queries().land(town.id()).plots().stream().anyMatch(plot -> plot.area().overlaps(street.area()))),
                "keine Straße über einem Grundstück");
        assertFalse(server.roads().jobs().isEmpty(), "und den Wegebau beauftragt");
        // Der Weg wächst mit der Zeit und endet fertig.
        for (int i = 0; i < 40; i++) {
            server.roads().advance(10_000);
        }
        assertTrue(server.roads().jobs().stream().anyMatch(job -> job.finished()), "ein Weg ist fertig");
    }

    @Test
    @DisplayName("Straßenpflicht: Das Bauamt legt zuerst die Straße an und baut den Weg, erst dann gibt es Grundstücke")
    void villagerPlannerLaysStreetsBeforePlots() {
        server.enforceStreets(true);
        setUp(8);
        boolean plotBeforeRoad = false;
        for (int day = 2; day < 160; day++) {
            server.construction().villagersOrder(Day.of(day));
            server.roads().advance(10_000);
            boolean anyPlot = !server.queries().land(town.id()).plots().isEmpty();
            boolean roadFinished = server.roads().jobs().stream().anyMatch(job -> job.finished());
            plotBeforeRoad |= anyPlot && !roadFinished;
        }
        assertFalse(plotBeforeRoad, "kein Grundstück, bevor der erste Weg fertig ist");
        PolityView updated = server.queries().polity(town.id()).orElseThrow();
        assertFalse(updated.streets().isEmpty(), "die Straße kam zuerst");
        var plots = server.queries().land(town.id()).plots();
        assertFalse(plots.isEmpty(), "an der fertigen Straße entstehen Grundstücke");
        assertTrue(plots.stream().allMatch(plot -> de.etiennebader.civ.application.civil.Addresses
                .streetsNear(updated, plot.area()).size() > 0), "jedes Grundstück liegt an einer Straße");
    }

    @Test
    @DisplayName("Straßenpflicht: Das Gebiet wird vollständig beplant, ohne dass es an der Straßenzahl hängt")
    void villagerPlannerFillsTerritory() {
        server.enforceStreets(true);
        setUp(8);
        for (int day = 2; day < 600; day++) {
            server.construction().villagersOrder(Day.of(day));
            server.roads().advance(10_000);
        }
        PolityView updated = server.queries().polity(town.id()).orElseThrow();
        var plots = server.queries().land(town.id()).plots();
        assertFalse(updated.streets().isEmpty());
        // Vier Chunks außerhalb des Kerns: Alle bekommen ein Grundstück, obwohl nur eine Straße (statt vier
        // Grundstücke je Straße) nötig ist; früher blieb es bei zwei oder drei.
        assertEquals(4, plots.size(), "die Grundstücke füllen das Gebiet");
    }

    @Test
    @DisplayName("Land vor Wasser: Auf Wasser legt das Bauamt weder Straßen noch Grundstücke an")
    void villagerPlannerAvoidsWater() {
        server.landRules().survey(area -> java.util.Optional.of(100));
        setUp(8);
        for (int day = 2; day < 200; day++) {
            server.construction().villagersOrder(Day.of(day));
        }
        PolityView updated = server.queries().polity(town.id()).orElseThrow();
        assertTrue(updated.streets().isEmpty(), "keine Straße im Wasser");
        assertTrue(server.queries().land(town.id()).plots().isEmpty(), "keine Grundstücke im Wasser");
    }

    @Test
    @DisplayName("Dorfbewohner kaufen angebotenes Wohnland und lassen darauf bauen")
    void villagersBuyLandAndBuild() {
        setUp(8);
        villagers.forEach(villager -> fund(villager, 5_000));
        for (int day = 2; day < 200; day++) {
            server.construction().villagersOrder(Day.of(day));
        }
        boolean owner = villagers.stream().anyMatch(villager ->
                !server.queries().land(town.id()).plotsHeldBy(villager).isEmpty());
        assertTrue(owner, "ein Dorfbewohner hat Land gekauft");
        assertTrue(server.construction().projects().stream().anyMatch(project -> project.client() instanceof PersonRef),
                "und eine Baufirma beauftragt");
    }

    @Test
    @DisplayName("Das Bauamt aus Dorfbewohnern lässt kommunale Bauten errichten, bezahlt aus der Gemeindekasse")
    void townBuildsMunicipalBuildings() {
        setUp(8);
        server.app().creditTownIncome().execute(new CreditTownIncome.Command(town.id(),
                Money.of(2_000_000, town.currency()), Day.of(1)));
        // Ein öffentliches Grundstück, das der Gemeinde gehört.
        PlotId square = server.app().createPlot().execute(new CreatePlot.Command(town.id(),
                new Area(OW, 17, 1, 30, 14), PlotType.PUBLIC, Day.of(1)));
        long before = server.queries().ledger().balanceOf(town.asActor(), town.currency()).amount();
        for (int day = 2; day < 60; day++) {
            server.construction().villagersOrder(Day.of(day));
        }
        var municipal = server.construction().projects().stream()
                .filter(project -> project.client().equals(town.asActor())).toList();
        assertFalse(municipal.isEmpty(), "die Gemeinde hat gebaut oder baut");
        assertTrue(municipal.stream().anyMatch(project -> project.plot().equals(square)),
                "auf dem Grundstück der Gemeinde");
        assertTrue(BuiltInBlueprints.plotTypesOf(server.construction().library().find(municipal.getFirst().blueprint())
                .orElseThrow()).contains(server.queries().land(town.id()).plot(municipal.getFirst().plot())
                .orElseThrow().type()), "der Plan passt zur Grundstücksart");
        // Der Bau schreitet voran und kostet die Gemeinde Geld.
        for (int i = 0; i < 20; i++) {
            server.construction().advance(100_000, new ConstructionServiceFlat());
        }
        long after = server.queries().ledger().balanceOf(town.asActor(), town.currency()).amount();
        assertTrue(after < before + 100, "die Baufirma wird aus der Gemeindekasse bezahlt: " + before + " -> " + after);
    }

    @Test
    @DisplayName("Ein Dorfbewohner als Firmenchef lässt auf dem Firmengrundstück bauen")
    void companyHeadOrdersBuilding() {
        setUp(8);
        PersonRef boss = villagers.getFirst();
        fund(boss, 100_000);
        server.app().companies().foundCompany(boss, PolityName.of("Handel Nordhain"),
                Money.of(60_000, town.currency()), Day.of(1));
        PolityView company = server.queries().polityNamed("Handel Nordhain").orElseThrow();
        PlotId plot = server.app().createPlot().execute(new CreatePlot.Command(town.id(),
                new Area(OW, 18, 2, 29, 13), PlotType.COMMERCIAL, Day.of(1)));
        server.app().assignPlot().execute(new AssignPlot.Command(town.id(), plot, new PolityRef(company.id()),
                Day.of(1)));
        for (int day = 2; day < 80; day++) {
            server.construction().villagersOrder(Day.of(day));
        }
        var order = server.construction().projects().stream()
                .filter(project -> project.client().equals(new PolityRef(company.id()))).toList();
        assertFalse(order.isEmpty(), "die Firma hat bauen lassen");
        assertEquals(plot, order.getFirst().plot());
    }

    /** Ebenes, geladenes, freies Gelände. */
    private static final class ConstructionServiceFlat implements ConstructionService.Terrain {

        @Override
        public boolean isLoaded(BlockPosition position) {
            return true;
        }

        @Override
        public int groundLevel(BlockPosition min, int sizeX, int sizeZ) {
            return 70;
        }

        @Override
        public boolean isClear(BlockPosition min, int sizeX, int sizeY, int sizeZ) {
            return true;
        }
    }

    @Test
    @DisplayName("Bebaute Grundstücke werden nicht überbaut, auch ohne früheren Bauauftrag")
    void builtUpPlotsAreSkipped() {
        setUp(8);
        server.app().creditTownIncome().execute(new CreditTownIncome.Command(town.id(),
                Money.of(2_000_000, town.currency()), Day.of(1)));
        server.app().createPlot().execute(new CreatePlot.Command(town.id(), new Area(OW, 17, 1, 30, 14),
                PlotType.PUBLIC, Day.of(1)));
        server.construction().siteSurvey(area -> java.util.Optional.of(true));
        for (int day = 2; day < 60; day++) {
            server.construction().villagersOrder(Day.of(day));
        }
        assertTrue(server.construction().projects().isEmpty(), "nichts wird über Gebautes bestellt");
        assertTrue(server.queries().land(town.id()).plots().size() == 1, "und auch keine neuen Grundstücke darüber");

        server.construction().siteSurvey(area -> java.util.Optional.empty());
        for (int day = 60; day < 90; day++) {
            server.construction().villagersOrder(Day.of(day));
        }
        assertTrue(server.construction().projects().isEmpty(), "ungeladenes Gelände: erst abwarten");

        server.construction().siteSurvey(area -> java.util.Optional.of(false));
        for (int day = 90; day < 150; day++) {
            server.construction().villagersOrder(Day.of(day));
        }
        assertFalse(server.construction().projects().isEmpty(), "freies Gelände: es wird bestellt");
    }
}
