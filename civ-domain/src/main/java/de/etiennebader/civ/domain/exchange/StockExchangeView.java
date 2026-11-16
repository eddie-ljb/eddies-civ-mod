package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lesende Sicht auf die Börse. */
public interface StockExchangeView {

    List<Listing> listings();

    Optional<Listing> listing(PolityId company);

    /** Aktionäre einer Firma mit Stückzahl, größte zuerst. */
    Map<ActorRef, Long> holders(PolityId company);

    long sharesOf(PolityId company, ActorRef holder);

    /** Aktien, die nicht schon in Verkaufsorders stecken. */
    long freeShares(PolityId company, ActorRef holder);

    List<Holding> holdingsOf(ActorRef holder);

    /** Alle Bestände, z. B. zum Speichern. */
    List<Holding> allHoldings();

    /** Alle Tageskurse je Firma, z. B. zum Speichern. */
    Map<PolityId, List<Quote>> allQuotes();

    /** Letzter Kurs; ohne Handel der Ausgabepreis. */
    long price(PolityId company);

    /** Offene Orders, älteste zuerst. */
    List<Order> orders();

    List<Order> orders(PolityId company);

    List<Order> ordersOf(ActorRef trader);

    Optional<Order> orderByNumber(int number);

    /** Kurse je Tag, älteste zuerst. */
    List<Quote> quotes(PolityId company);

    /** Letzte Geschäfte, älteste zuerst. */
    List<Trade> trades();

    List<Dividend> dividends();

    default List<Dividend> dividends(PolityId company) {
        return dividends().stream().filter(dividend -> dividend.company().equals(company)).toList();
    }
}
