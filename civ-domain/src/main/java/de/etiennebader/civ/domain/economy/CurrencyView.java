package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.shared.CurrencyId;

import de.etiennebader.civ.domain.shared.Money;

import java.util.List;
import java.util.Optional;

/** Lesender Zugriff auf das Währungsregister. */
public interface CurrencyView {

    Optional<Currency> find(CurrencyId id);

    /** @throws UnknownCurrencyException wenn die Währung nicht registriert ist */
    Currency require(CurrencyId id);

    List<Currency> all();

    /** Rechnet einen Betrag in eine andere Währung um (Smaragd als gemeinsamer Nenner, abgerundet). */
    Money convert(Money amount, CurrencyId target);
}
