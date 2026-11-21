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
            for (ConstructionService.Build build : server.construction().advance(2_000, position -> true,
                    position -> 70)) {
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
        assertTrue(server.construction().advance(100_000, position -> false, position -> 70).isEmpty());
    }

    @Test
    @DisplayName("Baugenehmigung: Antrag wartet, das Bauamt genehmigt, dann beginnt der Bau")
    void permit() {
        setUp();
        assertTrue(server.commands().amendLaw(aliceAt, "build_permit_required", "true").success());
        Reply ordered = server.construction().order(aliceAt, "holz-huette", "Bau AG");
        assertTrue(has(ordered, MessageKey.BUILD_ORDERED_PERMIT), ordered.toString());
        assertTrue(server.construction().advance(100_000, position -> true, position -> 70).isEmpty());
        assertTrue(server.construction().approve(aliceAt, 1).success());
        assertFalse(server.construction().advance(100_000, position -> true, position -> 70).isEmpty());
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
    @DisplayName("Eingebaute Pläne: drei Stile mit Hütte, Haus und Mehrfamilienhaus")
    void builtIns() {
        List<Blueprint> plans = BuiltInBlueprints.all();
        assertEquals(9, plans.size());
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
}
