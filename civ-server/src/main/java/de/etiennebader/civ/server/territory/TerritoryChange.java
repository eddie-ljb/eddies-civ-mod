package de.etiennebader.civ.server.territory;

import de.etiennebader.civ.server.command.Message;

import java.util.Objects;

/**
 * Ein Spieler hat eine Gebietsgrenze überschritten.
 *
 * @param title    groß anzuzeigender Name, z. B. der Gemeinde oder „Wildnis“
 * @param subtitle Ergänzung, z. B. „Kerngebiet“
 */
public record TerritoryChange(Message title, Message subtitle, Relation relation) {

    public TerritoryChange {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subtitle, "subtitle");
        Objects.requireNonNull(relation, "relation");
    }
}
