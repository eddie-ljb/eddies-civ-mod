package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.List;
import java.util.Set;

/**
 * Lesender Zugriff auf Briefe, Freundschaften und Chat.
 */
public interface CommunityView {

    /** Zugestellte Briefe an den Spieler, die neuesten zuerst. */
    List<Letter> inbox(PlayerRef player, Day today);

    /** Briefe des Spielers, die neuesten zuerst. */
    List<Letter> sent(PlayerRef player);

    /** Alle Briefe, z. B. zum Speichern. */
    List<Letter> letters();

    Set<PlayerRef> friendsOf(PlayerRef player);

    /** Offene Anfragen an den Spieler. */
    Set<PlayerRef> requestsTo(PlayerRef player);

    /** Offene Anfragen des Spielers. */
    Set<PlayerRef> requestsFrom(PlayerRef player);

    /** Alle Freundschaften als Paare, z. B. zum Speichern. */
    List<List<PlayerRef>> friendships();

    /** Alle offenen Anfragen als Paare (von, an). */
    List<List<PlayerRef>> requests();

    /** Nachrichten mit einer Nummer über {@code afterSeq}, die der Spieler lesen darf, höchstens {@code limit}. */
    List<ChatMessage> chat(PlayerRef reader, long afterSeq, int limit);

    /** Alle gespeicherten Chatnachrichten. */
    List<ChatMessage> messages();

    default boolean areFriends(PlayerRef one, PlayerRef other) {
        return friendsOf(one).contains(other);
    }
}
