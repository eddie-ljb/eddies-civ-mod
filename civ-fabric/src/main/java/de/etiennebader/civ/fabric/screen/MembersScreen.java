package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.town.TownOverview;
import de.etiennebader.civ.server.town.Viewer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.Optional;

/**
 * Die Mitglieder einer Gemeinde als Spielerköpfe.
 */
final class MembersScreen extends MenuScreen {

    private static final int PER_PAGE = 45;

    private final PolityId town;

    MembersScreen(CivServer civ, ServerPlayer player, PolityId town, Screen parent) {
        super(civ, player, Optional.of(parent));
        this.town = town;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.MEMBERS_TITLE.text(overview().map(TownOverview::name).orElse("?")));
    }

    private Optional<TownOverview> overview() {
        return civ.queries().polity(town).map(polity -> civ.towns().overview(polity, Players.ref(player)));
    }

    @Override
    public void render(Canvas canvas) {
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
        Optional<TownOverview> found = overview();
        if (found.isEmpty()) {
            return;
        }
        List<String> members = found.get().members();
        for (int index = 0; index < Math.min(members.size(), PER_PAGE); index++) {
            String name = members.get(index);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
            boolean mayor = name.equals(found.get().mayor());
            Icon icon = Icon.of(head).name(Icon.literal(name, mayor ? ChatFormatting.GOLD : ChatFormatting.WHITE));
            if (mayor) {
                icon.line(TownHallScreen.role(Viewer.MAYOR), Icon.TITLE);
            }
            canvas.set(index, icon.build());
        }
    }
}
