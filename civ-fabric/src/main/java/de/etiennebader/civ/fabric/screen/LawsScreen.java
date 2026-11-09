package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.town.LawSetting;
import de.etiennebader.civ.server.town.TownOverview;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Die Regelungen einer Gemeinde. Bürger schalten Ja/Nein-Regelungen per Klick um und verstellen Zahlen
 * mit Links- und Rechtsklick.
 */
final class LawsScreen extends MenuScreen {

    private static final Map<String, Item> ICONS = Map.of(
            "land_tax_per_block", Items.GOLD_NUGGET,
            "land_tax_free_blocks", Items.GRASS_BLOCK,
            "land_tax_grace_days", Items.CLOCK,
            "tax_embassies", Items.BANNER.white(),
            "max_outposts", Items.LANTERN,
            "outsiders_may_build", Items.BRICKS,
            "outsiders_may_interact", Items.OAK_DOOR,
            "outsiders_may_open_containers", Items.CHEST);
    private static final Map<String, Long> STEPS = Map.of("land_tax_free_blocks", 16L);
    private static final String MONEY_LAW = "land_tax_per_block";
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final PolityId town;

    LawsScreen(CivServer civ, ServerPlayer player, PolityId town, Screen parent) {
        super(civ, player, Optional.of(parent));
        this.town = town;
    }

    @Override
    public int rows() {
        return 5;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.LAWS_TITLE.text(overview().map(TownOverview::name).orElse("?")));
    }

    private Optional<TownOverview> overview() {
        return civ.queries().polity(town).map(polity -> civ.towns().overview(polity, Players.ref(player)));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(4, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        Optional<TownOverview> found = overview();
        if (found.isEmpty()) {
            return;
        }
        boolean citizen = found.get().viewer().isCitizen();
        List<LawSetting> laws = civ.towns().laws(found.get());
        for (int index = 0; index < Math.min(laws.size(), SLOTS.length); index++) {
            LawSetting law = laws.get(index);
            canvas.set(SLOTS[index], icon(law, citizen), click -> {
                if (!citizen) {
                    return;
                }
                if (law.isSwitch()) {
                    report(click, civ.towns().toggleLaw(caller(), law));
                } else {
                    long step = STEPS.getOrDefault(law.id(), 1L) * click.multiplier();
                    report(click, civ.towns().adjustLaw(caller(), law, click.right() ? -step : step));
                }
            });
        }
    }

    private net.minecraft.world.item.ItemStack icon(LawSetting law, boolean citizen) {
        Icon icon = Icon.of(ICONS.getOrDefault(law.id(), Items.PAPER)).name(law.name());
        Object value = law.id().equals(MONEY_LAW)
                ? money(Money.of(law.number(), CurrencyId.EMERALD))
                : civ.texts().lawValue(law.value());
        boolean on = law.isSwitch() ? law.enabled() : law.number() > 0;
        icon.line(GuiKeys.LAW_VALUE.text(value), on ? Icon.GOOD : Icon.INFO);
        icon.hint(GuiKeys.LAW_ID.text(law.id()));
        icon.blank();
        if (!citizen) {
            icon.hint(GuiKeys.LAW_CITIZENS_ONLY.text());
        } else if (law.isSwitch()) {
            icon.hint(GuiKeys.LAW_TOGGLE_HINT.text());
        } else {
            icon.hint(GuiKeys.LAW_ADJUST_HINT.text(STEPS.getOrDefault(law.id(), 1L)));
        }
        if (law.isSwitch() && law.enabled()) {
            icon.glint();
        }
        return icon.build();
    }
}
