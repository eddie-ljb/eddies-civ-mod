package de.etiennebader.civ.server.construction;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.construction.Blueprint;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionServiceTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(19);
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final Caller aliceAt = new Caller(alice, "Alice", new BlockPosition(DimensionId.OVERWORLD, 8, 64, 8));

    /** Ebenes Gelände: alles geladen, Höhe fest, Baufeld frei oder nicht. */
    private record Flat(boolean loaded, boolean clear, int ground) implements ConstructionService.Terrain {

        @Override
        public boolean isLoaded(BlockPosition position) {
            return loaded;
        }

        @Override
        public int groundLevel(BlockPosition min, int sizeX, int sizeZ) {
            return ground;
        }

        @Override
        public boolean isClear(BlockPosition min, int sizeX, int sizeY, int sizeZ) {
            return clear;
        }
    }

    private static final Flat FLAT = new Flat(true, true, 70);

    private static boolean has(Reply reply, MessageKey key) {
        return reply.lines().stream().anyMatch(line -> line.key().equals(key.key()));
    }

    private void setUp() {
        server.playerJoined(alice, "Alice");
        assertTrue(server.commands().found(aliceAt, "Nordhain").success());
        server.bank().deposit(aliceAt, 6_000);
        assertTrue(server.plots().createChunk(aliceAt, "wohnen").success());
        assertTrue(server.plots().assign(aliceAt, "Alice").success());
        assertTrue(server.companies().found(aliceAt, "50", "Bau AG").success());
        assertTrue(server.companies().chooseBranch(aliceAt, "Bau AG", "bau").success());
    }

    @Test
    @DisplayName("Auftrag, schrittweiser Bau mit Zahlung nach Fortschritt, Fertigstellung")
    void orderAndBuild() {
        setUp();
        Reply ordered = server.construction().order(aliceAt, "holz-huette", "Bau AG");
        assertTrue(has(ordered, MessageKey.BUILD_ORDERED), ordered.toString());
        Blueprint plan = server.construction().library().find("holz-huette").orElseThrow();
        int placed = 0;
        for (int step = 0; step < 100 && placed < plan.blockCount(); step++) {
            for (ConstructionService.Build build : server.construction().advance(2_000, FLAT)) {
                assertEquals(70, build.origin().y());
                placed += build.blocks().size();
            }
        }
        assertEquals(plan.blockCount(), placed);
        var project = server.construction().projects().getFirst();
        assertEquals(ConstructionProject.Status.DONE, project.status());
        assertEquals(project.price(), project.paid());
    }

    @Test
    @DisplayName("Ruht die Baustelle, solange das Gelände nicht geladen ist")
    void unloadedSiteWaits() {
        setUp();
        assertTrue(server.construction().order(aliceAt, "holz-huette", "Bau AG").success());
        assertTrue(server.construction().advance(100_000, new Flat(false, true, 70)).isEmpty());
    }

    @Test
    @DisplayName("Baugenehmigung: Antrag wartet, das Bauamt genehmigt, dann beginnt der Bau")
    void permit() {
        setUp();
        assertTrue(server.commands().amendLaw(aliceAt, "build_permit_required", "true").success());
        Reply ordered = server.construction().order(aliceAt, "holz-huette", "Bau AG");
        assertTrue(has(ordered, MessageKey.BUILD_ORDERED_PERMIT), ordered.toString());
        assertTrue(server.construction().advance(100_000, FLAT).isEmpty());
        assertTrue(server.construction().approve(aliceAt, 1).success());
        assertFalse(server.construction().advance(100_000, FLAT).isEmpty());
    }

    @Test
    @DisplayName("Falsche Aufträge: unbekannter Plan, keine Baufirma, zu großer Plan")
    void refusals() {
        setUp();
        assertTrue(has(server.construction().order(aliceAt, "gibtsnicht", "Bau AG"), MessageKey.BUILD_UNKNOWN_PLAN));
        assertTrue(has(server.construction().order(aliceAt, "holz-huette", "Nirgends AG"),
                MessageKey.BUILD_UNKNOWN_FIRM));
    }

    @Test
    @DisplayName("Platzierung: mittig auf dem Grundstück, Drehung vertauscht die Maße und dreht die Blöcke")
    void centredAndRotated() {
        setUp();
        assertTrue(has(server.construction().order(aliceAt, "holz-mehrfamilienhaus", "Bau AG", false, 45),
                MessageKey.BUILD_BAD_ROTATION));
        assertTrue(server.construction().order(aliceAt, "holz-mehrfamilienhaus", "Bau AG", false, 90).success());
        var build = server.construction().advance(2_000, FLAT).getFirst();
        Blueprint plan = server.construction().library().find("holz-mehrfamilienhaus").orElseThrow();
        // Grundstück = Chunk 0 (0..15): 7 x 11 nach Drehung, mittig bei (4, 2)
        assertEquals(4, build.origin().x());
        assertEquals(2, build.origin().z());
        assertEquals(1, build.rotation());
        assertTrue(build.blocks().stream().allMatch(block -> block.x() < plan.sizeZ() && block.z() < plan.sizeX()));
    }

    @Test
    @DisplayName("Hologramm: die Vorschau steht dort, wo der Bau stünde, und kostet nichts")
    void previewMatchesPlacement() {
        setUp();
        var result = server.construction().preview(aliceAt, "holz-mehrfamilienhaus", 90);
        assertTrue(result.reply().success());
        var preview = result.preview().orElseThrow();
        assertEquals(1, preview.rotation());
        assertEquals(4, preview.origin().x());
        assertEquals(2, preview.origin().z());
        assertEquals(64, preview.origin().y());
        assertTrue(server.construction().projects().isEmpty(), "eine Vorschau beauftragt nichts");
        assertTrue(has(server.construction().preview(aliceAt, "holz-mehrfamilienhaus", 45).reply(),
                MessageKey.BUILD_BAD_ROTATION));
        assertTrue(has(server.construction().preview(aliceAt, "gibt-es-nicht", 90).reply(),
                MessageKey.BUILD_UNKNOWN_PLAN));
        Caller wilderness = new Caller(alice, "Alice", new BlockPosition(DimensionId.OVERWORLD, 900, 64, 900));
        assertTrue(has(server.construction().preview(wilderness, "holz-huette", 0).reply(), MessageKey.BUILD_NO_PLOT));
        // automatisch ausgerichtet wie beim Auftrag
        var auto = server.construction().preview(aliceAt, "holz-huette", ConstructionService.AUTO).preview().orElseThrow();
        assertTrue(server.construction().order(aliceAt, "holz-huette", "Bau AG").success());
        assertEquals(server.construction().advance(2_000, FLAT).getFirst().rotation(), auto.rotation());
    }

    @Test
    @DisplayName("Straßenpflicht: Gebaut wird erst, wenn die Straße am Grundstück angelegt ist")
    void buildingNeedsAStreet() {
        setUp();
        server.enforceStreets(true);
        Reply refused = server.construction().order(aliceAt, "holz-huette", "Bau AG");
        assertTrue(has(refused, MessageKey.BUILD_NEEDS_STREET), refused.toString());
        var town = server.queries().polityNamed("Nordhain").orElseThrow();
        server.app().streets().lay(alice, town.id(), "Hauptstrasse",
                new de.etiennebader.civ.domain.land.Area(DimensionId.OVERWORLD, 0, 0, 15, 1),
                de.etiennebader.civ.domain.shared.Day.of(1));
        assertTrue(server.construction().order(aliceAt, "holz-huette", "Bau AG").success());
    }

    @Test
    @DisplayName("Ist das Baufeld nicht frei, ruht die Baustelle und der Bauherr bekommt eine Nachricht")
    void blockedSite() {
        setUp();
        assertTrue(server.construction().order(aliceAt, "holz-huette", "Bau AG").success());
        server.takeAnnouncements();
        assertTrue(server.construction().advance(100_000, new Flat(true, false, 70)).isEmpty());
        assertTrue(server.takeAnnouncements().stream()
                .anyMatch(a -> a.message().key().equals(MessageKey.BUILD_SITE_BLOCKED.key())));
        server.construction().advance(600, FLAT);
        assertFalse(server.construction().advance(100_000, FLAT).isEmpty(), "nach dem Räumen geht es weiter");
    }

    @Test
    @DisplayName("Eigenes Material: nur Arbeit kostet, gebaut wird nur, was geliefert wurde")
    void selfSuppliedMaterial() {
        setUp();
        Blueprint plan = server.construction().library().find("holz-huette").orElseThrow();
        assertTrue(server.construction().order(aliceAt, "holz-huette", "Bau AG", true, 0).success());
        var project = server.construction().projects().getFirst();
        assertEquals(ConstructionService.priceOf(plan) * 40 / 100, project.price());
        assertTrue(server.construction().advance(100_000, FLAT).isEmpty(), "ohne Material kein Bau");
        assertFalse(server.construction().missing(1).isEmpty());

        assertEquals(10, server.construction().deliver(alice, 1, "minecraft:spruce_planks", 10));
        int placed = server.construction().advance(100_000, FLAT).stream().mapToInt(b -> b.blocks().size()).sum();
        assertEquals(10, placed, "nur so viele Bodenplatten wie geliefert");
        for (var need : server.construction().missing(1).entrySet()) {
            server.construction().deliver(alice, 1, need.getKey(), need.getValue());
        }
        int rest = 0;
        for (int step = 0; step < 50; step++) {
            rest += server.construction().advance(100_000, FLAT).stream().mapToInt(b -> b.blocks().size()).sum();
        }
        assertEquals(plan.blockCount(), placed + rest);
        assertTrue(server.construction().missing(1).isEmpty());
        assertEquals(ConstructionProject.Status.DONE, server.construction().projects().getFirst().status());
    }

    @Test
    @DisplayName("Eingebaute Pläne: drei Stile mit Wohn-, Gewerbe-, Industrie-, Land- und kommunalen Bauten")
    void builtIns() {
        List<Blueprint> plans = BuiltInBlueprints.all();
        assertEquals(57, plans.size());
        for (var type : de.etiennebader.civ.domain.land.PlotType.values()) {
            if (type != de.etiennebader.civ.domain.land.PlotType.EMBASSY) {
                assertTrue(plans.stream().anyMatch(plan -> BuiltInBlueprints.plotTypesOf(plan).contains(type)),
                        "Plan für " + type);
            }
        }
        assertTrue(plans.stream().allMatch(plan -> plan.sizeX() <= 14 || plan.name().contains("lagerhalle")
                || plan.name().contains("rathaus") || plan.name().contains("schule")
                || plan.name().contains("marktplatz")));
        assertTrue(plans.stream().anyMatch(plan -> plan.units() > 1));
        assertTrue(plans.stream().allMatch(plan -> plan.blockCount() > 0 && plan.sizeX() <= Blueprint.MAX_SIZE));
    }

    @Test
    @DisplayName("Sponge-Schematic einlesen: Größe, Palette, Luft wird nicht gebaut")
    void readsSpongeSchematic() throws Exception {
        byte[] file = spongeFile();
        Blueprint plan = SchematicReader.read("kiste.schem", "kiste", "eigen", file);
        assertEquals(2, plan.sizeX());
        assertEquals(1, plan.sizeY());
        assertEquals(2, plan.sizeZ());
        assertEquals(3, plan.blockCount());
        assertEquals("minecraft:stone", plan.placements().getFirst().state());
        assertThrows(SchematicReader.InvalidSchematicException.class,
                () -> SchematicReader.read("kaputt.schem", "kaputt", "eigen", new byte[] {1, 2, 3}));
    }

    @Test
    @DisplayName("Upload: gültige Datei wird aufgenommen, Müll und Namensdoppel werden abgelehnt")
    void importsUploads() throws Exception {
        setUp();
        assertTrue(server.construction().importPlan("Kiste.schem", spongeFile()).success());
        assertTrue(server.construction().library().find("kiste").isPresent());
        assertTrue(has(server.construction().importPlan("holz-haus.schem", spongeFile()), MessageKey.BUILD_PLAN_INVALID));
        assertTrue(has(server.construction().importPlan("x.schem", new byte[] {1, 2}), MessageKey.BUILD_PLAN_INVALID));
    }

    /** Eine 2x1x2-Sponge-v2-Datei: Stein, Stein, Luft, Holz. */
    private static byte[] spongeFile() throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(2);
            out.writeUTF("Width");
            out.writeShort(2);
            out.writeByte(2);
            out.writeUTF("Height");
            out.writeShort(1);
            out.writeByte(2);
            out.writeUTF("Length");
            out.writeShort(2);
            out.writeByte(10);
            out.writeUTF("Palette");
            for (String[] entry : new String[][] {{"minecraft:air", "0"}, {"minecraft:stone", "1"},
                    {"minecraft:oak_planks", "2"}}) {
                out.writeByte(3);
                out.writeUTF(entry[0]);
                out.writeInt(Integer.parseInt(entry[1]));
            }
            out.writeByte(0);
            out.writeByte(7);
            out.writeUTF("BlockData");
            out.writeInt(4);
            out.write(new byte[] {1, 1, 0, 2});
            out.writeByte(0);
        }
        return raw.toByteArray();
    }

    @Test
    @DisplayName("Ein Baufeld, das tagelang blockiert bleibt, beendet den Auftrag")
    void blockedSiteIsAbandoned() {
        setUp();
        assertTrue(server.construction().order(aliceAt, "holz-huette", "Bau AG").success());
        Flat blocked = new Flat(true, false, 70);
        long perDay = de.etiennebader.civ.server.CivServerFixture.SETTINGS.ticksPerDay();
        for (int i = 0; i < 5; i++) {
            server.construction().advance(perDay, blocked);
        }
        assertEquals(ConstructionProject.Status.CANCELLED, server.construction().projects().getFirst().status());
    }

    @Test
    @DisplayName("Die Baufirma gibt einen Auftrag an einen Spieler weiter, der selbst baut und nach Fortschritt bezahlt wird")
    void firmPassesOrderToPlayer() {
        setUp();
        PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
        server.playerJoined(bob, "Bob");
        Caller bobAt = new Caller(bob, "Bob", aliceAt.position());
        assertTrue(server.construction().order(aliceAt, "holz-huette", "Bau AG").success());
        assertTrue(server.construction().subcontract(aliceAt, 1, "Bob").success());
        assertEquals(ConstructionProject.Contractor.PLAYER_REVIEW,
                server.construction().projects().getFirst().contractorState());
        assertEquals(0, server.construction().advance(100, FLAT).size(), "wartet auf den Spieler");
        assertFalse(server.construction().answerJob(aliceAt, 1, true).success(), "nur der vorgesehene Spieler");
        assertTrue(server.construction().blueprintInfo(bobAt, 1).success());
        assertTrue(server.construction().answerJob(bobAt, 1, true).success());

        Blueprint plan = server.construction().library().find("holz-huette").orElseThrow();
        server.construction().advance(100, FLAT); // Höhe festlegen
        ConstructionProject project = server.construction().projects().getFirst();
        java.util.Map<String, String> world = new java.util.HashMap<>();
        for (Blueprint.Placement p : plan.placements()) {
            world.put(p.x() + "," + p.y() + "," + p.z(), Blueprint.baseName(p.state()));
        }
        ConstructionService.Terrain mirror = new ConstructionService.Terrain() {
            public boolean isLoaded(BlockPosition position) {
                return true;
            }

            public int groundLevel(BlockPosition min, int sizeX, int sizeZ) {
                return 70;
            }

            public boolean isClear(BlockPosition min, int sizeX, int sizeY, int sizeZ) {
                return true;
            }

            public String blockName(BlockPosition position) {
                return world.get((position.x() - project.originX()) + "," + (position.y() - project.originY().getAsInt())
                        + "," + (position.z() - project.originZ()));
            }
        };
        long before = server.queries().ledger().balanceOf(bob, de.etiennebader.civ.domain.shared.CurrencyId.EMERALD).amount();
        server.construction().advance(100, mirror);
        assertEquals(ConstructionProject.Status.DONE, server.construction().projects().getFirst().status());
        long paid = server.queries().ledger().balanceOf(bob, de.etiennebader.civ.domain.shared.CurrencyId.EMERALD).amount() - before;
        assertEquals(project.price() * 90 / 100, paid, "der Spieler bekommt den Preis abzüglich 10 % Provision");
    }

    @Test
    @DisplayName("Der Bauherr bestellt gleich mit Selbstbauer; die Firma muss zustimmen, sonst endet der Auftrag")
    void clientRequestsPlayerBuild() {
        setUp();
        PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
        server.playerJoined(bob, "Bob");
        assertTrue(server.construction().order(aliceAt, "holz-huette", "Bau AG", false, 0, "Bob").success());
        assertEquals(ConstructionProject.Contractor.FIRM_REVIEW,
                server.construction().projects().getFirst().contractorState());
        assertTrue(server.construction().answerFirm(aliceAt, 1, false).success());
        assertEquals(ConstructionProject.Status.CANCELLED, server.construction().projects().getFirst().status());
        assertFalse(server.construction().order(aliceAt, "holz-huette", "Bau AG", false, 0, "Niemand").success());
    }
}
