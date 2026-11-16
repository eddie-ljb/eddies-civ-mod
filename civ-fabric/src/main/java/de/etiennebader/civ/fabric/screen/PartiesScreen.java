package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.party.PartyView;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Alle Parteien mit Wappen, Richtung und Mitgliedern. Klick tritt bei, Umschalt-Klick auf die eigene tritt aus.
 */
public final class PartiesScreen extends MenuScreen {

    private static final int FIRST_SLOT = 9;
    private static final int LAST_SLOT = 44;

    public PartiesScreen(CivServer civ, ServerPlayer player, Optional<Screen> parent) {
        super(civ, player, parent);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.PARTIES_TITLE.text());
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(0, Icon.of(Items.STAINED_GLASS_PANE.purple()).hidden().build());
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.purple()).hidden().build());
        backButton(canvas);
        canvas.set(4, Icon.of(Items.BANNER.white()).name(GuiKeys.PARTIES_TITLE.text(), Icon.TITLE)
                .hint(GuiKeys.PARTIES_FOUND_HINT.text()).build());
        List<PartyView> parties = civ.parties().parties(Players.ref(player));
        if (parties.isEmpty()) {
            canvas.set(22, Icon.of(Items.PAPER).name(GuiKeys.PARTIES_EMPTY.text(), Icon.INFO)
                    .hint(GuiKeys.PARTIES_FOUND_HINT.text()).build());
            return;
        }
        int slot = FIRST_SLOT;
        for (PartyView party : parties) {
            if (slot > LAST_SLOT) {
                break;
            }
            canvas.set(slot++, icon(party), click -> {
                if (party.member() && click.shift()) {
                    report(click, civ.parties().leave(caller()));
                } else if (!party.member()) {
                    report(click, civ.parties().join(caller(), party.id()));
                }
            });
        }
    }

    private static ItemStack icon(PartyView party) {
        Icon icon = Icon.of(emblem(party.emblem())).name(Icon.literal(party.name(), ChatFormatting.GOLD))
                .line(GuiKeys.PARTY_PLATFORM_LINE.text(Message.of(TranslationKeys.platform(party.platform()))),
                        Icon.INFO)
                .line(GuiKeys.PARTY_DIRECTION_LINE.text(party.order(), party.economy()), Icon.HINT)
                .line(GuiKeys.PARTY_CHAIR_LINE.text(party.chair()))
                .line(GuiKeys.PARTY_MEMBERS_LINE.text(party.players(), party.villagers()))
                .blank();
        if (party.member()) {
            icon.line(GuiKeys.PARTY_OWN.text(), Icon.GOOD).hint(GuiKeys.PARTY_LEAVE_HINT.text()).glint();
        } else {
            icon.hint(GuiKeys.PARTY_JOIN_HINT.text());
        }
        return icon.build();
    }

    /** Das Item zum Wappen; unbekannte IDs werden zum weißen Banner. */
    static Item emblem(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) {
            return Items.BANNER.white();
        }
        return BuiltInRegistries.ITEM.getOptional(key).orElse(Items.BANNER.white());
    }
}
