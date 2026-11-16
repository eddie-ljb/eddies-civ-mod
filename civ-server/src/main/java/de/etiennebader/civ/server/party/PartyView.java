package de.etiennebader.civ.server.party;

import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Partei, wie Menü, Chat und Webseite sie zeigen.
 *
 * @param order    -100 (Freiheit) bis +100 (Ordnung)
 * @param economy  -100 (niedrige Steuern) bis +100 (Ausbau)
 * @param emblem   Item-ID des Wappens
 * @param platform Wahlprogramm, mit dem ihre Mitglieder antreten
 * @param member   ob der Betrachter Mitglied ist
 * @param head     ob der Betrachter den Vorsitz hat
 */
public record PartyView(PolityId id, String name, int order, int economy, String emblem, Platform platform,
                        String chair, long players, long villagers, long foundedOn, boolean member, boolean head) {

    public PartyView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(emblem, "emblem");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(chair, "chair");
    }
}
