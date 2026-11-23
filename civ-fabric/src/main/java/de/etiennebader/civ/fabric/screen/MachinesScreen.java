package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.production.MachineType;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.company.CompanyService;
import de.etiennebader.civ.server.machine.MachineService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Die Maschinen einer Firma: oben die vorhandenen (Klick baut aus, Rechtsklick wartet selbst, Schleichen und Klick
 * beauftragt eine Wartungsfirma), darunter die Typen der Branche zum Kaufen.
 */
public final class MachinesScreen extends MenuScreen {

    private static final int MACHINE_SLOTS = 18;
    private static final int BUY_ROW = 27;

    private final String companyName;

    public MachinesScreen(CivServer civ, ServerPlayer player, String companyName, Optional<Screen> parent) {
        super(civ, player, parent);
        this.companyName = companyName;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.MACHINES_TITLE.text(companyName));
    }

    private static Item itemOf(MachineType type) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("eddies-civ-mod", type.blockId()))
                .orElse(Items.IRON_BLOCK);
    }

    private static Message plain(String text) {
        return Message.of(MessageKey.RELIGION_DONE, text);
    }

    @Override
    public void render(Canvas canvas) {
        String quoted = "\"" + companyName.replace("\"", "") + "\"";
        List<MachineService.MachineView> machines = civ.machines().views(companyName);
        boolean firm = civ.machines().maintenanceFirmAvailable(companyName);
        for (int index = 0; index < Math.min(MACHINE_SLOTS, machines.size()); index++) {
            MachineService.MachineView view = machines.get(index);
            Message state = Message.of(view.broken() ? MessageKey.MACHINE_STATE_BROKEN
                    : view.placed() ? MessageKey.MACHINE_STATE_RUNNING : MessageKey.MACHINE_STATE_UNPLACED);
            Icon icon = Icon.of(itemOf(view.type()))
                    .name(Message.of(MachineService.typeKey(view.type())), view.broken() ? Icon.BAD : Icon.TITLE)
                    .line(Message.of(MessageKey.MACHINE_LINE, view.number(), Message.of(MachineService.typeKey(view.type())),
                            view.level(), view.wear(), state), view.broken() ? Icon.BAD : Icon.INFO)
                    .line(GuiKeys.MACHINE_OUTPUT_LINE.text(view.type().outputPercent()), Icon.HINT);
            if (view.upgradeCost() > 0) {
                icon.hint(GuiKeys.MACHINE_UPGRADE_HINT.text(CompanyService.money(view.upgradeCost())));
            }
            if (view.wear() > 0) {
                icon.hint(GuiKeys.MACHINE_MAINTAIN_HINT.text(CompanyService.money(view.maintainCost())));
                if (firm) {
                    icon.hint(GuiKeys.MACHINE_MAINTAIN_FIRM_HINT.text(CompanyService.money(view.firmCost())));
                }
            }
            if (view.broken()) {
                icon.glint();
            }
            canvas.set(index, icon.build(), click -> {
                Feedback.click(click.player());
                Reply reply;
                if (click.shift() && view.wear() > 0) {
                    reply = civ.machines().maintain(Players.caller(click.player()), companyName, view.number(), true);
                } else if (click.right() && view.wear() > 0) {
                    reply = civ.machines().maintain(Players.caller(click.player()), companyName, view.number(), false);
                } else {
                    reply = civ.machines().upgrade(Players.caller(click.player()), companyName, view.number());
                }
                report(click, reply);
                Screens.open(click.player(), this);
            });
        }
        List<MachineType> types = civ.machines().typesFor(companyName);
        if (types.isEmpty()) {
            canvas.set(BUY_ROW + 4, Icon.of(Items.BARRIER).name(GuiKeys.MACHINE_NO_BRANCH.text(), Icon.BAD).build());
        } else {
            canvas.set(BUY_ROW - 1, Icon.of(Items.ANVIL).name(GuiKeys.MACHINE_BUY_HEADER.text(), Icon.TITLE).build());
        }
        for (int index = 0; index < types.size(); index++) {
            MachineType type = types.get(index);
            canvas.set(BUY_ROW + index, Icon.of(itemOf(type))
                    .name(Message.of(MachineService.typeKey(type)), Icon.TITLE)
                    .line(GuiKeys.MACHINE_OUTPUT_LINE.text(type.outputPercent()), Icon.HINT)
                    .hint(GuiKeys.MACHINE_BUY_HINT.text(CompanyService.money(type.price()))).build(), click -> {
                        Feedback.click(click.player());
                        MachineService.Purchase purchase = civ.machines().buy(Players.caller(click.player()),
                                companyName, type.id());
                        purchase.type().ifPresent(bought -> click.player().getInventory().placeItemBackInInventory(
                                new ItemStack(itemOf(bought)), Prediction.SERVER_ONLY));
                        report(click, purchase.reply());
                        Screens.open(click.player(), this);
                    });
        }
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        backButton(canvas);
    }
}
