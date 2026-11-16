package de.etiennebader.civ.domain.justice;

public interface JusticeRepository {

    Judiciary load();

    void save(Judiciary judiciary);
}
