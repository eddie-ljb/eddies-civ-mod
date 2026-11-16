package de.etiennebader.civ.server.community;

/**
 * Ein Freund oder eine offene Anfrage.
 *
 * @param state {@code friend}, {@code incoming} (Anfrage an den Betrachter) oder {@code outgoing}
 */
public record FriendView(String id, String name, boolean online, String state, String town) {
}
