package de.etiennebader.civ.server.community;

/**
 * Eine Chatnachricht für die Webseite.
 *
 * @param to     Empfänger einer Direktnachricht; leer im allgemeinen Chat
 * @param origin {@code game} oder {@code web}
 * @param mine   ob der Betrachter sie geschrieben hat
 */
public record ChatView(long seq, String from, String to, String text, long at, String origin, boolean mine) {
}
