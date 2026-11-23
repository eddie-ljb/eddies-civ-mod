package de.etiennebader.civ.domain.economy;

/** Speicher für das Währungsregister; es gibt genau eines. */
public interface CurrencyRepository {

    CurrencyRegistry load();

    void save(CurrencyRegistry registry);
}
