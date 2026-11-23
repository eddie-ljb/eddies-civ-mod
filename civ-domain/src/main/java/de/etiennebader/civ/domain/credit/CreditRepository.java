package de.etiennebader.civ.domain.credit;

/** Speicher für das Kreditregister; es gibt genau eines. */
public interface CreditRepository {

    CreditRegistry load();

    void save(CreditRegistry registry);
}
