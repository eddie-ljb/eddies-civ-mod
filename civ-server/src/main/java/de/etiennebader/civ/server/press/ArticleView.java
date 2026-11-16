package de.etiennebader.civ.server.press;

import java.util.List;

/**
 * Ein Artikel für Menüs und Webseite.
 *
 * @param tendency      -2 bis 2
 * @param tendencyWord  z. B. „kritisch“
 * @param topic         Name des Themas
 */
public record ArticleView(int number, String newspaperId, String newspaper, String author, String title, String body,
                          String topic, String subject, int tendency, String tendencyWord, long day, List<String> towns,
                          int readers) {
}
