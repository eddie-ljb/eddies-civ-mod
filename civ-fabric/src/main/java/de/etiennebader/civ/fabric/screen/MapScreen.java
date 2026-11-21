package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.federation.Level;
import de.etiennebader.civ.server.territory.Relation;
import de.etiennebader.civ.server.town.ChunkMap;
import de.etiennebader.civ.server.town.MapCell;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Karte der Chunks um den Spieler, Norden oben. Flächen zeigen die Farbe des zugehörigen Bundes, standardmäßig nach
 * dessen höchster Ebene (Nation &gt; Land &gt; Gemeinde, siehe {@link de.etiennebader.civ.server.level.TerritoryColor});
 * ein Knopf grenzt die Einfärbung auf eine bestimmte Ebene ein. Eigene Chunks heißen grün, fremde rot. Ein Klick
 * beansprucht den Chunk für die eigene Gemeinde.
 */
public final class MapScreen extends MenuScreen {

    private static final int WIDTH = 9;
    private static final int HEIGHT = 5;

    /** Palette für Bund-Farben auf der Karte; je Bund-Kennung immer dieselbe Farbe. */
    private static final List<Supplier<Item>> PALETTE = List.of(
            () -> Items.CONCRETE.red(), () -> Items.CONCRETE.orange(), () -> Items.CONCRETE.yellow(),
            () -> Items.CONCRETE.lime(), () -> Items.CONCRETE.green(), () -> Items.CONCRETE.cyan(),
            () -> Items.CONCRETE.lightBlue(), () -> Items.CONCRETE.blue(), () -> Items.CONCRETE.purple(),
            () -> Items.CONCRETE.brown(), () -> Items.CONCRETE.gray(), () -> Items.CONCRETE.black(),
            () -> Items.CONCRETE.white());

    /** Leer = höchste Ebene; sonst die Ebene, auf die die Einfärbung eingegrenzt ist. Ignoriert, wenn {@link #electionMode}. */
    private Optional<Level> levelFilter = Optional.empty();

    /** Statt der Bundesfarbe die Wahlkarte der eigenen Gemeinde zeigen: Sieger je Wahlbezirk. */
    private boolean electionMode = false;

    public MapScreen(CivServer civ, ServerPlayer player, Optional<Screen> parent) {
        super(civ, player, parent);
    }

    @Override
    public int rows() {
        return HEIGHT + 1;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.MAP_TITLE.text());
    }

    @Override
    public void render(Canvas canvas) {
        Optional<de.etiennebader.civ.domain.shared.PolityId> ownTown = civ.queries().municipalityOf(caller().player())
                .map(de.etiennebader.civ.domain.polity.PolityView::id);
        ChunkMap map = electionMode && ownTown.isPresent()
                ? civ.towns().mapByElection(caller(), WIDTH, HEIGHT, ownTown.get(), Optional.empty())
                : civ.towns().map(caller(), WIDTH, HEIGHT, levelFilter.map(Level::id));
        for (int row = 0; row < HEIGHT; row++) {
            for (int column = 0; column < WIDTH; column++) {
                MapCell cell = map.at(column, row);
                canvas.set(column, row, icon(cell), click -> {
                    if (cell.relation() == Relation.FOREIGN) {
                        return;
                    }
                    ClaimType type = click.right() ? ClaimType.OUTPOST : ClaimType.CORE;
                    report(click, civ.towns().claimFromMap(caller(), cell.chunk(), type));
                });
            }
        }
        canvas.fillRow(HEIGHT, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        canvas.set(HEIGHT * 9 + 4, Icon.of(Items.COMPASS).name(GuiKeys.MAP_TITLE.text())
                .line(GuiKeys.MAP_OWN.text("■"), Icon.GOOD)
                .line(GuiKeys.MAP_FOREIGN.text("■"), Icon.BAD)
                .line(GuiKeys.MAP_WILDERNESS.text(), Icon.INFO)
                .blank()
                .hint(GuiKeys.MAP_CLAIM_HINT.text())
                .hint(GuiKeys.MAP_OUTPOST_HINT.text()).build());
        canvas.set(HEIGHT * 9 + 5, levelIcon(), click -> {
            Feedback.click(click.player());
            levelFilter = nextLevel();
            Screens.open(click.player(), this);
        });
        canvas.set(HEIGHT * 9 + 6, electionIcon(), click -> {
            Feedback.click(click.player());
            electionMode = !electionMode;
            Screens.open(click.player(), this);
        });
    }

    /** Kreis durch die Ebenen, von der höchsten (Standard, {@code leer}) abwärts bis zur Gemeinde. */
    private Optional<Level> nextLevel() {
        List<Level> descending = new ArrayList<>(civ.federations().levels().all());
        Collections.reverse(descending);
        if (descending.isEmpty()) {
            return Optional.empty();
        }
        if (levelFilter.isEmpty()) {
            return Optional.of(descending.getFirst());
        }
        int index = descending.indexOf(levelFilter.get());
        if (index < 0 || index + 1 >= descending.size()) {
            return Optional.empty();
        }
        return Optional.of(descending.get(index + 1));
    }

    private ItemStack levelIcon() {
        Message name = levelFilter.map(level -> GuiKeys.MAP_LEVEL.text(level.id()))
                .orElseGet(GuiKeys.MAP_LEVEL_HIGHEST::text);
        return Icon.of(Items.FILLED_MAP).name(name, Icon.TITLE).hint(GuiKeys.MAP_LEVEL_HINT.text()).build();
    }

    private ItemStack electionIcon() {
        Message name = electionMode ? GuiKeys.MAP_ELECTION_ON.text() : GuiKeys.MAP_ELECTION_OFF.text();
        return Icon.of(Items.PAPER).name(name, Icon.TITLE).hint(GuiKeys.MAP_ELECTION_HINT.text()).build();
    }

    private static ItemStack icon(MapCell cell) {
        boolean outpost = cell.type().filter(ClaimType.OUTPOST::equals).isPresent();
        Item item = switch (cell.relation()) {
            case OWN, FOREIGN -> cell.group().map(MapScreen::colorFor).orElseGet(Items.CONCRETE::gray);
            case WILDERNESS -> Items.GRASS_BLOCK;
        };
        Message name = switch (cell.relation()) {
            case OWN -> GuiKeys.MAP_OWN.text(cell.owner().orElse("?"));
            case FOREIGN -> GuiKeys.MAP_FOREIGN.text(cell.owner().orElse("?"));
            case WILDERNESS -> GuiKeys.MAP_WILDERNESS.text();
        };
        int color = switch (cell.relation()) {
            case OWN -> Icon.GOOD;
            case FOREIGN -> Icon.BAD;
            case WILDERNESS -> Icon.INFO;
        };
        Icon icon = Icon.of(item).name(name, color).line(GuiKeys.MAP_COORDS.text(cell.chunk().x(), cell.chunk().z()));
        cell.type().ifPresent(type -> icon.line(Message.of(TranslationKeys.claimType(type))));
        if (cell.here()) {
            icon.line(GuiKeys.MAP_HERE.text(), Icon.TITLE).glint();
        }
        if (cell.relation() != Relation.FOREIGN) {
            icon.blank().hint(GuiKeys.MAP_CLAIM_HINT.text()).hint(GuiKeys.MAP_OUTPOST_HINT.text());
        }
        return icon.build();
    }

    /** Immer dieselbe Farbe für dieselbe Bund- oder Gemeinde-Kennung. */
    private static Item colorFor(String group) {
        int index = Math.floorMod(group.hashCode(), PALETTE.size());
        return PALETTE.get(index).get();
    }
}
