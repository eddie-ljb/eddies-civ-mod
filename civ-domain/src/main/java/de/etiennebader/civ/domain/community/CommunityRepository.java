package de.etiennebader.civ.domain.community;

/**
 * Speicher für die Gemeinschaft; es gibt genau eine {@link Community}.
 */
public interface CommunityRepository {

    Community load();

    void save(Community community);
}
