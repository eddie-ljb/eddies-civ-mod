package de.etiennebader.civ.fabric.screen;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.production.Branch;
import de.etiennebader.civ.domain.production.Enterprise;
import de.etiennebader.civ.fabric.Components;
import de.etiennebader.civ.fabric.Feedback;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.fabric.gui.Canvas;
import de.etiennebader.civ.fabric.gui.Icon;
import de.etiennebader.civ.fabric.gui.Screen;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.company.CompanyService;
import de.etiennebader.civ.server.company.ListingView;
import de.etiennebader.civ.server.command.GuiKeys;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Das Menü einer Firma: Branche, Ausstattung, Verkauf, Lager und Belegschaft. Ändern darf nur die Leitung; alle anderen
 * sehen nur hin.
 */
public final class CompanyScreen extends MenuScreen {

    private static final int FIRST_BRANCH = 9;
    private static final int FIRST_STOCK = 18;
    private static final int STOCK_SLOTS = 18;
    private static final int FIRST_EMPLOYEE = 36;
    private static final int EMPLOYEE_SLOTS = 8;

    private final String companyName;

    public CompanyScreen(CivServer civ, ServerPlayer player, String companyName, Optional<Screen> parent) {
        super(civ, player, parent);
        this.companyName = companyName;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return Components.of(GuiKeys.COMPANY_MENU_TITLE.text(companyName));
    }

    @Override
    public void render(Canvas canvas) {
        Optional<PolityView> company = civ.companies().company(companyName);
        if (company.isEmpty()) {
            canvas.set(22, Icon.of(Items.BARRIER).name(Message.of(MessageKey.COMPANY_UNKNOWN, companyName), Icon.BAD)
                    .build());
            backButton(canvas);
            return;
        }
        PolityView firm = company.get();
        String quoted = "\"" + firm.name().value().replace("\"", "") + "\"";
        Optional<Enterprise> enterprise = civ.queries().production().enterpriseOf(firm.id());
        Optional<CompanyService.EnterpriseView> view = civ.companies().enterprise(firm);
        renderHeader(canvas, firm, enterprise, view, quoted);
        renderBranches(canvas, enterprise, quoted);
        enterprise.ifPresent(found -> renderStock(canvas, found, quoted));
        renderEmployees(canvas, firm, quoted);
        canvas.fillRow(5, Icon.of(Items.STAINED_GLASS_PANE.gray()).hidden().build());
        backButton(canvas);
        if (enterprise.isPresent()) {
            canvas.set(49, Icon.of(Items.EMERALD).name(GuiKeys.COMPANY_SELL_NOW.text(), Icon.TITLE).build(),
                    click -> act(click, civ.companies().sell(Players.caller(click.player()), quoted, "jetzt")));
        }
    }

    private void renderHeader(Canvas canvas, PolityView firm, Optional<Enterprise> enterprise,
                              Optional<CompanyService.EnterpriseView> view, String quoted) {
        Optional<ListingView> listing = civ.companies().listing(firm);
        Icon info = Icon.of(Items.BELL).name(Icon.literal(firm.name().value(), ChatFormatting.GOLD))
                .line(GuiKeys.COMPANY_HEAD.text(firm.mayor().map(civ.texts()::actor).orElse("-")), Icon.INFO);
        listing.ifPresent(found -> info.line(GuiKeys.COMPANY_TREASURY.text(found.treasury()), Icon.INFO));
        canvas.set(0, info.build());
        if (enterprise.isEmpty() || view.isEmpty()) {
            canvas.set(2, Icon.of(Items.CRAFTING_TABLE).name(GuiKeys.COMPANY_BRANCH_NONE.text(), Icon.HINT).build());
            return;
        }
        Enterprise found = enterprise.get();
        CompanyService.EnterpriseView details = view.get();
        Icon branch = Icon.of(iconFor(found.branch())).name(GuiKeys.COMPANY_BRANCH_CURRENT.text(
                CompanyService.branchName(found.branch())), Icon.TITLE)
                .line(GuiKeys.COMPANY_WORKFORCE.text(String.format(Locale.GERMANY, "%.1f", details.workforce())),
                        Icon.INFO);
        if (found.lastDay().isPresent()) {
            branch.line(GuiKeys.COMPANY_LAST.text(found.lastOutput(), CompanyService.money(found.lastRevenue())),
                    Icon.INFO);
        }
        canvas.set(2, branch.build());
        Icon forecast = Icon.of(Items.CLOCK).name(GuiKeys.COMPANY_FORECAST.text(), Icon.TITLE);
        details.forecast().forEach((id, quantity) -> forecast.line(GuiKeys.COMPANY_STOCK_ITEM.text(quantity,
                Message.of(CompanyService.itemKey(Commodity.of(id)))), Icon.INFO));
        canvas.set(3, forecast.build());
        Icon equipment = Icon.of(Items.ANVIL).name(GuiKeys.COMPANY_EQUIPMENT.text(found.level(), found.level() * 25L),
                Icon.TITLE);
        if (found.toNextLevel() > 0) {
            equipment.line(GuiKeys.COMPANY_EQUIPMENT_NEXT.text(MoneyFormat.whole(found.toNextLevel())), Icon.INFO);
        }
        canvas.set(5, equipment.hint(GuiKeys.COMPANY_INVEST_HINT.text()).build(),
                click -> act(click, civ.companies().invest(Players.caller(click.player()), quoted,
                        click.shift() ? "100" : "10")));
        Message mode = Message.of(found.autoSell() ? MessageKey.PRODUCTION_SELL_AUTO : MessageKey.PRODUCTION_SELL_MANUAL);
        canvas.set(7, Icon.of(found.autoSell() ? Items.HOPPER : Items.CHEST).name(GuiKeys.COMPANY_AUTOSELL.text(mode),
                        Icon.TITLE).hint(GuiKeys.COMPANY_AUTOSELL_HINT.text()).build(),
                click -> act(click, civ.companies().sell(Players.caller(click.player()), quoted,
                        found.autoSell() ? "hand" : "automatisch")));
    }

    private void renderBranches(Canvas canvas, Optional<Enterprise> enterprise, String quoted) {
        Branch[] branches = Branch.values();
        for (int index = 0; index < Math.min(9, branches.length); index++) {
            Branch branch = branches[index];
            boolean current = enterprise.filter(found -> found.branch() == branch).isPresent();
            Icon icon = Icon.of(iconFor(branch)).name(CompanyService.branchName(branch), Icon.TITLE);
            for (Branch.Yield yield : branch.yields()) {
                icon.line(GuiKeys.COMPANY_YIELD_LINE.text(yield.perWorker(),
                        Message.of(CompanyService.itemKey(yield.asCommodity()))), Icon.INFO);
            }
            if (current) {
                icon.glint();
            } else {
                icon.hint(GuiKeys.COMPANY_BRANCH_HINT.text());
            }
            if (current) {
                canvas.set(FIRST_BRANCH + index, icon.build());
            } else {
                canvas.set(FIRST_BRANCH + index, icon.build(), click -> act(click, civ.companies()
                        .chooseBranch(Players.caller(click.player()), quoted, branch.id())));
            }
        }
    }

    private void renderStock(Canvas canvas, Enterprise enterprise, String quoted) {
        int slot = FIRST_STOCK;
        for (Map.Entry<Commodity, Long> entry : enterprise.stock().entrySet()) {
            if (slot >= FIRST_STOCK + STOCK_SLOTS) {
                break;
            }
            Optional<Item> item = item(entry.getKey().id());
            if (item.isEmpty()) {
                continue;
            }
            String id = entry.getKey().id();
            ItemStack stack = Icon.of(item.get()).name(GuiKeys.COMPANY_STOCK_ITEM.text(entry.getValue(),
                    Message.of(CompanyService.itemKey(entry.getKey()))), Icon.TITLE)
                    .hint(GuiKeys.COMPANY_STOCK_HINT.text()).build();
            stack.setCount((int) Math.max(1, Math.min(64, entry.getValue())));
            canvas.set(slot++, stack, click -> {
                int wanted = click.shift() ? freeSpace(click.player(), item.get()) : 64;
                CompanyService.Pickup pickup = civ.companies().take(Players.caller(click.player()), quoted, id,
                        Math.max(1, wanted));
                long remaining = pickup.quantity();
                while (remaining > 0) {
                    int count = (int) Math.min(remaining, item.get().getDefaultMaxStackSize());
                    click.player().getInventory().placeItemBackInInventory(new ItemStack(item.get(), count),
                            Prediction.SERVER_ONLY);
                    remaining -= count;
                }
                act(click, pickup.reply());
            });
        }
    }

    private void renderEmployees(Canvas canvas, PolityView firm, String quoted) {
        List<ActorRef> employees = civ.companies().employees(firm);
        for (int index = 0; index < Math.min(EMPLOYEE_SLOTS, employees.size()); index++) {
            ActorRef employee = employees.get(index);
            canvas.set(FIRST_EMPLOYEE + index, Icon.of(employee instanceof de.etiennebader.civ.domain.actor.PersonRef
                            ? Items.EMERALD : Items.PLAYER_HEAD)
                            .name(Icon.literal(civ.texts().actor(employee), ChatFormatting.WHITE))
                            .hint(GuiKeys.COMPANY_EMPLOYEE_HINT.text()).build(),
                    click -> {
                        if (click.shift()) {
                            act(click, civ.companies().fire(Players.caller(click.player()), quoted, employee));
                        }
                    });
        }
        canvas.set(44, Icon.of(Items.WRITABLE_BOOK).name(GuiKeys.COMPANY_HIRE.text(), Icon.TITLE)
                        .hint(GuiKeys.COMPANY_HIRE_HINT.text()).build(),
                click -> prompt(click, GuiKeys.COMPANY_HIRE.text(), "/civ firma einstellen " + quoted + " "));
    }

    private void act(de.etiennebader.civ.fabric.gui.Click click, Reply reply) {
        Feedback.click(click.player());
        report(click, reply);
        Screens.open(click.player(), this);
    }

    private static int freeSpace(ServerPlayer player, Item item) {
        int free = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                free += item.getDefaultMaxStackSize();
            } else if (stack.is(item)) {
                free += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
        }
        return free;
    }

    private static Optional<Item> item(String id) {
        return Optional.ofNullable(Identifier.tryParse(id)).flatMap(BuiltInRegistries.ITEM::getOptional);
    }

    private static Item iconFor(Branch branch) {
        return switch (branch) {
            case FORESTRY -> Items.IRON_AXE;
            case SHEEP -> Items.SHEARS;
            case FARM -> Items.WHEAT;
            case MINE -> Items.IRON_PICKAXE;
            case FISHERY -> Items.FISHING_ROD;
            case RANCH -> Items.LEATHER;
            case BRICKWORKS -> Items.BRICK;
            case ORCHARD -> Items.APPLE;
            case CONSTRUCTION -> Items.STONE_BRICKS;
        };
    }
}
