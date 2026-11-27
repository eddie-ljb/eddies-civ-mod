package de.etiennebader.civ.client;

import java.util.UUID;

/** Trägt die UUID des Dorfbewohners durch den Render-Zustand bis zur Texturwahl. */
public interface CivVillagerState {

    UUID civ$uuid();

    void civ$setUuid(UUID uuid);

    /** Größenfaktor des Dorfbewohners; daran erkennt der Client seine Rasse. */
    float civ$scale();

    void civ$setScale(float scale);
}
