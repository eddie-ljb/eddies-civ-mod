package de.etiennebader.civ.server.currency;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.Currency;
import de.etiennebader.civ.domain.economy.ExchangeRate;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Eigene Währungen aus Spielersicht: erstellen, Wechselkurs, Aussehen, Zentralbank, Umrechnung.
 *
 * <p>Kurse nennt man als Wert <em>einer</em> Einheit der Währung in Smaragden, z. B. {@code 2,5}: eine Einheit
 * Gold ist dann 2,5 Smaragde wert.
 */
public final class CurrencyService {

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final Texts texts;

    public CurrencyService(CivApplication app, Calendar calendar, PlayerDirectory players, Texts texts) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
    }

    /** Die Währung mit diesem Code; leer bei unbekanntem oder ungültigem Code. */
    public Optional<Currency> find(String code) {
        try {
            return queries.currencies().find(id(code));
        } catch (DomainException e) {
            return Optional.empty();
        }
    }

    /** Alle Währungscodes, z. B. für die Befehlsvervollständigung. */
    public List<String> currencyCodes() {
        return queries.currencies().all().stream().map(currency -> currency.id().code()).toList();
    }

    /** Alle Währungen, die am Bankschalter ausgezahlt werden können (mit Item). */
    public List<Currency> payoutCurrencies() {
        return queries.currencies().all().stream().filter(currency -> currency.itemId().isPresent()).toList();
    }

    /** {@code /civ waehrung erstellen <Code> <Symbol> <Kurs> <Name>} */
    public Reply create(Caller caller, String code, String symbol, String rateInput, String name) {
        return attempt(() -> {
            CurrencyId id = new CurrencyId(code.strip().toUpperCase(Locale.ROOT));
            ExchangeRate rate = parseRate(rateInput);
            Currency created = app.currencies().create(id, name, symbol, rate, caller.player(), null, calendar.today());
            return Reply.success(Message.of(MessageKey.CURRENCY_CREATED, created.name(), created.symbol(),
                    created.id().code(), rateInput.strip()));
        });
    }

    /** {@code /civ waehrung kurs <Code> <Kurs>} */
    public Reply changeRate(Caller caller, String code, String rateInput) {
        return attempt(() -> {
            Currency updated = app.currencies().changeRate(id(code), parseRate(rateInput), caller.player(),
                    calendar.today());
            return Reply.success(Message.of(MessageKey.CURRENCY_RATE_CHANGED, updated.name(), rateInput.strip()));
        });
    }

    /** {@code /civ waehrung leitzins <Code> <Prozent>} */
    public Reply setBaseRate(Caller caller, String code, int percent) {
        return attempt(() -> {
            Currency updated = app.currencies().setBaseRate(id(code), percent, caller.player(), calendar.today());
            return Reply.success(Message.of(MessageKey.CURRENCY_BASE_RATE_SET, updated.name(),
                    String.valueOf(updated.baseRatePercent())));
        });
    }

    /** {@code /civ waehrung vorsitz <Code> <Spieler>} */
    public Reply appointChair(Caller caller, String code, String playerName) {
        Optional<ActorRef> chair = players.findByName(playerName.strip()).map(ActorRef.class::cast);
        if (chair.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, playerName.strip()));
        }
        return attempt(() -> {
            Currency updated = app.currencies().appointChair(id(code), chair.get(), caller.player(), calendar.today());
            return Reply.success(Message.of(MessageKey.CURRENCY_CHAIR_APPOINTED, updated.name(),
                    texts.actor(chair.get())));
        });
    }

    /** {@code /civ waehrung item <Code> <Item-ID>}: das Aussehen der Währung am Bankschalter. */
    public Reply setItem(Caller caller, String code, String itemId) {
        return attempt(() -> {
            Currency updated = app.currencies().setItem(id(code), itemId.strip().toLowerCase(Locale.ROOT),
                    caller.player(), calendar.today());
            return Reply.success(Message.of(MessageKey.CURRENCY_ITEM_SET, updated.name(), updated.itemId().orElse("-")));
        });
    }

    /** {@code /civ waehrung liste} */
    public Reply list() {
        List<Message> lines = new java.util.ArrayList<>();
        List<Currency> all = queries.currencies().all();
        lines.add(Message.of(MessageKey.CURRENCY_LIST_HEADER, String.valueOf(all.size())));
        all.forEach(currency -> lines.add(line(currency)));
        return Reply.success(lines);
    }

    /** {@code /civ waehrung umrechnen <Betrag> <von> <nach>} */
    public Reply convert(String amountInput, String from, String to) {
        return attempt(() -> {
            Money source = Money.of(MoneyFormat.parse(amountInput), id(from));
            Money result = app.currencies().convert(source, id(to));
            return Reply.success(Message.of(MessageKey.CURRENCY_CONVERTED, texts.money(source), texts.money(result)));
        });
    }

    private Message line(Currency currency) {
        return Message.of(MessageKey.CURRENCY_ENTRY, currency.id().code(), currency.name(), currency.symbol(),
                emeraldValueOfOne(currency),
                currency.governor().map(texts::actor).orElse("-"), String.valueOf(currency.baseRatePercent()));
    }

    /** Wert einer Einheit der Währung in Smaragden, z. B. {@code 2,50}. */
    private String emeraldValueOfOne(Currency currency) {
        long units = queries.currencies().convert(Money.of(MoneyFormat.UNITS_PER_EMERALD, currency.id()),
                CurrencyId.EMERALD).amount();
        return MoneyFormat.whole(units) + "," + MoneyFormat.cents(units);
    }

    private static CurrencyId id(String code) {
        return new CurrencyId(code.strip().toUpperCase(Locale.ROOT));
    }

    /** Wert einer Einheit in Smaragd (Hundertstel) → Wechselkurs: 100 Einheiten der Währung = n Smaragd-Einheiten. */
    private static ExchangeRate parseRate(String input) {
        return new ExchangeRate(MoneyFormat.UNITS_PER_EMERALD, MoneyFormat.parse(input));
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        } catch (IllegalArgumentException e) {
            return Reply.failure(Message.of(MessageKey.INVALID_AMOUNT, String.valueOf(e.getMessage())));
        } catch (ArithmeticException e) {
            return Reply.failure(Message.of(MessageKey.INVALID_AMOUNT, "overflow"));
        }
    }
}
