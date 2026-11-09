package de.etiennebader.civ.server.shop;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.shop.BuyFromShop;
import de.etiennebader.civ.application.shop.CloseShop;
import de.etiennebader.civ.application.shop.OpenShop;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shop.Shop;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.market.ItemTraits;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Läden im Spiel.
 *
 * <ul>
 *     <li>Wer an einer Kiste bauen darf, macht sie mit {@code /civ laden eroeffnen <Preis>} zum Laden für das Item
 *         in seiner Hand.</li>
 *     <li>Andere Spieler kaufen per Rechtsklick auf die Kiste. Ist die Kiste leer, gibt es nichts zu kaufen.</li>
 *     <li>Im Gemeindegebiet geht die Umsatzsteuer der Gemeinde an deren Kasse.</li>
 * </ul>
 */
public final class ShopService {

    /** Ein Laden mit lesbarem Inhabernamen. */
    public record ShopView(Shop shop, String owner) {
    }

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final CurrencyId currency;
    private final Texts texts;

    public ShopService(CivApplication app, Calendar calendar, CurrencyId currency, Texts texts) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.texts = Objects.requireNonNull(texts, "texts");
    }

    public Optional<ShopView> shopAt(BlockPosition position) {
        return queries.shops().shopAt(position).map(shop -> new ShopView(shop, texts.actor(shop.owner())));
    }

    /** Eröffnet einen Laden an einer Kiste oder ändert den eigenen. */
    public Reply open(Caller caller, BlockPosition chest, ItemTraits item, String priceInput) {
        if (item.modified()) {
            return Reply.failure(Message.of(MessageKey.MARKET_MODIFIED_ITEM, Message.of(item.translationKey())));
        }
        long units;
        try {
            units = MoneyFormat.parse(priceInput);
        } catch (IllegalArgumentException e) {
            return Reply.failure(Message.of(MessageKey.INVALID_AMOUNT, priceInput.strip()));
        }
        if (queries.access(caller.player(), ProtectedAction.BUILD, chest).isPresent()) {
            return Reply.failure(Message.of(MessageKey.SHOP_NOT_HERE));
        }
        try {
            Money price = Money.of(units, currency);
            app.openShop().execute(new OpenShop.Command(chest, caller.player(), Commodity.of(item.id()), price,
                    calendar.today()));
            return Reply.success(Message.of(MessageKey.SHOP_OPENED, Message.of(item.translationKey()), texts.money(price)));
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    /** Der Inhaber schließt seinen Laden; die Kiste bleibt. */
    public Reply close(Caller caller, BlockPosition chest) {
        try {
            app.closeShop().execute(new CloseShop.Command(chest, Optional.of(caller.player()), calendar.today()));
            return Reply.success(Message.of(MessageKey.SHOP_CLOSED));
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    /** Entfernt einen Laden, dessen Kiste abgebaut wurde oder fehlt. */
    public void removeAt(BlockPosition chest) {
        if (queries.shops().shopAt(chest).isPresent()) {
            app.closeShop().execute(new CloseShop.Command(chest, Optional.empty(), calendar.today()));
        }
    }

    /**
     * Kauft im Laden.
     *
     * @param inStock wie viele passende Items gerade in der Kiste liegen
     * @return bei Erfolg muss der Aufrufer {@code quantity} Items aus der Kiste in das Inventar des Käufers legen
     */
    public Reply buy(Caller caller, BlockPosition chest, ItemTraits item, int quantity, int inStock) {
        Optional<Shop> shop = queries.shops().shopAt(chest);
        if (shop.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.SHOP_GONE));
        }
        if (shop.get().owner().equals(caller.player())) {
            return Reply.failure(Message.of(MessageKey.SHOP_OWN));
        }
        if (quantity > inStock) {
            return Reply.failure(Message.of(MessageKey.SHOP_OUT_OF_STOCK, inStock));
        }
        try {
            BuyFromShop.Receipt receipt = app.buyFromShop().execute(
                    new BuyFromShop.Command(chest, caller.player(), quantity, calendar.today()));
            List<Message> lines = new ArrayList<>();
            lines.add(Message.of(MessageKey.SHOP_BOUGHT, quantity, Message.of(item.translationKey()),
                    texts.money(receipt.total()), texts.actor(receipt.shop().owner())));
            if (receipt.tax().isPositive()) {
                lines.add(Message.of(MessageKey.SHOP_TAX_PAID, texts.money(receipt.tax())));
            }
            return Reply.success(lines);
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    /** {@code /civ laden liste}: die eigenen Läden. */
    public Reply list(Caller caller) {
        List<Shop> own = queries.shops().shopsOf(caller.player());
        if (own.isEmpty()) {
            return Reply.success(Message.of(MessageKey.SHOP_NONE));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.SHOP_LIST_HEADER, own.size()));
        for (Shop shop : own) {
            lines.add(Message.of(MessageKey.SHOP_LIST_ENTRY, shop.commodity().id(), texts.money(shop.price()),
                    shop.position().x(), shop.position().y(), shop.position().z()));
        }
        return Reply.success(lines);
    }

    /** Für Anzeigen: der Preis für eine Menge. */
    public Money priceFor(Shop shop, int quantity) {
        return Money.of(shop.price().amount() * quantity, shop.price().currency());
    }
}
