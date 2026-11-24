package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.population.Culture;
import de.etiennebader.civ.domain.population.Race;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.culture.CultureService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Kultur und Rasse der Gemeinde: oben die sieben Kulturen (Kleidung der Dorfbewohner), darunter die vier Rassen
 * (Größe und Hautton). Der Bürgermeister wählt per Klick; alle anderen sehen nur die Auswahl.
 */
public final class CultureScreen extends MenuScreen {

    private static final int[] CULTURE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] RACE_SLOTS = {20, 21, 23, 24};

    private final PolityId town;
    private final String townName;

    public CultureScreen(CivServer civ, ServerPlayer player, PolityId town, String townName, Optional<Screen> parent) {
        super(civ, player, parent);
        this.town = town;
        this.townName = townName;
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.CULTURE_TITLE.text(townName));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(3, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        CultureService.Character current = civ.culture().of(town);
        boolean mayor = civ.queries().polity(town).map(found -> found.holds(
                de.etiennebader.civ.fabric.Players.ref(player), Office.MAYOR)).orElse(false);
        Culture[] cultures = Culture.values();
        for (int i = 0; i < cultures.length; i++) {
            Culture culture = cultures[i];
            boolean chosen = culture == current.culture();
            canvas.set(CULTURE_SLOTS[i], choice(cultureItem(culture), GuiKeys.CULTURE_CULTURE.text(
                    Message.of(TranslationKeys.culture(culture))), chosen, mayor),
                    click -> {
                        if (mayor) {
                            report(click, civ.culture().set(de.etiennebader.civ.fabric.Players.caller(click.player()),
                                    Optional.of(culture), Optional.empty()));
                        }
                    });
        }
        Race[] races = Race.values();
        for (int i = 0; i < races.length; i++) {
            Race race = races[i];
            boolean chosen = race == current.race();
            canvas.set(RACE_SLOTS[i], choice(raceItem(race), GuiKeys.CULTURE_RACE.text(
                    Message.of(TranslationKeys.race(race))), chosen, mayor),
                    click -> {
                        if (mayor) {
                            report(click, civ.culture().set(de.etiennebader.civ.fabric.Players.caller(click.player()),
                                    Optional.empty(), Optional.of(race)));
                        }
                    });
        }
    }

    private static net.minecraft.world.item.ItemStack choice(Item item, Message name, boolean chosen, boolean mayor) {
        Icon icon = Icon.of(item).name(name, chosen ? Icon.GOOD : Icon.TITLE);
        if (chosen) {
            icon.line(GuiKeys.CULTURE_CHOSEN.text(), Icon.GOOD).glint();
        } else if (mayor) {
            icon.line(GuiKeys.CULTURE_PICK_HINT.text(), Icon.HINT);
        } else {
            icon.line(GuiKeys.CULTURE_ONLY_MAYOR.text(), Icon.HINT);
        }
        return icon.build();
    }

    private static Item cultureItem(Culture culture) {
        return switch (culture) {
            case PLAINS -> Items.WHEAT;
            case DESERT -> Items.SAND;
            case JUNGLE -> Items.JUNGLE_SAPLING;
            case SAVANNA -> Items.ACACIA_SAPLING;
            case SNOW -> Items.SNOWBALL;
            case SWAMP -> Items.LILY_PAD;
            case TAIGA -> Items.SPRUCE_SAPLING;
        };
    }

    private static Item raceItem(Race race) {
        return switch (race) {
            case HUMAN -> Items.PLAYER_HEAD;
            case DWARF -> Items.IRON_PICKAXE;
            case ELF -> Items.BOW;
            case ORC -> Items.IRON_AXE;
        };
    }
}
