package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.PolityId;

/** Ereignisse an der Börse. */
public sealed interface ExchangeEvent extends DomainEvent
        permits ExchangeEvent.CompanyListed, ExchangeEvent.SharesIssued, ExchangeEvent.OrderPlaced,
        ExchangeEvent.OrderCancelled, ExchangeEvent.SharesTraded, ExchangeEvent.DividendPaid,
        ExchangeEvent.CompanyDelisted {

    PolityId company();

    record CompanyListed(PolityId company, long shares, long price, Day occurredOn)
            implements ExchangeEvent {
    }

    record SharesIssued(PolityId company, long shares, Day occurredOn)
            implements ExchangeEvent {
    }

    record OrderPlaced(PolityId company, int number, Side side, Day occurredOn)
            implements ExchangeEvent {
    }

    record OrderCancelled(PolityId company, int number, Day occurredOn)
            implements ExchangeEvent {
    }

    record SharesTraded(PolityId company, long quantity, long price, Day occurredOn)
            implements ExchangeEvent {
    }

    record DividendPaid(PolityId company, long perShare, Day occurredOn)
            implements ExchangeEvent {
    }

    record CompanyDelisted(PolityId company, Day occurredOn)
            implements ExchangeEvent {
    }
}
