package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Post zwischen Spielern und Dorfbewohnern oder zwischen Dorfbewohnern untereinander (Issue #18).
 *
 * <p>Bewusst einfach gehalten: eine Nachricht ist ein vorformulierter Textbaustein ({@link #send}), keine freie
 * Texteingabe. Nur im Arbeitsspeicher gehalten, wie die anderen in-memory Zustände in {@code VillagerService}
 * (siehe deren Klassenkommentar) - Post geht bei einem Serverneustart verloren.
 */
public final class VillagerMailbox {

    /** Höchstens so viele Nachrichten je Dorfbewohner; ältere fallen raus. */
    private static final int CAPACITY = 20;

    /** Eine zugestellte Nachricht. */
    public record Mail(ActorRef from, String templateKey, Day sent) {

        public Mail {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(templateKey, "templateKey");
            Objects.requireNonNull(sent, "sent");
        }
    }

    private final Map<PersonRef, Deque<Mail>> inboxes = new HashMap<>();

    /** Legt eine Nachricht in den Posteingang des Empfängers; die älteste fällt heraus, wenn er voll ist. */
    public void send(ActorRef from, PersonRef to, String templateKey, Day today) {
        Objects.requireNonNull(to, "to");
        Deque<Mail> inbox = inboxes.computeIfAbsent(to, ignored -> new ArrayDeque<>(CAPACITY));
        inbox.addFirst(new Mail(from, templateKey, today));
        while (inbox.size() > CAPACITY) {
            inbox.removeLast();
        }
    }

    /** Posteingang, neueste zuerst; leer, wenn noch nichts angekommen ist. */
    public List<Mail> inboxOf(PersonRef villager) {
        Deque<Mail> inbox = inboxes.get(Objects.requireNonNull(villager, "villager"));
        return inbox == null ? List.of() : List.copyOf(inbox);
    }

    /** Die zuletzt zugestellte Nachricht, falls vorhanden. */
    public Optional<Mail> latest(PersonRef villager) {
        List<Mail> inbox = inboxOf(villager);
        return inbox.isEmpty() ? Optional.empty() : Optional.of(inbox.get(0));
    }
}
