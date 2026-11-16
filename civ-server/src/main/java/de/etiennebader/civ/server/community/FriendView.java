package de.etiennebader.civ.server.community;

/**
 * Ein Freund oder eine offene Anfrage.
 *
 * @param state    {@code friend}, {@code incoming} (Anfrage an den Betrachter) oder {@code outgoing}
 * @param trusted  ob der Betrachter dem Freund Baurecht auf seinen Grundstücken gibt
 * @param trustsMe ob der Freund dem Betrachter Baurecht gibt
 */
public record FriendView(String id, String name, boolean online, String state, String town, boolean trusted,
                         boolean trustsMe) {

    public FriendView(String id, String name, boolean online, String state, String town) {
        this(id, name, online, state, town, false, false);
    }
}
