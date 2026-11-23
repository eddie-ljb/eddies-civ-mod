package de.etiennebader.civ.application.company;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.exchange.StockExchange;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;

/**
 * Übernahme einer Firma: Wer mehr als die Hälfte aller Aktien hält, übernimmt die Leitung. Der bisherige Leiter bleibt
 * Mitglied, verliert aber das Amt.
 */
public final class TakeOverCompany {

    private final Aggregates aggregates;

    public TakeOverCompany(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @return ob die Firma übernommen wurde; {@code false}, wenn der Akteur die Aktienmehrheit nicht hat oder die
     *         Firma schon leitet
     * @throws NotAnOrganisationException wenn die Polity keine Firma ist
     */
    public boolean execute(PolityId company, ActorRef taker, Day day) {
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(taker, "taker");
        Objects.requireNonNull(day, "day");
        Polity polity = aggregates.polity(company);
        if (polity.kind() != PolityKind.COMPANY) {
            throw new NotAnOrganisationException(polity.name().value());
        }
        if (polity.holds(taker, Office.MAYOR) || !hasMajority(aggregates.exchange(), company, taker)) {
            return false;
        }
        if (!polity.isMember(taker)) {
            polity.admit(taker, MembershipType.CITIZEN, day);
        }
        polity.fillOffice(Office.MAYOR, taker, Optional.empty(), day);
        aggregates.commit(polity);
        return true;
    }

    /** Ob der Akteur mehr als die Hälfte aller ausgegebenen Aktien hält. */
    public static boolean hasMajority(StockExchange exchange, PolityId company, ActorRef holder) {
        long total = exchange.holders(company).values().stream().mapToLong(Long::longValue).sum();
        return total > 0 && exchange.sharesOf(company, holder) * 2 > total;
    }
}
