package de.etiennebader.civ.domain.civic;

public interface CivicRepository {

    CivicRegistry load();

    void save(CivicRegistry registry);
}
