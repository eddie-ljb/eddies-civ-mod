package de.etiennebader.civ.server.company;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.company.CompanyFigures;
import de.etiennebader.civ.application.company.VillagerInvestors;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.exchange.Dividend;
import de.etiennebader.civ.domain.exchange.Holding;
import de.etiennebader.civ.domain.exchange.Listing;
import de.etiennebader.civ.domain.exchange.Order;
import de.etiennebader.civ.domain.exchange.Quote;
import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.domain.exchange.StockExchange;
import de.etiennebader.civ.domain.exchange.StockExchangeView;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.production.Branch;
import de.etiennebader.civ.domain.production.Enterprise;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Firmen und Börse.
 *
 * <ul>
 *   <li>{@code /civ firma gruenden <Kapital> <Name>}, {@code info [Name]}, {@code liste},
 *       {@code aufnehmen|entlassen <Firma> <Spieler>}, {@code einstellen <Firma> <Spieler> <Lohn>},
 *       {@code dividende <Firma> <Betrag>}, {@code ausgeben <Firma> <Anzahl> <Preis>}, {@code aufloesen <Firma>}</li>
 *   <li>{@code /civ boerse [liste]}, {@code kaufen|verkaufen <Firma> <Anzahl> [Limit]}, {@code depot},
 *       {@code buch <Firma>}, {@code streichen <Nr>}</li>
 * </ul>
 *
 * <p>Ohne Limit kauft man bis 10 % über dem letzten Kurs und verkauft bis 10 % darunter.
 */
public final class CompanyService {

    private static final int TOP_HOLDERS = 8;

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final CurrencyId currency;
    private final Texts texts;
    private final Announcements announcements;
    private final de.etiennebader.civ.server.conjuncture.ConjunctureService conjuncture;

    public CompanyService(CivApplication app, Calendar calendar, PlayerDirectory players, CurrencyId currency,
                          Texts texts, Announcements announcements,
                          de.etiennebader.civ.server.conjuncture.ConjunctureService conjuncture) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.conjuncture = Objects.requireNonNull(conjuncture, "conjuncture");
    }

    // ---- Firmen ----

    /** {@code /civ firma gruenden <Kapital> <Name>} */
    public Reply found(Caller caller, String capitalInput, String name) {
        return withAmount(capitalInput, capital -> attempt(() -> {
            PolityId id = app.companies().foundCompany(caller.player(), PolityName.of(name.strip()), capital,
                    calendar.today());
            Listing listing = queries.exchange().listing(id).orElseThrow();
            return Reply.success(Message.of(MessageKey.COMPANY_FOUNDED, name.strip(), texts.money(capital),
                    listing.totalShares(), money(listing.initialPrice())));
        }));
    }

    /** {@code /civ firma liste} */
    public Reply list() {
        List<ListingView> listings = listings();
        if (listings.isEmpty()) {
            return Reply.success(Message.of(MessageKey.COMPANIES_NONE));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.EXCHANGE_HEADER, listings.size(), calendar.today().value()));
        for (ListingView listing : listings) {
            lines.add(Message.of(MessageKey.EXCHANGE_ENTRY, listing.name(), listing.price(), percent(listing.change()),
                    listing.bookValue(), listing.marketCap()));
        }
        return Reply.success(lines);
    }

    /** {@code /civ firma info [Name]}: ohne Namen die erste Firma, die der Spieler leitet. */
    public Reply info(Caller caller, Optional<String> name) {
        Optional<PolityView> company = name.filter(value -> !value.isBlank()).map(this::company)
                .orElseGet(() -> led(caller.player()).stream().findFirst());
        if (company.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.COMPANY_UNKNOWN, name.orElse("")));
        }
        Optional<ListingView> view = listing(company.get());
        if (view.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.COMPANY_UNKNOWN, company.get().name().value()));
        }
        ListingView listing = view.get();
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.COMPANY_HEADER, listing.name(), listing.head()));
        lines.add(Message.of(MessageKey.COMPANY_FIGURES, listing.price(), percent(listing.change()), listing.shares(),
                listing.marketCap(), listing.treasury(), listing.bookValue()));
        lines.add(Message.of(MessageKey.COMPANY_STAFF, company.get().members().size(), listing.employees(),
                percent(listing.dividendYield())));
        for (ListingView.HolderLine holder : listing.holders()) {
            lines.add(Message.of(MessageKey.COMPANY_HOLDER, holder.name(), holder.shares(), percent(holder.percent())));
        }
        productionLines(company.get(), lines);
        return Reply.success(lines);
    }

    /** {@code /civ firma aufnehmen <Firma> <Spieler>}: in die Geschäftsleitung. */
    public Reply admit(Caller caller, String companyName, String playerName) {
        return withCompany(companyName, company -> withPlayer(playerName, member -> attempt(() -> {
            app.companies().admit(caller.player(), company.id(), PolityKind.COMPANY, member, calendar.today());
            return Reply.success(Message.of(MessageKey.ORGANISATION_ADMITTED, texts.actor(member),
                    company.name().value()));
        })));
    }

    /** {@code /civ firma entlassen <Firma> <Spieler>} */
    public Reply dismiss(Caller caller, String companyName, String playerName) {
        return withCompany(companyName, company -> withPlayer(playerName, member -> attempt(() ->
                app.companies().dismiss(caller.player(), company.id(), PolityKind.COMPANY, member, calendar.today())
                        ? Reply.success(Message.of(MessageKey.ORGANISATION_DISMISSED, texts.actor(member),
                        company.name().value()))
                        : Reply.failure(Message.of(MessageKey.ORGANISATION_NOT_MEMBER, texts.actor(member))))));
    }

    /** {@code /civ firma einstellen <Firma> <Spieler|Dorfbewohner> <Lohn>}: Arbeitsvertrag mit der Firma. */
    public Reply hire(Caller caller, String companyName, ActorRef employee, String wageInput) {
        return withCompany(companyName, company -> withAmount(wageInput, wage -> attempt(() -> {
            app.companies().requireHead(caller.player(), company.id(), PolityKind.COMPANY);
            Contract offered = app.contracts().offerEmployment(company.asActor(), employee, wage, 0,
                    calendar.today());
            if (employee instanceof PlayerRef player) {
                announcements.add(Announcement.toPlayer(player, Message.of(MessageKey.CONTRACT_OFFERED_TO_YOU,
                        company.name().value(), offered.number(), offered.title())));
            }
            return Reply.success(Message.of(employee instanceof PersonRef ? MessageKey.COMPANY_VILLAGER_HIRED
                    : MessageKey.CONTRACT_OFFERED, offered.number(), offered.title(), texts.actor(employee)));
        })));
    }

    public Reply hire(Caller caller, String companyName, String employeeName, String wageInput) {
        return withPlayer(employeeName, employee -> hire(caller, companyName, employee, wageInput));
    }

    /** {@code /civ firma dividende <Firma> <Betrag>} */
    public Reply dividend(Caller caller, String companyName, String amountInput) {
        return withCompany(companyName, company -> withAmount(amountInput, total -> attempt(() -> {
            Dividend dividend = app.companies().payDividend(caller.player(), company.id(), total, calendar.today());
            Message message = Message.of(MessageKey.DIVIDEND_PAID, company.name().value(), money(dividend.perShare()),
                    money(dividend.total()));
            queries.exchange().holders(company.id()).keySet().stream().filter(PlayerRef.class::isInstance)
                    .map(PlayerRef.class::cast).filter(holder -> !holder.equals(caller.player()))
                    .forEach(holder -> announcements.add(Announcement.toPlayer(holder, message)));
            return Reply.success(message);
        })));
    }

    /** {@code /civ firma ausgeben <Firma> <Anzahl> <Preis>}: Kapitalerhöhung. */
    public Reply issue(Caller caller, String companyName, long shares, String priceInput) {
        return withCompany(companyName, company -> withAmount(priceInput, price -> attempt(() -> {
            StockExchange.Placement placement = app.companies().issue(caller.player(), company.id(), shares,
                    price.amount(), calendar.today());
            return Reply.success(Message.of(MessageKey.SHARES_ISSUED, shares, company.name().value(),
                    texts.money(price), placement.filled()));
        })));
    }

    /** {@code /civ firma aufloesen <Firma>} */
    public Reply dissolve(Caller caller, String companyName) {
        return withCompany(companyName, company -> attempt(() -> {
            app.companies().dissolve(caller.player(), company.id(), calendar.today());
            return Reply.success(Message.of(MessageKey.ORGANISATION_DISSOLVED, company.name().value()));
        }));
    }

    /** Firmen, die ein Spieler leitet. */
    public List<PolityView> led(ActorRef player) {
        return queries.companies().stream().filter(company -> company.holds(player, Office.MAYOR)).toList();
    }

    // ---- Börse ----

    /** {@code /civ boerse kaufen|verkaufen <Firma> <Anzahl> [Limit]} */
    public Reply trade(Caller caller, Side side, String companyName, long quantity, Optional<String> limitInput) {
        return trade(caller.player(), side, companyName, quantity, limitInput);
    }

    public Reply trade(ActorRef trader, Side side, String companyName, long quantity, Optional<String> limitInput) {
        return withCompany(companyName, company -> {
            long last = queries.exchange().price(company.id());
            Function<Long, Reply> place = limit -> attempt(() -> {
                StockExchange.Placement placement = app.trading().place(trader, company.id(), side, quantity, limit,
                        calendar.today());
                notifyCounterparties(placement, trader, company);
                MessageKey key = placement.stoppedForFunds() ? MessageKey.ORDER_STOPPED
                        : placement.resting() ? MessageKey.ORDER_RESTING : MessageKey.ORDER_FILLED;
                long average = placement.filled() == 0 ? 0 : placement.trades().stream()
                        .mapToLong(trade -> trade.value()).sum() / placement.filled();
                return Reply.success(Message.of(key, placement.order().number(),
                        Message.of(side == Side.BUY ? MessageKey.SIDE_BUY : MessageKey.SIDE_SELL), quantity,
                        company.name().value(), placement.filled(), money(average), money(limit)));
            });
            if (limitInput.isPresent() && !limitInput.get().isBlank()) {
                return withAmount(limitInput.get(), limit -> place.apply(limit.amount()));
            }
            return place.apply(Math.max(1, side == Side.BUY ? last * 110 / 100 + 1 : last * 90 / 100));
        });
    }

    private void notifyCounterparties(StockExchange.Placement placement, ActorRef trader, PolityView company) {
        for (var trade : placement.trades()) {
            ActorRef other = trade.buyer().equals(trader) ? trade.seller() : trade.buyer();
            if (other instanceof PlayerRef player) {
                announcements.add(Announcement.toPlayer(player, Message.of(MessageKey.ORDER_EXECUTED_FOR_YOU,
                        trade.quantity(), company.name().value(), money(trade.price()))));
            }
        }
    }

    /** {@code /civ boerse streichen <Nr>} */
    public Reply cancel(Caller caller, int number) {
        return cancel(caller.player(), number);
    }

    public Reply cancel(ActorRef trader, int number) {
        return attempt(() -> {
            Order order = app.trading().cancel(trader, number, calendar.today());
            return Reply.success(Message.of(MessageKey.ORDER_CANCELLED, order.number()));
        });
    }

    /** {@code /civ boerse depot} */
    public Reply depot(Caller caller) {
        List<HoldingView> holdings = holdingsOf(caller.player());
        List<OrderView> orders = ordersOf(caller.player());
        if (holdings.isEmpty() && orders.isEmpty()) {
            return Reply.success(Message.of(MessageKey.DEPOT_EMPTY));
        }
        List<Message> lines = new ArrayList<>();
        long total = holdings.stream().mapToLong(HoldingView::valueUnits).sum();
        lines.add(Message.of(MessageKey.DEPOT_HEADER, holdings.size(), money(total)));
        holdings.forEach(holding -> lines.add(Message.of(MessageKey.DEPOT_ENTRY, holding.company(), holding.shares(),
                holding.price(), holding.value())));
        orders.forEach(order -> lines.add(Message.of(MessageKey.ORDER_ENTRY, order.number(),
                Message.of(order.side().equals("BUY") ? MessageKey.SIDE_BUY : MessageKey.SIDE_SELL),
                order.remaining(), order.company(), order.limit())));
        return Reply.success(lines);
    }

    /** {@code /civ boerse buch <Firma>}: die besten fünf Kauf- und Verkaufsorders. */
    public Reply book(String companyName) {
        return withCompany(companyName, company -> {
            List<Message> lines = new ArrayList<>();
            lines.add(Message.of(MessageKey.BOOK_HEADER, company.name().value(),
                    money(queries.exchange().price(company.id()))));
            List<Order> orders = queries.exchange().orders(company.id());
            orders.stream().filter(order -> order.side() == Side.SELL).sorted(Comparator.comparingLong(Order::limit))
                    .limit(5).forEach(order -> lines.add(Message.of(MessageKey.BOOK_ASK, order.remaining(),
                            money(order.limit()))));
            orders.stream().filter(order -> order.side() == Side.BUY)
                    .sorted(Comparator.comparingLong(Order::limit).reversed())
                    .limit(5).forEach(order -> lines.add(Message.of(MessageKey.BOOK_BID, order.remaining(),
                            money(order.limit()))));
            if (orders.isEmpty()) {
                lines.add(Message.of(MessageKey.BOOK_EMPTY));
            }
            return Reply.success(lines);
        });
    }

    /** Tagesende: verfallene Orders, Kurse, Dorfbewohner legen an. */
    public VillagerInvestors.Report endOfDay(Day day) {
        for (Order expired : app.trading().endOfDay(day)) {
            if (expired.trader() instanceof PlayerRef player) {
                announcements.add(Announcement.toPlayer(player, Message.of(MessageKey.ORDER_EXPIRED,
                        expired.number())));
            }
        }
        return app.villagerInvestors().execute(day);
    }


    // ---- Produktion ----

    private Supplier<de.etiennebader.civ.domain.market.PriceLevel> priceLevel =
            () -> de.etiennebader.civ.domain.market.PriceLevel.NEUTRAL;

    /** Woher das Preisniveau für Verkäufe aus dem Lager kommt (vom Markt). */
    public void pricing(Supplier<de.etiennebader.civ.domain.market.PriceLevel> level) {
        this.priceLevel = Objects.requireNonNull(level, "level");
    }

    /** Grundpreis einer Ware für den Servermarkt. */
    public static java.util.OptionalLong basePrice(Commodity commodity) {
        String path = commodity.id().substring(commodity.id().indexOf(':') + 1);
        return de.etiennebader.civ.server.market.BasePrices.of(new de.etiennebader.civ.server.market.ItemTraits(
                commodity.id(), itemKey(commodity), de.etiennebader.civ.server.market.ItemTraits.Rarity.COMMON, 64,
                isBlock(path), false));
    }

    private static boolean isBlock(String path) {
        return path.endsWith("_log") || path.endsWith("_wool") || path.endsWith("_sapling")
                || java.util.Set.of("cobblestone", "bricks", "glass", "stone").contains(path);
    }

    /** Übersetzungsschlüssel einer Ware, z. B. {@code block.minecraft.oak_log}. */
    public static String itemKey(Commodity commodity) {
        int colon = commodity.id().indexOf(':');
        String namespace = commodity.id().substring(0, colon);
        String path = commodity.id().substring(colon + 1);
        return (isBlock(path) ? "block." : "item.") + namespace + "." + path;
    }

    public static Message branchName(Branch branch) {
        return Message.of("eddies-civ-mod.branch." + branch.name().toLowerCase(Locale.ROOT));
    }

    /** {@code /civ firma branche <Firma> <Branche>} */
    public Reply chooseBranch(Caller caller, String companyName, String branchWord) {
        Optional<Branch> branch = Branch.of(branchWord);
        if (branch.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PRODUCTION_BRANCH_UNKNOWN, branchWord.strip(),
                    String.join(", ", Branch.ids())));
        }
        return withCompany(companyName, company -> attemptState(() -> {
            app.production().chooseBranch(caller.player(), company.id(), branch.get(), calendar.today());
            return Reply.success(Message.of(MessageKey.PRODUCTION_BRANCH_SET, company.name().value(),
                    branchName(branch.get())));
        }));
    }

    /** {@code /civ firma investieren <Firma> <Betrag>} */
    public Reply invest(Caller caller, String companyName, String amountInput) {
        return withCompany(companyName, company -> withAmount(amountInput, amount -> attemptState(() -> {
            Enterprise enterprise = app.production().invest(caller.player(), company.id(), amount, calendar.today());
            return Reply.success(Message.of(MessageKey.PRODUCTION_INVESTED, money(amount.amount()),
                    company.name().value(), enterprise.level(), enterprise.level() * 25L,
                    money(enterprise.toNextLevel())));
        })));
    }

    /** {@code /civ firma verkauf <Firma> automatisch|hand|jetzt} */
    public Reply sell(Caller caller, String companyName, String mode) {
        return withCompany(companyName, company -> attemptState(() -> {
            switch (mode.strip().toLowerCase(Locale.ROOT)) {
                case "automatisch", "auto", "an", "on" -> {
                    app.production().autoSell(caller.player(), company.id(), true, calendar.today());
                    return Reply.success(Message.of(MessageKey.PRODUCTION_AUTOSELL_ON, company.name().value()));
                }
                case "hand", "manuell", "aus", "off", "manual" -> {
                    app.production().autoSell(caller.player(), company.id(), false, calendar.today());
                    return Reply.success(Message.of(MessageKey.PRODUCTION_AUTOSELL_OFF, company.name().value()));
                }
                default -> {
                    var sale = app.production().sellStock(caller.player(), company.id(), CompanyService::basePrice,
                            priceLevel.get(), calendar.today());
                    if (sale.sold().isEmpty()) {
                        return Reply.failure(Message.of(MessageKey.PRODUCTION_NOTHING_SOLD));
                    }
                    return Reply.success(Message.of(MessageKey.PRODUCTION_SOLD, company.name().value(),
                            money(sale.revenue().amount())));
                }
            }
        }));
    }

    /** {@code /civ firma lager <Firma>} */
    public Reply stock(Caller caller, String companyName) {
        return withCompany(companyName, company -> {
            Optional<Enterprise> enterprise = queries.production().enterpriseOf(company.id());
            if (enterprise.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.PRODUCTION_NONE, company.name().value(),
                        String.join("|", Branch.ids())));
            }
            List<Message> lines = new ArrayList<>();
            lines.add(Message.of(MessageKey.PRODUCTION_STOCK_HEADER, company.name().value()));
            if (enterprise.get().stock().isEmpty()) {
                lines.add(Message.of(MessageKey.PRODUCTION_STOCK_EMPTY));
            }
            enterprise.get().stock().forEach((commodity, quantity) -> lines.add(Message.of(
                    MessageKey.PRODUCTION_STOCK_LINE, quantity, Message.of(itemKey(commodity)), commodity.id())));
            return Reply.success(lines);
        });
    }

    /** Ergebnis von {@code /civ firma abholen}: was dem Spieler ins Inventar gelegt werden soll. */
    public record Pickup(Reply reply, String item, long quantity) {
    }

    /** {@code /civ firma abholen <Firma> <Ware> [Anzahl]}; legt nichts ins Inventar, das macht der Aufrufer. */
    public Pickup take(Caller caller, String companyName, String item, long quantity) {
        Optional<PolityView> company = company(companyName);
        if (company.isEmpty()) {
            return new Pickup(Reply.failure(Message.of(MessageKey.COMPANY_UNKNOWN, companyName.strip())), "", 0);
        }
        String id = item.contains(":") ? item.strip() : "minecraft:" + item.strip();
        try {
            Commodity commodity = Commodity.of(id);
            long taken = app.production().take(caller.player(), company.get().id(), commodity, quantity,
                    calendar.today());
            return new Pickup(Reply.success(Message.of(MessageKey.PRODUCTION_TAKEN, taken,
                    Message.of(itemKey(commodity)))), id, taken);
        } catch (DomainException e) {
            return new Pickup(Reply.failure(texts.error(e)), "", 0);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return new Pickup(Reply.failure(Message.of(MessageKey.CIVIC_REFUSED, e.getMessage())), "", 0);
        }
    }

    /** Waren im Lager einer Firma, für Vorschläge. */
    public List<String> stockItems(String companyName) {
        return company(companyName).flatMap(company -> queries.production().enterpriseOf(company.id()))
                .map(enterprise -> enterprise.stock().keySet().stream().map(Commodity::id)
                        .map(id -> id.startsWith("minecraft:") ? id.substring(10) : id).toList())
                .orElse(List.of());
    }

    /** {@code /civ firma kuendigen <Firma> <Spieler|dorfbewohner>}: kündigt den Arbeitsvertrag. */
    public Reply fire(Caller caller, String companyName, ActorRef employee) {
        return withCompany(companyName, company -> attemptState(() -> {
            app.companies().requireHead(caller.player(), company.id(), PolityKind.COMPANY);
            Optional<Contract> contract = queries.contracts().active().stream()
                    .filter(found -> found.kind() == ContractKind.EMPLOYMENT
                            && found.offeror().equals(company.asActor()) && found.counterparty().equals(employee))
                    .findFirst();
            if (contract.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.COMPANY_NOT_EMPLOYED, texts.actor(employee),
                        company.name().value()));
            }
            Contract noticed = app.contracts().giveNotice(contract.get().id(), caller.player(), calendar.today());
            if (employee instanceof PlayerRef player) {
                announcements.add(Announcement.toPlayer(player, Message.of(MessageKey.COMPANY_FIRED_YOU,
                        company.name().value(), noticed.number())));
            }
            return Reply.success(Message.of(MessageKey.COMPANY_FIRED, noticed.number(), texts.actor(employee),
                    de.etiennebader.civ.application.civil.ContractUseCases.NOTICE_DAYS));
        }));
    }

    public Reply fire(Caller caller, String companyName, String employeeName) {
        return withPlayer(employeeName, employee -> fire(caller, companyName, employee));
    }

    /** Angestellte einer Firma mit geltendem Arbeitsvertrag. */
    public List<ActorRef> employees(PolityView company) {
        return app.production().employees(company.id());
    }

    /** Zeilen für {@code /civ firma info}. */
    private void productionLines(PolityView company, List<Message> lines) {
        Optional<Enterprise> enterprise = queries.production().enterpriseOf(company.id());
        if (enterprise.isEmpty()) {
            lines.add(Message.of(MessageKey.PRODUCTION_NONE, company.name().value(), String.join("|", Branch.ids())));
            return;
        }
        Enterprise found = enterprise.get();
        double workforce = app.production().workforce(company.id());
        lines.add(Message.of(MessageKey.PRODUCTION_INFO, branchName(found.branch()), found.level(), found.level() * 25L,
                String.format(Locale.ROOT, "%.1f", workforce),
                Message.of(found.autoSell() ? MessageKey.PRODUCTION_SELL_AUTO : MessageKey.PRODUCTION_SELL_MANUAL)));
        found.dailyOutput(workforce).forEach((commodity, quantity) -> lines.add(Message.of(
                MessageKey.PRODUCTION_FORECAST_LINE, quantity, Message.of(itemKey(commodity)))));
        if (found.lastDay().isPresent()) {
            lines.add(Message.of(MessageKey.PRODUCTION_LAST, found.lastOutput(), money(found.lastRevenue())));
        }
    }

    /** Ansicht des Betriebs für Menü und Webseite. */
    public record EnterpriseView(String branch, int level, long toNextLevel, double workforce, boolean autoSell,
                                 Map<String, Long> stock, Map<String, Long> forecast, long lastOutput,
                                 long lastRevenue, List<String> employees) {
    }

    public Optional<EnterpriseView> enterprise(PolityView company) {
        return queries.production().enterpriseOf(company.id()).map(found -> {
            double workforce = app.production().workforce(company.id());
            Map<String, Long> stock = new java.util.LinkedHashMap<>();
            found.stock().forEach((commodity, quantity) -> stock.put(commodity.id(), quantity));
            Map<String, Long> forecast = new java.util.LinkedHashMap<>();
            found.dailyOutput(workforce).forEach((commodity, quantity) -> forecast.put(commodity.id(), quantity));
            List<String> employees = app.production().employees(company.id()).stream().map(texts::actor).toList();
            return new EnterpriseView(found.branch().name(), found.level(), found.toNextLevel(), workforce,
                    found.autoSell(), stock, forecast, found.lastOutput(), found.lastRevenue(), employees);
        });
    }

    /** Tagesende der Produktion: erzeugen, verkaufen, der Leitung melden. */
    public void produce(Day day) {
        // Firmen sind nicht geografisch verankert (keine Gemeinde, in der sie sitzen), deshalb wirkt die Konjunktur
        // hier mit dem Durchschnitt aller Wirtschaftszonen statt einer einzelnen; siehe ConjunctureService.
        double conjunctureFactor = conjuncture.globalAverageMultiplier(day);
        de.etiennebader.civ.domain.market.PriceLevel adjusted = new de.etiennebader.civ.domain.market.PriceLevel(
                Math.clamp(priceLevel.get().factor() * conjunctureFactor, de.etiennebader.civ.domain.market.PriceLevel.MIN,
                        de.etiennebader.civ.domain.market.PriceLevel.MAX));
        for (var result : app.production().endOfDay(CompanyService::basePrice, adjusted, day)) {
            long pieces = result.produced().values().stream().mapToLong(Long::longValue).sum();
            if (pieces == 0) {
                continue;
            }
            queries.polity(result.company()).ifPresent(company -> company.mayor()
                    .filter(PlayerRef.class::isInstance).map(PlayerRef.class::cast)
                    .ifPresent(head -> announcements.add(Announcement.toPlayer(head, result.revenue().amount() > 0
                            ? Message.of(MessageKey.PRODUCTION_DAY_SOLD, company.name().value(), pieces,
                            money(result.revenue().amount()))
                            : Message.of(MessageKey.PRODUCTION_DAY, company.name().value(), pieces)))));
        }
    }

    private Reply attemptState(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Reply.failure(Message.of(MessageKey.CIVIC_REFUSED, e.getMessage()));
        }
    }

    // ---- Ansichten ----

    public List<ListingView> listings() {
        List<ListingView> views = new ArrayList<>();
        for (Listing listing : queries.exchange().listings()) {
            queries.polity(listing.company()).flatMap(this::listing).ifPresent(views::add);
        }
        views.sort(Comparator.comparingLong(ListingView::units).reversed());
        return views;
    }

    public Optional<ListingView> listing(PolityView company) {
        StockExchangeView exchange = queries.exchange();
        Optional<Listing> listing = exchange.listing(company.id());
        if (listing.isEmpty()) {
            return Optional.empty();
        }
        Day today = calendar.today();
        long treasury = queries.ledger().balanceOf(company.asActor(), company.currency()).amount();
        CompanyFigures figures = CompanyFigures.of(company, listing.get(), exchange, treasury, today);
        List<ListingView.QuotePoint> quotes = exchange.quotes(company.id()).stream()
                .map(quote -> new ListingView.QuotePoint(quote.day().value(), quote.open(), quote.high(), quote.low(),
                        quote.close(), quote.volume()))
                .toList();
        long volume = exchange.quotes(company.id()).stream().filter(quote -> quote.day().equals(today))
                .mapToLong(Quote::volume).sum();
        List<ListingView.HolderLine> holders = new ArrayList<>();
        for (Map.Entry<ActorRef, Long> holder : exchange.holders(company.id()).entrySet()) {
            if (holders.size() >= TOP_HOLDERS) {
                break;
            }
            holders.add(new ListingView.HolderLine(texts.actor(holder.getKey()), holder.getValue(),
                    holder.getValue() * 100.0 / listing.get().totalShares()));
        }
        int employees = (int) queries.contracts().active().stream()
                .filter(contract -> contract.kind() == ContractKind.EMPLOYMENT
                        && contract.offeror().equals(company.asActor()))
                .count();
        long freeFloat = listing.get().totalShares() - company.mayor().map(head -> exchange.sharesOf(company.id(), head))
                .orElse(0L) - exchange.sharesOf(company.id(), company.asActor());
        return Optional.of(new ListingView(company.id().value().toString(), company.name().value(),
                company.mayor().map(texts::actor).orElse("–"), money(figures.price()), figures.price(),
                round(figures.changePercent()), money(figures.bookValue()), money(figures.marketCap()),
                money(treasury), listing.get().totalShares(), round(figures.dividendYield()), volume, quotes, holders,
                employees, freeFloat));
    }

    public List<HoldingView> holdingsOf(ActorRef holder) {
        StockExchangeView exchange = queries.exchange();
        List<HoldingView> views = new ArrayList<>();
        for (Holding holding : exchange.holdingsOf(holder)) {
            long price = exchange.price(holding.company());
            String name = queries.polity(holding.company()).map(polity -> polity.name().value()).orElse("?");
            views.add(new HoldingView(holding.company().value().toString(), name, holding.shares(),
                    exchange.freeShares(holding.company(), holder), money(price), money(price * holding.shares()),
                    price * holding.shares()));
        }
        views.sort(Comparator.comparingLong(HoldingView::valueUnits).reversed());
        return views;
    }

    public List<OrderView> ordersOf(ActorRef trader) {
        return queries.exchange().ordersOf(trader).stream().map(order -> view(order, trader)).toList();
    }

    /** Orderbuch einer Firma für die Webseite. */
    public List<OrderView> book(PolityId company, ActorRef viewer) {
        return queries.exchange().orders(company).stream().map(order -> view(order, viewer)).toList();
    }

    private OrderView view(Order order, ActorRef viewer) {
        return new OrderView(order.number(), queries.polity(order.company()).map(p -> p.name().value()).orElse("?"),
                texts.actor(order.trader()), order.side().name(), order.quantity(), order.remaining(), money(order.limit()), order.limit(),
                order.placedOn().value(), order.trader().equals(viewer));
    }

    public Optional<PolityView> company(String name) {
        return queries.polityNamed(name.strip()).filter(polity -> polity.kind() == PolityKind.COMPANY);
    }

    // ---- Hilfen ----

    public static String money(long units) {
        String sign = units < 0 ? "-" : "";
        long abs = Math.abs(units);
        return sign + MoneyFormat.whole(abs) + "," + MoneyFormat.cents(abs);
    }

    private static String percent(double value) {
        return String.format(Locale.GERMANY, "%+.1f", value);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private Reply withCompany(String name, Function<PolityView, Reply> action) {
        return company(name).map(action)
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.COMPANY_UNKNOWN, name.strip())));
    }

    private Reply withPlayer(String name, Function<ActorRef, Reply> action) {
        return players.findByName(name.strip()).<ActorRef>map(player -> player).map(action)
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, name.strip())));
    }

    private Reply withAmount(String input, Function<Money, Reply> action) {
        try {
            return action.apply(Money.of(MoneyFormat.parse(input), currency));
        } catch (IllegalArgumentException e) {
            return Reply.failure(Message.of(MessageKey.INVALID_AMOUNT, input.strip()));
        }
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}
