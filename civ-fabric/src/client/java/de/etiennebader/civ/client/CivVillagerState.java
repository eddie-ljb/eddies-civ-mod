package de.etiennebader.civ.client;

import java.util.UUID;

/** Trägt die UUID des Dorfbewohners durch den Render-Zustand bis zur Texturwahl. */
public interface CivVillagerState {

    UUID civ$uuid();

    void civ$setUuid(UUID uuid);
}
