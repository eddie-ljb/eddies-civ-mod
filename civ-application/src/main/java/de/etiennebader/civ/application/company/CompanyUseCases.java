package de.etiennebader.civ.application.company;

import de.etiennebader.civ.application.office.NotOfficeHolderException;
import de.etiennebader.civ.application.polity.DissolvePolity;
import de.etiennebader.civ.application.polity.PolityNameTakenException;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.economy.InsufficientFundsException;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.exchange.Dividend;
import de.etiennebader.civ.domain.exchange.InvalidOrderException;
import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.domain.exchange.StockExchange;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Firmen und Zeitungen gründen, führen und auflösen; Aktien ausgeben und Dividenden zahlen.
 *
 * <p>Eine Firma startet mit Kapital vom Gründer und {@link #SHARES_AT_FOUNDING} Aktien, die alle dem Gründer gehören;
 * der Ausgabepreis ist Kapital durch Aktienzahl. Wer die Leitung hat ({@link Office#MAYOR}), handelt für die Firma.
 */
public final class CompanyUseCases {

    public static final long SHARES_AT_FOUNDING = 1_000;
    /** Mindestkapital: 10 Smaragde. */
    public static final long MIN_CAPITAL = 1_000;

    private final Aggregates aggregates;
    private final IdGenerator ids;
    private final Trading trading;
    private final DissolvePolity dissolvePolity;

    public CompanyUseCases(Aggregates aggregates, IdGenerator ids, Trading trading, DissolvePolity dissolvePolity) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.trading = Objects.requireNonNull(trading, "trading");
        this.dissolvePolity = Objects.requireNonNull(dissolvePolity, "dissolvePolity");
    }

    /**
     * Gründet eine Firma und bringt sie an die Börse.
     *
     * @throws PolityNameTakenException   wenn es den Namen schon gibt
     * @throws InsufficientFundsException wenn der Gründer das Kapital nicht hat
     * @throws InvalidOrderException      wenn das Kapital unter dem Mindestkapital liegt
     */
    public PolityId foundCompany(ActorRef founder, PolityName name, Money capital, Day day) {
        if (capital.amount() < MIN_CAPITAL) {
            throw new InvalidOrderException(MIN_CAPITAL);
        }
        PolityId id = found(founder, name, PolityKind.COMPANY, capital.currency(), day);
        Ledger ledger = aggregates.ledger();
        try {
            ledger.transfer(founder, new PolityRef(id), capital, TransferReason.CAPITAL, day);
        } catch (InsufficientFundsException e) {
            aggregates.repositories().polities().remove(id);
            aggregates.repositories().land().remove(id);
            throw e;
        }
        aggregates.commit(ledger);
        StockExchange exchange = aggregates.exchange();
        exchange.list(id, founder, SHARES_AT_FOUNDING, Math.max(1, capital.amount() / SHARES_AT_FOUNDING), day);
        aggregates.commit(exchange);
        return id;
    }

    /**
     * Gründet eine Firma oder Zeitung ohne Börsengang.
     *
     * @throws PolityNameTakenException wenn es den Namen schon gibt
     */
    public PolityId found(ActorRef founder, PolityName name, PolityKind kind, CurrencyId currency, Day day) {
        if (new WorldQueries(aggregates.repositories()).polityNamed(name.value()).isPresent()) {
            throw new PolityNameTakenException(name);
        }
        PolityId id = PolityId.generate(ids);
        Polity organisation = Polity.foundOrganisation(id, name, kind, founder, currency, day);
        aggregates.commit(organisation);
        aggregates.commit(LandRegistry.open(id));
        return id;
    }

    /** Die Organisation, die der Akteur leitet; prüft die Art. */
    public Polity requireHead(ActorRef head, PolityId id, PolityKind kind) {
        Polity polity = aggregates.polity(id);
        if (polity.kind() != kind) {
            throw new NotAnOrganisationException(polity.name().value());
        }
        if (!polity.holds(head, Office.MAYOR)) {
            throw new NotOfficeHolderException(head, Office.MAYOR);
        }
        return polity;
    }

    /** Nimmt jemanden auf, z. B. in die Redaktion oder Geschäftsleitung. */
    public void admit(ActorRef head, PolityId id, PolityKind kind, ActorRef member, Day day) {
        Polity polity = requireHead(head, id, kind);
        if (!polity.isMember(member)) {
            polity.admit(member, MembershipType.CITIZEN, day);
            aggregates.commit(polity);
        }
    }

    /** Entfernt ein Mitglied; die Leitung selbst bleibt. */
    public boolean dismiss(ActorRef head, PolityId id, PolityKind kind, ActorRef member, Day day) {
        Polity polity = requireHead(head, id, kind);
        if (!polity.isMember(member) || member.equals(head)) {
            return false;
        }
        polity.leave(member, day);
        aggregates.commit(polity);
        return true;
    }

    /**
     * Gibt neue Aktien aus und bietet sie zum Preis an; der Erlös geht in die Firmenkasse.
     *
     * @param price Einheiten je Aktie
     */
    public StockExchange.Placement issue(ActorRef head, PolityId company, long shares, long price, Day day) {
        Polity polity = requireHead(head, company, PolityKind.COMPANY);
        StockExchange exchange = aggregates.exchange();
        exchange.issue(company, shares, day);
        aggregates.commit(exchange);
        return trading.place(polity.asActor(), company, Side.SELL, shares, price, day);
    }

    /**
     * Schüttet einen Betrag aus der Firmenkasse an alle Aktionäre aus, je Aktie gleich viel. Aktien der Firma selbst
     * bekommen nichts; Reste unter einer Einheit je Aktie bleiben in der Kasse.
     *
     * @return die Ausschüttung
     * @throws InvalidOrderException      wenn weniger als eine Einheit je Aktie herauskäme
     * @throws InsufficientFundsException wenn die Kasse den Betrag nicht hat
     */
    public Dividend payDividend(ActorRef head, PolityId company, Money total, Day day) {
        Polity polity = requireHead(head, company, PolityKind.COMPANY);
        StockExchange exchange = aggregates.exchange();
        Map<ActorRef, Long> holders = new LinkedHashMap<>(exchange.holders(company));
        holders.remove(polity.asActor());
        long outstanding = holders.values().stream().mapToLong(Long::longValue).sum();
        long perShare = outstanding == 0 ? 0 : total.amount() / outstanding;
        if (perShare <= 0) {
            throw new InvalidOrderException(total.amount());
        }
        Ledger ledger = aggregates.ledger();
        Money needed = Money.of(perShare * outstanding, total.currency());
        Money available = ledger.balanceOf(polity.asActor(), total.currency());
        if (available.amount() < needed.amount()) {
            throw new InsufficientFundsException(needed, available);
        }
        holders.forEach((holder, shares) -> ledger.transfer(polity.asActor(), holder,
                Money.of(perShare * shares, total.currency()), TransferReason.DIVIDEND, day));
        Dividend dividend = new Dividend(company, perShare, needed.amount(), day);
        exchange.recordDividend(dividend);
        aggregates.commit(ledger);
        aggregates.commit(exchange);
        return dividend;
    }

    /**
     * Löst eine Firma oder Zeitung auf. Bei einer Firma geht die Kasse anteilig an die Aktionäre, der Rest an die
     * Leitung.
     */
    public void dissolve(ActorRef head, PolityId id, Day day) {
        Polity polity = aggregates.polity(id);
        if (polity.kind() != PolityKind.COMPANY && polity.kind() != PolityKind.NEWSPAPER) {
            throw new NotAnOrganisationException(polity.name().value());
        }
        requireHead(head, id, polity.kind());
        if (polity.kind() == PolityKind.COMPANY) {
            StockExchange exchange = aggregates.exchange();
            if (exchange.listing(id).isPresent()) {
                Map<ActorRef, Long> holders = new LinkedHashMap<>(exchange.delist(id, day));
                holders.remove(polity.asActor());
                long outstanding = holders.values().stream().mapToLong(Long::longValue).sum();
                Ledger ledger = aggregates.ledger();
                long treasury = ledger.balanceOf(polity.asActor(), polity.currency()).amount();
                if (outstanding > 0 && treasury > 0) {
                    for (Map.Entry<ActorRef, Long> holder : holders.entrySet()) {
                        long share = treasury * holder.getValue() / outstanding;
                        if (share > 0) {
                            ledger.transfer(polity.asActor(), holder.getKey(), Money.of(share, polity.currency()),
                                    TransferReason.DIVIDEND, day);
                        }
                    }
                }
                aggregates.commit(ledger);
                aggregates.commit(exchange);
            }
        } else {
            var press = aggregates.press();
            press.forget(id, day);
            aggregates.commit(press);
        }
        dissolvePolity.execute(new DissolvePolity.Command(id, head, day));
    }
}
