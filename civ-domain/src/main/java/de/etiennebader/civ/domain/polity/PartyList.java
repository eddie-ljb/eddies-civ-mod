package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Kodierung der Parteiliste ({@link de.etiennebader.civ.domain.law.StandardParameters#PARTY_LIST}) als Text, und wie
 * sie bei einer Listenwahl auf die tatsächlichen Kandidaten einer Liste angewendet wird.
 */
public final class PartyList {

    private PartyList() {
    }

    /** Eine Parteiliste als Text, z. B. {@code "PLAYER:<uuid>,PERSON:<uuid>"}; leere Liste ergibt {@code ""}. */
    public static String encode(List<ActorRef> order) {
        Objects.requireNonNull(order, "order");
        StringBuilder text = new StringBuilder();
        for (ActorRef actor : order) {
            if (!text.isEmpty()) {
                text.append(',');
            }
            text.append(kindOf(actor)).append(':').append(actor.uuid());
        }
        return text.toString();
    }

    /** Gegenstück zu {@link #encode}; überspringt Einträge, die sich nicht lesen lassen. */
    public static List<ActorRef> decode(String text) {
        Objects.requireNonNull(text, "text");
        List<ActorRef> order = new ArrayList<>();
        if (text.isBlank()) {
            return order;
        }
        for (String entry : text.split(",")) {
            try {
                int colon = entry.indexOf(':');
                order.add(actorOf(entry.substring(0, colon), UUID.fromString(entry.substring(colon + 1))));
            } catch (RuntimeException e) {
                // ungültiger oder veralteter Eintrag: überspringen, statt die ganze Liste zu verwerfen
            }
        }
        return order;
    }

    /**
     * Reihenfolge der Kandidaten einer Liste für die Sitzvergabe: zuerst die Kandidaten, die auch auf der
     * Parteiliste stehen, in deren Reihenfolge, danach alle übrigen Kandidaten in ihrer Kandidatur-Reihenfolge
     * (z. B. weil sie nach dem Festlegen der Liste kandidiert haben, oder die Liste leer ist).
     */
    public static List<ActorRef> apply(String encodedList, List<ActorRef> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        List<ActorRef> listed = decode(encodedList);
        Set<ActorRef> remaining = new LinkedHashSet<>(candidates);
        List<ActorRef> ordered = new ArrayList<>();
        for (ActorRef actor : listed) {
            if (remaining.remove(actor)) {
                ordered.add(actor);
            }
        }
        ordered.addAll(remaining);
        return ordered;
    }

    private static String kindOf(ActorRef actor) {
        return switch (actor) {
            case PlayerRef ignored -> "PLAYER";
            case PersonRef ignored -> "PERSON";
            case PolityRef ignored -> "POLITY";
        };
    }

    private static ActorRef actorOf(String kind, UUID uuid) {
        return switch (kind) {
            case "PLAYER" -> new PlayerRef(new PlayerId(uuid));
            case "PERSON" -> new PersonRef(new PersonId(uuid));
            case "POLITY" -> new PolityRef(new PolityId(uuid));
            default -> throw new IllegalArgumentException("Unbekannte Akteursart: " + kind);
        };
    }
}
