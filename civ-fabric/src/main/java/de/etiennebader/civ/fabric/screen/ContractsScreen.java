package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.contract.ContractStatus;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.civil.ContractView;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Verträge des Spielers und der Polities, die er führt: angebotene zuerst, dann laufende, dann die letzten beendeten.
 * Klick unterschreibt ein Angebot, Rechtsklick lehnt ab oder zieht zurück, Schleichen und Klick kündigt.
 */
public final class ContractsScreen extends MenuScreen {

    private static final int SLOTS = 45;

    public ContractsScreen(CivServer civ, ServerPlayer player, Optional<Screen> parent) {
        super(civ, player, parent);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.CONTRACTS_TITLE.text());
    }

    @Override
    public void render(Canvas canvas) {
        List<ContractView> contracts = civ.civil().contractsOf(Players.ref(player)).stream()
                .sorted(Comparator.comparingInt(ContractsScreen::rank)).limit(SLOTS).toList();
        for (int index = 0; index < contracts.size(); index++) {
            ContractView contract = contracts.get(index);
            canvas.set(index, icon(contract).build(), click -> {
                Feedback.click(click.player());
                if (click.shift() && contract.canNotice()) {
                    report(click, civ.civil().giveNotice(caller(), contract.number()));
                } else if (click.right() && contract.canDecline()) {
                    report(click, civ.civil().decline(caller(), contract.number()));
                } else if (!click.right() && contract.canSign()) {
                    report(click, civ.civil().sign(caller(), contract.number()));
                } else {
                    Feedback.reply(click.player(), civ.civil().show(caller(), contract.number()));
                }
            });
        }
        if (contracts.isEmpty()) {
            canvas.set(22, Icon.of(Items.PAPER).name(Message.of(MessageKey.CONTRACTS_NONE), Icon.INFO).build());
        }
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        backButton(canvas);
        canvas.set(49, Icon.of(Items.KNOWLEDGE_BOOK).name(GuiKeys.CONTRACTS_INFO.text(), Icon.TITLE)
                .line(Message.of(MessageKey.HELP_CONTRACTS), Icon.INFO).build());
    }

    private Icon icon(ContractView contract) {
        Icon icon = Icon.of(item(contract))
                .name(Icon.literal("#" + contract.number() + " " + contract.title(), switch (contract.status()) {
                    case OFFERED -> ChatFormatting.YELLOW;
                    case ACTIVE -> ChatFormatting.GREEN;
                    default -> ChatFormatting.GRAY;
                }))
                .line(GuiKeys.CONTRACT_KIND_LINE.text(civ.civil().kindName(contract.kind()),
                        civ.civil().statusName(contract.status())), Icon.INFO)
                .line(GuiKeys.CONTRACT_WITH.text(contract.other()), Icon.INFO);
        if (!contract.perDay().equals("0,00")) {
            icon.line(GuiKeys.CONTRACT_DAILY.text(contract.perDay(),
                    contract.pays() ? GuiKeys.CONTRACT_YOU_PAY.text() : GuiKeys.CONTRACT_YOU_GET.text()),
                    contract.pays() ? Icon.BAD : Icon.GOOD);
        }
        if (!contract.once().equals("0,00")) {
            icon.line(GuiKeys.CONTRACT_ONCE.text(contract.once()), Icon.INFO);
        }
        if (!contract.deposit().equals("0,00")) {
            icon.line(GuiKeys.CONTRACT_DEPOSIT.text(contract.deposit()), Icon.INFO);
        }
        contract.address().ifPresent(address -> icon.line(GuiKeys.CONTRACT_ADDRESS.text(address), Icon.INFO));
        contract.endsOn().ifPresent(day -> icon.line(GuiKeys.CONTRACT_ENDS_LINE.text(day), Icon.HINT));
        if (!contract.text().isEmpty()) {
            String text = contract.text();
            icon.line(Icon.literal(text.length() > 50 ? text.substring(0, 49) + "…" : text, ChatFormatting.GRAY));
        }
        icon.blank();
        if (contract.canSign()) {
            icon.hint(GuiKeys.CONTRACT_SIGN_HINT.text());
        }
        if (contract.canDecline()) {
            icon.hint(GuiKeys.CONTRACT_DECLINE_HINT.text());
        }
        if (contract.canNotice()) {
            icon.hint(GuiKeys.CONTRACT_NOTICE_HINT.text());
        }
        if (contract.status() == ContractStatus.OFFERED && contract.canSign()) {
            icon.glint();
        }
        return icon;
    }

    private static Item item(ContractView contract) {
        return switch (contract.kind()) {
            case LEASE -> Items.OAK_DOOR;
            case EMPLOYMENT -> Items.IRON_PICKAXE;
            case ALLIANCE -> Items.SHIELD;
            case CUSTOM -> Items.PAPER;
        };
    }

    private static int rank(ContractView contract) {
        return switch (contract.status()) {
            case OFFERED -> 0;
            case ACTIVE -> 1;
            default -> 2;
        };
    }
}
