package de.etiennebader.civ.persistence;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.UUID;

/**
 * Speichert Akteure als Paar aus Art ({@code PLAYER}, {@code PERSON}, {@code POLITY}) und UUID.
 */
public final class ActorCodec {

    private ActorCodec() {
    }

    public static String kindOf(ActorRef actor) {
        return switch (actor) {
            case PlayerRef ignored -> "PLAYER";
            case PersonRef ignored -> "PERSON";
            case PolityRef ignored -> "POLITY";
        };
    }

    public static ActorRef decode(String kind, String id) {
        UUID uuid = UUID.fromString(id);
        return switch (kind) {
            case "PLAYER" -> new PlayerRef(new PlayerId(uuid));
            case "PERSON" -> new PersonRef(new PersonId(uuid));
            case "POLITY" -> new PolityRef(new PolityId(uuid));
            default -> throw new IllegalArgumentException("Unbekannte Akteursart: " + kind);
        };
    }
}
