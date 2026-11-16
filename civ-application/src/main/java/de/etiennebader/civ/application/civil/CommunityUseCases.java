package de.etiennebader.civ.application.civil;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.community.ChatMessage;
import de.etiennebader.civ.domain.community.Community;
import de.etiennebader.civ.domain.community.Letter;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.LetterId;

import java.util.Objects;
import java.util.Optional;

/**
 * Post, Freunde und Chat.
 */
public final class CommunityUseCases {

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public CommunityUseCases(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /** Gibt einen Brief auf; er ist am nächsten Tag im Briefkasten. */
    public Letter send(PlayerRef from, PlayerRef to, String subject, String body, Day day) {
        Community community = aggregates.community();
        String trimmed = body.length() > Letter.MAX_BODY ? body.substring(0, Letter.MAX_BODY) : body;
        String title = subject.strip().isEmpty() ? "Brief" : subject.strip();
        Letter letter = community.send(LetterId.generate(ids), from, to,
                title.length() > Letter.MAX_SUBJECT ? title.substring(0, Letter.MAX_SUBJECT) : title, trimmed, day);
        aggregates.commit(community);
        return letter;
    }

    /** Öffnet einen Brief des Lesers und markiert ihn als gelesen. */
    public Optional<Letter> read(LetterId id, PlayerRef reader, Day day) {
        Community community = aggregates.community();
        Optional<Letter> letter = community.read(id, reader, day);
        aggregates.commit(community);
        return letter;
    }

    /** @return ob daraus eine Freundschaft wurde (die Gegenseite hatte schon angefragt) */
    public boolean befriend(PlayerRef from, PlayerRef to, Day day) {
        Community community = aggregates.community();
        boolean friends = community.befriend(from, to, day);
        aggregates.commit(community);
        return friends;
    }

    /** Baurecht für einen Freund geben oder nehmen. @return ob sich etwas änderte */
    public boolean trust(PlayerRef owner, PlayerRef friend, boolean trusted, Day day) {
        Community community = aggregates.community();
        boolean changed = community.trust(owner, friend, trusted, day);
        aggregates.commit(community);
        return changed;
    }

    public boolean unfriend(PlayerRef actor, PlayerRef other, Day day) {
        Community community = aggregates.community();
        boolean changed = community.unfriend(actor, other, day);
        aggregates.commit(community);
        return changed;
    }

    /**
     * @param to     leer für den öffentlichen Chat
     * @param origin {@code game} oder {@code web}
     */
    public ChatMessage post(PlayerRef from, Optional<PlayerRef> to, String text, long at, String origin, Day day) {
        Community community = aggregates.community();
        String trimmed = text.strip();
        ChatMessage message = community.post(from, to,
                trimmed.length() > ChatMessage.MAX_TEXT ? trimmed.substring(0, ChatMessage.MAX_TEXT) : trimmed, at,
                origin, day);
        aggregates.commit(community);
        return message;
    }
}
