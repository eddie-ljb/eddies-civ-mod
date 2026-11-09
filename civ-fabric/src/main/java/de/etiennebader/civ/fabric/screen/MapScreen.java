package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.territory.Relation;
import de.etiennebader.civ.server.town.ChunkMap;
import de.etiennebader.civ.server.town.MapCell;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Karte der Chunks um den Spieler, Norden oben. Eigene Chunks grün, fremde rot, Wildnis als Gras.
 * Ein Klick beansprucht den Chunk für die eigene Gemeinde.
 */
public final class MapScreen extends MenuScreen {

    private static final int WIDTH = 9;
    private static final int HEIGHT = 5;

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
        ChunkMap map = civ.towns().map(caller(), WIDTH, HEIGHT);
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
    }

    private static net.minecraft.world.item.ItemStack icon(MapCell cell) {
        boolean outpost = cell.type().filter(ClaimType.OUTPOST::equals).isPresent();
        Item item = switch (cell.relation()) {
            case OWN -> outpost ? Items.CONCRETE.green() : Items.CONCRETE.lime();
            case FOREIGN -> outpost ? Items.CONCRETE.orange() : Items.CONCRETE.red();
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
}
