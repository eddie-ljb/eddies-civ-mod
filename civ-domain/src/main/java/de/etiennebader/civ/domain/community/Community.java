package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.LetterId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Briefe, Freundschaften und Chat aller Spieler.
 *
 * <ul>
 *   <li>Briefe werden am Tag nach dem Absenden zugestellt; gespeichert bleiben je Empfänger die neuesten
 *       {@value #KEPT_LETTERS}.</li>
 *   <li>Freundschaft entsteht, wenn beide einander anfragen (die zweite Anfrage nimmt die erste an).</li>
 *   <li>Vom Chat bleiben die neuesten {@value #KEPT_MESSAGES} Nachrichten.</li>
 * </ul>
 */
public final class Community extends AggregateRoot<CommunityEvent> implements CommunityView {

    public static final int KEPT_LETTERS = 50;
    public static final int KEPT_MESSAGES = 500;

    private final Map<LetterId, Letter> letters = new LinkedHashMap<>();
    private final Set<List<PlayerRef>> friendships = new LinkedHashSet<>();
    private final Set<List<PlayerRef>> requests = new LinkedHashSet<>();
    private final List<ChatMessage> messages = new ArrayList<>();
    private long lastSeq;

    private Community() {
    }

    public static Community empty() {
        return new Community();
    }

    /**
     * @param friendships Paare befreundeter Spieler
     * @param requests    offene Anfragen als (von, an)
     */
    public static Community restore(Collection<Letter> letters, Collection<List<PlayerRef>> friendships,
                                    Collection<List<PlayerRef>> requests, Collection<ChatMessage> messages) {
        Community community = new Community();
        letters.forEach(letter -> community.letters.put(letter.id(), letter));
        friendships.forEach(pair -> community.friendships.add(pair(pair.get(0), pair.get(1))));
        community.requests.addAll(requests.stream().map(List::copyOf).toList());
        messages.stream().sorted(Comparator.comparingLong(ChatMessage::seq)).forEach(community.messages::add);
        community.lastSeq = community.messages.stream().mapToLong(ChatMessage::seq).max().orElse(0);
        return community;
    }

    // ---- Briefe ----

    /** Gibt einen Brief auf; er kommt am nächsten Tag an. */
    public Letter send(LetterId id, PlayerRef from, PlayerRef to, String subject, String body, Day day) {
        Letter letter = new Letter(id, from, to, subject, body, day, day.plus(1), false);
        letters.put(id, letter);
        raise(new LetterSent(from, id, to, day));
        List<LetterId> received = letters.values().stream().filter(stored -> stored.to().equals(to))
                .map(Letter::id).toList();
        for (int index = 0; index < received.size() - KEPT_LETTERS; index++) {
            letters.remove(received.get(index));
        }
        return letter;
    }

    /** Markiert einen Brief des Lesers als gelesen. @return der Brief, falls er ihm gehört */
    public Optional<Letter> read(LetterId id, PlayerRef reader, Day day) {
        Letter letter = letters.get(Objects.requireNonNull(id, "id"));
        if (letter == null || !letter.to().equals(reader) || !letter.deliveredBy(day)) {
            return Optional.empty();
        }
        if (!letter.read()) {
            letters.put(id, letter.markedRead());
            raise(new LetterRead(reader, id, day));
        }
        return Optional.of(letters.get(id));
    }

    @Override
    public List<Letter> inbox(PlayerRef player, Day today) {
        return letters.values().stream().filter(letter -> letter.to().equals(player) && letter.deliveredBy(today))
                .toList().reversed();
    }

    @Override
    public List<Letter> sent(PlayerRef player) {
        return letters.values().stream().filter(letter -> letter.from().equals(player)).toList().reversed();
    }

    @Override
    public List<Letter> letters() {
        return List.copyOf(letters.values());
    }

    // ---- Freunde ----

    /**
     * Fragt eine Freundschaft an. Hat der andere schon angefragt, sind beide nun befreundet.
     *
     * @return ob daraus eine Freundschaft wurde
     */
    public boolean befriend(PlayerRef from, PlayerRef to, Day day) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to) || areFriends(from, to)) {
            return areFriends(from, to);
        }
        if (requests.remove(List.of(to, from))) {
            friendships.add(pair(from, to));
            raise(new FriendshipChanged(from, to, true, day));
            return true;
        }
        if (requests.add(List.of(from, to))) {
            raise(new FriendRequested(from, to, day));
        }
        return false;
    }

    /** Beendet eine Freundschaft oder zieht eine Anfrage zurück bzw. lehnt sie ab. @return ob sich etwas änderte */
    public boolean unfriend(PlayerRef actor, PlayerRef other, Day day) {
        boolean changed = friendships.remove(pair(actor, other));
        changed |= requests.remove(List.of(actor, other));
        changed |= requests.remove(List.of(other, actor));
        if (changed) {
            raise(new FriendshipChanged(actor, other, false, day));
        }
        return changed;
    }

    @Override
    public Set<PlayerRef> friendsOf(PlayerRef player) {
        Set<PlayerRef> friends = new LinkedHashSet<>();
        for (List<PlayerRef> pair : friendships) {
            if (pair.get(0).equals(player)) {
                friends.add(pair.get(1));
            } else if (pair.get(1).equals(player)) {
                friends.add(pair.get(0));
            }
        }
        return friends;
    }

    @Override
    public Set<PlayerRef> requestsTo(PlayerRef player) {
        Set<PlayerRef> from = new LinkedHashSet<>();
        requests.stream().filter(request -> request.get(1).equals(player)).forEach(request -> from.add(request.get(0)));
        return from;
    }

    @Override
    public Set<PlayerRef> requestsFrom(PlayerRef player) {
        Set<PlayerRef> to = new LinkedHashSet<>();
        requests.stream().filter(request -> request.get(0).equals(player)).forEach(request -> to.add(request.get(1)));
        return to;
    }

    @Override
    public List<List<PlayerRef>> friendships() {
        return List.copyOf(friendships);
    }

    @Override
    public List<List<PlayerRef>> requests() {
        return List.copyOf(requests);
    }

    private static List<PlayerRef> pair(PlayerRef one, PlayerRef other) {
        return one.uuid().compareTo(other.uuid()) <= 0 ? List.of(one, other) : List.of(other, one);
    }

    // ---- Chat ----

    /**
     * Schreibt eine Chatnachricht.
     *
     * @param to leer für den öffentlichen Chat
     * @throws NotFriendsException wenn eine Direktnachricht nicht an einen Freund geht
     */
    public ChatMessage post(PlayerRef from, Optional<PlayerRef> to, String text, long at, String origin, Day day) {
        Objects.requireNonNull(to, "to");
        if (to.isPresent() && !areFriends(from, to.get())) {
            throw new NotFriendsException(to.get());
        }
        ChatMessage message = new ChatMessage(++lastSeq, from, to, text, at, origin);
        messages.add(message);
        while (messages.size() > KEPT_MESSAGES) {
            messages.removeFirst();
        }
        raise(new ChatPosted(from, message.seq(), message.direct(), day));
        return message;
    }

    @Override
    public List<ChatMessage> chat(PlayerRef reader, long afterSeq, int limit) {
        List<ChatMessage> visible = messages.stream()
                .filter(message -> message.seq() > afterSeq && message.visibleTo(reader))
                .toList();
        return visible.subList(Math.max(0, visible.size() - limit), visible.size());
    }

    @Override
    public List<ChatMessage> messages() {
        return List.copyOf(messages);
    }
}
