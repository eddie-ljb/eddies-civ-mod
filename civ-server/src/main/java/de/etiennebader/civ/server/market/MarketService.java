package de.etiennebader.civ.server.market;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.market.BuyFromMarket;
import de.etiennebader.civ.application.market.SellToMarket;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.market.PriceLevel;
import de.etiennebader.civ.domain.market.Quote;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * Der Servermarkt aus Spielersicht. Items bewegt der Aufrufer im Spiel: vor {@link #sell} nimmt er
 * sie dem Spieler ab (und gibt sie bei einer Fehlerantwort zurück), nach erfolgreichem {@link #buy}
 * gibt er sie ihm.
 */
public final class MarketService {

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final ServerSettings settings;
    private final PlayerDirectory players;
    private final Texts texts;

    public MarketService(CivApplication app, Calendar calendar, ServerSettings settings, PlayerDirectory players,
                         Texts texts) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
    }

    /** Aktuelle Preise für ein Stück; leer, wenn das Item nicht gehandelt wird. */
    public Optional<Quote> quote(ItemTraits item) {
        OptionalLong base = BasePrices.of(item);
        if (base.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(queries.market().quote(Commodity.of(item.id()), base.getAsLong(), priceLevel(), calendar.today()));
    }

    /** Das aktuelle Preisniveau aus Geldmenge und Zahl der bekannten Spieler. */
    public PriceLevel priceLevel() {
        return queries.priceLevel(Math.max(1, players.all().size()), settings.priceReference());
    }

    /** {@code /civ preis}: Preise des Items in der Hand. */
    public Reply priceInfo(ItemTraits item) {
        Optional<Quote> quote = quote(item);
        if (quote.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.MARKET_NOT_TRADABLE, Message.of(item.translationKey())));
        }
        Message buy = quote.get().available()
                ? texts.money(quote.get().buyPrice())
                : Message.of(MessageKey.MARKET_SOLD_OUT);
        return Reply.success(
                Message.of(MessageKey.MARKET_PRICE, Message.of(item.translationKey()), texts.money(quote.get().sellPrice()),
                        buy),
                Message.of(MessageKey.MARKET_STOCK, quote.get().stock(), priceLevelPercent()));
    }

    /** Verkauft Items, die der Aufrufer dem Spieler bereits abgenommen hat. */
    public Reply sell(Caller caller, ItemTraits item, int quantity) {
        if (item.modified()) {
            return Reply.failure(Message.of(MessageKey.MARKET_MODIFIED_ITEM, Message.of(item.translationKey())));
        }
        OptionalLong base = BasePrices.of(item);
        if (base.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.MARKET_NOT_TRADABLE, Message.of(item.translationKey())));
        }
        return attempt(() -> {
            Money proceeds = app.sellToMarket().execute(new SellToMarket.Command(caller.player(), Commodity.of(item.id()),
                    quantity, base.getAsLong(), priceLevel(), calendar.today()));
            return Reply.success(Message.of(MessageKey.MARKET_SOLD, quantity, Message.of(item.translationKey()),
                    texts.money(proceeds)));
        });
    }

    /** Kauft Items aus dem Lager; bei Erfolg muss der Aufrufer sie dem Spieler geben. */
    public Reply buy(Caller caller, ItemTraits item, int quantity) {
        OptionalLong base = BasePrices.of(item);
        if (base.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.MARKET_NOT_TRADABLE, Message.of(item.translationKey())));
        }
        return attempt(() -> {
            Money cost = app.buyFromMarket().execute(new BuyFromMarket.Command(caller.player(), Commodity.of(item.id()),
                    quantity, base.getAsLong(), priceLevel(), calendar.today()));
            return Reply.success(Message.of(MessageKey.MARKET_BOUGHT, quantity, Message.of(item.translationKey()),
                    texts.money(cost)));
        });
    }

    /** Waren auf Lager, meiste zuerst. */
    public List<MarketOffer> offers(int limit) {
        return queries.market().stocks().stream()
                .map(stock -> new MarketOffer(stock.commodity(), queries.market().stockOf(stock.commodity(), calendar.today())))
                .filter(offer -> offer.stock() > 0)
                .sorted(Comparator.comparingLong(MarketOffer::stock).reversed().thenComparing(MarketOffer::commodity,
                        Comparator.naturalOrder()))
                .limit(limit)
                .toList();
    }

    /** Preisniveau in Prozent des Grundpreises, z. B. {@code 120}. */
    public long priceLevelPercent() {
        return Math.round(priceLevel().factor() * 100);
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}
