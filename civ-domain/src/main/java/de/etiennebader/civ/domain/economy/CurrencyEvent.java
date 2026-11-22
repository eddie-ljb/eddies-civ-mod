package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainEvent;

/** Ereignisse des Währungsregisters (Issue #4). */
public sealed interface CurrencyEvent extends DomainEvent {

    CurrencyId currency();

    /** Eine neue Währung wurde erstellt. */
    record Created(CurrencyId currency, Day occurredOn) implements CurrencyEvent {
    }

    /** Der Wechselkurs einer Währung zu Smaragd hat sich geändert. */
    record RateChanged(CurrencyId currency, Day occurredOn) implements CurrencyEvent {
    }

    /** Ein neuer Zentralbank-Vorsitzender wurde ernannt. */
    record ChairAppointed(CurrencyId currency, Day occurredOn) implements CurrencyEvent {
    }

    /** Der Leitzins wurde geändert. */
    record BaseRateChanged(CurrencyId currency, Day occurredOn) implements CurrencyEvent {
    }
}
