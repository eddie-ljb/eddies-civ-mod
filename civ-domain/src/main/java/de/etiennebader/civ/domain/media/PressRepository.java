package de.etiennebader.civ.domain.media;

public interface PressRepository {

    Press load();

    void save(Press press);
}
