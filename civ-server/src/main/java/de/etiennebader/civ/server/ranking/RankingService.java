package de.etiennebader.civ.server.ranking;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.AccountBalance;
import de.etiennebader.civ.domain.economy.ServerAccount;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.company.CompanyService;
import de.etiennebader.civ.server.company.ListingView;
import de.etiennebader.civ.server.federation.FederationService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Ranglisten: die Reichsten und Größten unter Spielern, Dorfbewohnern, Gemeinden, Firmen und Bünden (Ländern,
 * Nationen und eigenen Ebenen). Das {@link ServerAccount Serverkonto} gehört keiner dieser Gruppen und zählt nirgends mit.
 */
public final class RankingService {

    /** Die Ranglisten. {@code money}: Wert ist ein Geldbetrag, sonst eine Zahl (Einwohner bzw. Beschäftigte). */
    public enum Category {
        RICHEST_PLAYERS("spieler", true, MessageKey.RANKING_RICHEST_PLAYERS),
        RICHEST_VILLAGERS("dorfbewohner", true, MessageKey.RANKING_RICHEST_VILLAGERS),
        RICHEST_TOWNS("staedte", true, MessageKey.RANKING_RICHEST_TOWNS),
        RICHEST_COMPANIES("firmen", true, MessageKey.RANKING_RICHEST_COMPANIES),
        RICHEST_FEDERATIONS("buende", true, MessageKey.RANKING_RICHEST_FEDERATIONS),
        LARGEST_TOWNS("groesste-staedte", false, MessageKey.RANKING_LARGEST_TOWNS),
        LARGEST_COMPANIES("groesste-firmen", false, MessageKey.RANKING_LARGEST_COMPANIES),
        LARGEST_FEDERATIONS("groesste-buende", false, MessageKey.RANKING_LARGEST_FEDERATIONS);

        private final String id;
        private final boolean money;
        private final MessageKey title;

        Category(String id, boolean money, MessageKey title) {
            this.id = id;
            this.money = money;
            this.title = title;
        }

        public String id() {
            return id;
        }

        public boolean money() {
            return money;
        }

        public static Optional<Category> find(String input) {
            String wanted = input.strip().toLowerCase(Locale.ROOT);
            for (Category category : values()) {
                if (category.id.equals(wanted)) {
                    return Optional.of(category);
                }
            }
            return Optional.empty();
        }
    }

    /** Ein Eintrag; {@code levelId} ist bei Bünden die Ebene, sonst leer. */
    public record Entry(String name, String levelId, Object levelName, long value) {
    }

    public static final int DEFAULT_LIMIT = 10;

    private final CivApplication app;
    private final Texts texts;
    private final CurrencyId currency;
    private final CompanyService companies;
    private final FederationService federations;

    public RankingService(CivApplication app, Texts texts, CurrencyId currency, CompanyService companies,
                          FederationService federations) {
        this.app = Objects.requireNonNull(app, "app");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.companies = Objects.requireNonNull(companies, "companies");
        this.federations = Objects.requireNonNull(federations, "federations");
    }

    /** Kontostand des Servers (Gründungsgebühren); er steht in keiner Rangliste. */
    public Money serverBalance() {
        return app.queries().ledger().balanceOf(ServerAccount.REF, currency);
    }

    /**
     * Die besten Einträge einer Rangliste, absteigend nach Wert, bei Gleichstand nach Name.
     *
     * @param levelId nur bei den Bund-Ranglisten: nur diese Ebene (z. B. {@code land}); sonst leer
     */
    public List<Entry> top(Category category, Optional<String> levelId, int limit) {
        List<Entry> all = switch (category) {
            case RICHEST_PLAYERS -> balances(PlayerRef.class::isInstance);
            case RICHEST_VILLAGERS -> balances(PersonRef.class::isInstance);
            case RICHEST_TOWNS -> treasuries(PolityKind.TERRITORIAL);
            case RICHEST_COMPANIES -> treasuries(PolityKind.COMPANY);
            case RICHEST_FEDERATIONS -> treasuries(PolityKind.FEDERATION);
            case LARGEST_TOWNS -> sizes(PolityKind.TERRITORIAL);
            case LARGEST_FEDERATIONS -> sizes(PolityKind.FEDERATION);
            case LARGEST_COMPANIES -> employees();
        };
        return all.stream()
                .filter(entry -> levelId.isEmpty() || entry.levelId().equals(levelId.get()))
                .sorted(Comparator.comparingLong(Entry::value).reversed().thenComparing(Entry::name))
                .limit(Math.max(1, limit)).toList();
    }

    private List<Entry> balances(Predicate<ActorRef> kind) {
        List<Entry> entries = new ArrayList<>();
        for (AccountBalance account : app.queries().ledger().accountBalances()) {
            if (account.balance().currency().equals(currency) && kind.test(account.account())
                    && !ServerAccount.is(account.account())) {
                entries.add(new Entry(texts.actor(account.account()), "", "", account.balance().amount()));
            }
        }
        return entries;
    }

    private List<Entry> treasuries(PolityKind kind) {
        List<Entry> entries = new ArrayList<>();
        for (PolityView polity : app.queries().polities()) {
            if (polity.kind() == kind) {
                long balance = app.queries().ledger().balanceOf(new PolityRef(polity.id()), currency).amount();
                entries.add(entry(polity, balance));
            }
        }
        return entries;
    }

    private List<Entry> sizes(PolityKind kind) {
        List<Entry> entries = new ArrayList<>();
        for (PolityView polity : app.queries().polities()) {
            if (polity.kind() == kind) {
                entries.add(entry(polity, polity.memberCount()));
            }
        }
        return entries;
    }

    private List<Entry> employees() {
        Map<String, Integer> byId = new HashMap<>();
        companies.listings().forEach(listing -> byId.put(listing.id(), listing.employees()));
        List<Entry> entries = new ArrayList<>();
        for (PolityView polity : app.queries().polities()) {
            if (polity.kind() == PolityKind.COMPANY) {
                entries.add(entry(polity, byId.getOrDefault(polity.id().value().toString(), 0)));
            }
        }
        return entries;
    }

    private Entry entry(PolityView polity, long value) {
        if (polity.kind() != PolityKind.FEDERATION) {
            return new Entry(polity.name().value(), "", "", value);
        }
        var level = federations.levelOf(polity);
        return new Entry(polity.name().value(), level.id(), level.displayName(), value);
    }

    /** {@code /civ rangliste <Kategorie> [Ebene]} */
    public Reply show(String categoryInput, Optional<String> levelInput) {
        Optional<Category> category = Category.find(categoryInput);
        if (category.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.RANKING_UNKNOWN_CATEGORY, categoryInput.strip(),
                    String.join(", ", java.util.Arrays.stream(Category.values()).map(Category::id).toList())));
        }
        Optional<String> level = levelInput.filter(value -> !value.isBlank())
                .flatMap(value -> federations.levels().find(value)).map(found -> found.id());
        if (levelInput.filter(value -> !value.isBlank()).isPresent() && level.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_LEVEL, levelInput.get().strip(),
                    String.join(", ", federations.levels().federal().stream().map(found -> found.id()).toList())));
        }
        List<Entry> entries = top(category.get(), level, DEFAULT_LIMIT);
        if (entries.isEmpty()) {
            return Reply.success(Message.of(MessageKey.RANKING_EMPTY));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.RANKING_HEADER, Message.of(category.get().title)));
        int position = 1;
        for (Entry entry : entries) {
            Object value = category.get().money() ? texts.money(Money.of(entry.value(), currency)) : entry.value();
            String name = entry.name();
            lines.add(entry.levelId().isEmpty()
                    ? Message.of(MessageKey.RANKING_ENTRY, position, name, value)
                    : Message.of(MessageKey.RANKING_ENTRY_LEVEL, position, name, entry.levelName(), value));
            position++;
        }
        return Reply.success(lines);
    }
}
