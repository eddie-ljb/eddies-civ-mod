package de.etiennebader.civ.server.town;

import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.command.Message;

import java.util.List;
import java.util.Objects;

/**
 * Steckbrief einer Gemeinde für Menüs.
 *
 * @param members    Namen der Spieler in Beitrittsreihenfolge, ohne Dorfbewohner
 * @param villagers  Dorfbewohner, die in der Gemeinde wohnen
 * @param claimLimit wie viele Chunks die Gemeinde bei ihrer Größe haben darf
 */
public record TownOverview(PolityId id, String name, Message tier, long foundedOn, String mayor, List<String> members,
                           int villagers, int chunks, int claimLimit, long coreChunks, long outposts, Money treasury, Viewer viewer) {

    public TownOverview {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(mayor, "mayor");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        Objects.requireNonNull(treasury, "treasury");
        Objects.requireNonNull(viewer, "viewer");
    }
}
