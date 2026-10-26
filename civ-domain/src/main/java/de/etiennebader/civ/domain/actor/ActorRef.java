package de.etiennebader.civ.domain.actor;

import java.util.Comparator;
import java.util.UUID;

/**
 * Verweis auf jeden handelnden Akteur.
 *
 * <p>Versiegelt, damit der Compiler bei {@code switch} alle Varianten erzwingt.
 * Kommt eine Variante hinzu, meldet er jede Stelle, die angepasst werden muss.
 */
public sealed interface ActorRef permits PlayerRef, PersonRef, PolityRef {

    /**
     * Feste, vollständige Ordnung über alle Akteure: zuerst nach Art (Spieler, Villager, Polity),
     * dann nach UUID. Für deterministische Verarbeitung, z. B. beim Steuereinzug.
     */
    Comparator<ActorRef> CANONICAL_ORDER = Comparator
            .comparingInt(ActorRef::kindRank)
            .thenComparing(ActorRef::uuid);

    /** Die UUID hinter dem Verweis, unabhängig von der Art des Akteurs. */
    UUID uuid();

    private static int kindRank(ActorRef actor) {
        return switch (actor) {
            case PlayerRef ignored -> 0;
            case PersonRef ignored -> 1;
            case PolityRef ignored -> 2;
        };
    }
}
