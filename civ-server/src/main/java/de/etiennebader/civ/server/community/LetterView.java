package de.etiennebader.civ.server.community;

/**
 * Ein Brief für Menü und Webseite.
 *
 * @param number  laufende Nummer im Postfach des Betrachters, ab 1
 * @param from    Name des Absenders
 * @param to      Name des Empfängers
 * @param address Anschrift des Empfängers zur Zeit der Ansicht; leer ohne Wohnsitz
 */
public record LetterView(String id, int number, String from, String to, String subject, String body, long sentOn,
                         long deliverOn, boolean read, String address) {
}
