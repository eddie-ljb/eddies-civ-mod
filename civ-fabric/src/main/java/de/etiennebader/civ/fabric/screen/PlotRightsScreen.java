package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.protection.PlotFlag;
import de.etiennebader.civ.domain.protection.PlotRights;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.protection.Stance;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.plot.PlotRightsService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Was auf einem Grundstück erlaubt ist: Bauen, Türen und Behälter für Mitglieder und Fremde, dazu die Regeln, die sonst
 * das Gesetz festlegt (PvP, Monster, Explosionen, Feuer, Tierschutz). Gäste einzelner Spieler stellt
 * {@code /civ grundstueck rechte gast} ein.
 */
final class PlotRightsScreen extends MenuScreen {

    private static final int[] MEMBER_SLOTS = {10, 11, 12};
    private static final int[] OUTSIDER_SLOTS = {14, 15, 16};
    private static final int[] RULE_SLOTS = {28, 29, 30, 31, 32};
    private static final int[] GUEST_SLOTS = {18, 19, 20, 21, 22, 23, 24, 25};
    private static final ProtectedAction[] ACTIONS = {ProtectedAction.BUILD, ProtectedAction.INTERACT,
            ProtectedAction.OPEN_CONTAINER};

    private final PolityId town;
    private final PlotId plot;
    private final String townName;

    PlotRightsScreen(CivServer civ, ServerPlayer player, PolityId town, PlotId plot, String townName, Screen parent) {
        super(civ, player, Optional.of(parent));
        this.town = town;
        this.plot = plot;
        this.townName = townName;
    }

    @Override
    public int rows() {
        return 5;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.PLOT_RIGHTS_TITLE.text(townName));
    }

    @Override
    public void render(Canvas canvas) {
        Optional<PlotRightsService.View> found = civ.plotRights().view(Players.ref(player), town, plot);
        if (found.isEmpty()) {
            canvas.set(22, Icon.of(Items.BARRIER).name(Message.of(MessageKey.PLOT_NONE_HERE)).build());
            backButton(canvas);
            return;
        }
        PlotRightsService.View view = found.get();
        PlotRights rights = view.rights();
        canvas.set(4, Icon.of(Items.OAK_SIGN).name(GuiKeys.PLOT_RIGHTS_INFO.text(), Icon.TITLE)
                .line(Message.of(MessageKey.PLOT_RIGHTS_HEADER, "", townName, Message.of(view.overrideAllowed()
                        ? MessageKey.PLOT_RIGHTS_OVERRIDE_ON : MessageKey.PLOT_RIGHTS_OVERRIDE_OFF)), Icon.INFO)
                .line(GuiKeys.PLOT_RIGHTS_GUESTS.text(view.guestNames().isEmpty() ? "-"
                        : String.join(", ", view.guestNames())))
                .hint(view.canManage() ? GuiKeys.PLOT_RIGHTS_TOGGLE_HINT.text() : GuiKeys.PLOT_RIGHTS_NOT_MANAGER.text())
                .build());
        canvas.set(9, Icon.of(Items.PAPER).name(GuiKeys.PLOT_RIGHTS_MEMBERS.text(), Icon.TITLE).build());
        canvas.set(13, Icon.of(Items.PAPER).name(GuiKeys.PLOT_RIGHTS_OUTSIDERS.text(), Icon.TITLE).build());
        for (int i = 0; i < ACTIONS.length; i++) {
            ProtectedAction action = ACTIONS[i];
            boolean members = rights.members().contains(action);
            boolean outsiders = rights.outsiders().contains(action);
            canvas.set(MEMBER_SLOTS[i], toggle(action, members, view.canManage()),
                    click -> change(click.player(), view, r -> r.withMembers(action, !r.members().contains(action))));
            canvas.set(OUTSIDER_SLOTS[i], toggle(action, outsiders, view.canManage()),
                    click -> change(click.player(), view, r -> r.withOutsiders(action, !r.outsiders().contains(action))));
        }
        PlotFlag[] flags = PlotFlag.values();
        for (int i = 0; i < flags.length && i < RULE_SLOTS.length; i++) {
            PlotFlag flag = flags[i];
            PlotRightsService.RuleView rule = view.rules().get(i);
            Icon icon = Icon.of(ruleItem(flag)).name(PlotRightsService.ruleName(flag),
                            rule.effective() ? Icon.GOOD : Icon.BAD)
                    .line(Message.of(MessageKey.PLOT_RIGHTS_RULE, PlotRightsService.ruleName(flag),
                            PlotRightsService.stanceName(rule.stance()), Message.of(rule.effective()
                                    ? MessageKey.PLOT_RIGHTS_ALLOWED : MessageKey.PLOT_RIGHTS_FORBIDDEN)));
            if (view.canManage()) {
                icon.hint(GuiKeys.PLOT_RIGHTS_RULE_HINT.text());
            }
            if (rule.effective()) {
                icon.glint();
            }
            canvas.set(RULE_SLOTS[i], icon.build(), click -> change(click.player(), view,
                    r -> r.withStance(flag, next(r.stance(flag)))));
        }
        int index = 0;
        for (var entry : rights.guests().entrySet()) {
            if (index >= GUEST_SLOTS.length) {
                break;
            }
            var guest = entry.getKey();
            var actions = entry.getValue();
            String name = civ.texts().actor(guest);
            Icon icon = Icon.of(Items.PLAYER_HEAD).name(GuiKeys.PLOT_RIGHTS_GUEST_ENTRY.text(name), Icon.TITLE)
                    .line(Message.of(MessageKey.PLOT_RIGHTS_ACTIONS,
                            Message.of(actions.contains(ProtectedAction.BUILD) ? MessageKey.PLOT_RIGHTS_ALLOWED
                                    : MessageKey.PLOT_RIGHTS_FORBIDDEN),
                            Message.of(actions.contains(ProtectedAction.INTERACT) ? MessageKey.PLOT_RIGHTS_ALLOWED
                                    : MessageKey.PLOT_RIGHTS_FORBIDDEN),
                            Message.of(actions.contains(ProtectedAction.OPEN_CONTAINER) ? MessageKey.PLOT_RIGHTS_ALLOWED
                                    : MessageKey.PLOT_RIGHTS_FORBIDDEN)));
            if (view.canManage()) {
                icon.hint(GuiKeys.PLOT_RIGHTS_GUEST_HINT.text());
            }
            canvas.set(GUEST_SLOTS[index++], icon.build(), click -> change(click.player(), view, r -> {
                if (click.shift()) {
                    return r.withoutGuest(guest);
                }
                ProtectedAction action = click.right() ? ProtectedAction.INTERACT : ProtectedAction.BUILD;
                return r.withGuest(guest, action, !actions.contains(action));
            }));
        }
        if (view.canManage() && rights.guests().size() < PlotRights.MAX_GUESTS) {
            canvas.set(26, Icon.of(Items.WRITABLE_BOOK).name(GuiKeys.PLOT_RIGHTS_GUEST_ADD.text(), Icon.GOOD).build(),
                    click -> prompt(click, GuiKeys.PLOT_RIGHTS_GUEST_ADD.text(),
                            "/civ grundstueck rechte gast "));
        }
        canvas.set(34, Icon.of(Items.BARRIER).name(GuiKeys.PLOT_RIGHTS_RESET.text(), Icon.BAD).build(),
                click -> change(click.player(), view, r -> PlotRights.DEFAULT));
        canvas.fillRow(4, Icon.of(Items.STAINED_GLASS_PANE.black()).hidden().build());
        backButton(canvas);
    }

    private void change(ServerPlayer clicker, PlotRightsService.View view, UnaryOperator<PlotRights> change) {
        de.etiennebader.civ.server.command.Reply reply = civ.plotRights().change(Players.ref(clicker), view.town(),
                view.plot(), change);
        de.etiennebader.civ.fabric.Feedback.inMenu(clicker, reply);
    }

    private static Stance next(Stance stance) {
        return switch (stance) {
            case INHERIT -> Stance.ALLOW;
            case ALLOW -> Stance.DENY;
            case DENY -> Stance.INHERIT;
        };
    }

    private net.minecraft.world.item.ItemStack toggle(ProtectedAction action, boolean on, boolean manage) {
        Message name = Message.of(switch (action) {
            case BUILD -> MessageKey.PLOT_RIGHTS_ACTION_BUILD;
            case INTERACT -> MessageKey.PLOT_RIGHTS_ACTION_INTERACT;
            case OPEN_CONTAINER -> MessageKey.PLOT_RIGHTS_ACTION_CONTAINER;
        });
        Icon icon = Icon.of(actionItem(action)).name(name, on ? Icon.GOOD : Icon.BAD)
                .line(Message.of(on ? MessageKey.PLOT_RIGHTS_ALLOWED : MessageKey.PLOT_RIGHTS_FORBIDDEN),
                        on ? Icon.GOOD : Icon.BAD);
        if (on) {
            icon.glint();
        }
        if (manage) {
            icon.hint(GuiKeys.PLOT_RIGHTS_TOGGLE_HINT.text());
        }
        return icon.build();
    }

    private static Item actionItem(ProtectedAction action) {
        return switch (action) {
            case BUILD -> Items.IRON_PICKAXE;
            case INTERACT -> Items.OAK_DOOR;
            case OPEN_CONTAINER -> Items.CHEST;
        };
    }

    private static Item ruleItem(PlotFlag flag) {
        return switch (flag) {
            case PVP -> Items.IRON_SWORD;
            case HOSTILE_MOBS -> Items.CREEPER_SPAWN_EGG;
            case EXPLOSIONS -> Items.TNT;
            case FIRE_SPREAD -> Items.FLINT_AND_STEEL;
            case OUTSIDERS_HURT_ANIMALS -> Items.LEATHER;
        };
    }
}
