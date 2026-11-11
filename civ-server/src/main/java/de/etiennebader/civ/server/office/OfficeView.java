package de.etiennebader.civ.server.office;

import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.server.command.Message;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Amt für Menüs, Listen und die Webseite.
 *
 * @param holder   Name des Inhabers; leer, wenn das Amt frei ist
 * @param until    letzter Tag der Amtszeit; leer, wenn unbefristet oder frei
 * @param implicit ob der Inhaber nur kommissarisch im Amt ist (Bürgermeister ohne Wahl)
 * @param election die laufende Wahl für das Amt
 */
public record OfficeView(Office office, Message name, Optional<String> holder, Optional<Long> until, boolean implicit,
                         Optional<ElectionView> election) {

    public OfficeView {
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(election, "election");
    }
}
