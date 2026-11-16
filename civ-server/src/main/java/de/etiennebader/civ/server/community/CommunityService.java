package de.etiennebader.civ.server.community;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.community.ChatMessage;
import de.etiennebader.civ.domain.community.CommunityView;
import de.etiennebader.civ.domain.community.Letter;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Briefe, Freundschaften und Chat.
 *
 * <ul>
 *   <li>{@code /civ brief <Spieler> <Betreff> | <Text>}: kommt am nächsten Tag an.</li>
 *   <li>{@code /civ post} zeigt das Postfach, {@code /civ post lesen <Nr>} einen Brief.</li>
 *   <li>{@code /civ freund <Spieler>} fragt an oder nimmt an, {@code /civ freund entfernen <Spieler>},
 *       {@code /civ freunde}.</li>
 *   <li>{@code /civ nachricht <Freund> <Text>}: Direktnachricht, auch an Freunde auf der Webseite.</li>
 * </ul>
 *
 * <p>Der allgemeine Chat im Spiel wird mitgeschrieben, damit die Webseite ihn zeigt; was auf der Webseite geschrieben
 * wird, erscheint im Spiel mit „[Web]“.
 */
public final class CommunityService {

    /** Wie viele Chatnachrichten die Webseite höchstens auf einmal holt. */
    public static final int CHAT_PAGE = 100;

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final Texts texts;
    private final Announcements announcements;
    private final Clock clock;
    private final Set<PlayerRef> online = ConcurrentHashMap.newKeySet();

    public CommunityService(CivApplication app, Calendar calendar, PlayerDirectory players, Texts texts,
                            Announcements announcements, Clock clock) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private CommunityView view() {
        return queries.community();
    }

    // ---- Anwesenheit ----

    /** Ein Spieler betritt den Server; er erfährt von neuer Post und Anfragen. */
    public void joined(PlayerRef player) {
        online.add(player);
        long unread = view().inbox(player, calendar.today()).stream().filter(letter -> !letter.read()).count();
        if (unread > 0) {
            announcements.add(Announcement.toPlayer(player, Message.of(MessageKey.MAIL_UNREAD, unread)));
        }
        int requests = view().requestsTo(player).size();
        if (requests > 0) {
            announcements.add(Announcement.toPlayer(player, Message.of(MessageKey.FRIEND_REQUESTS_WAITING, requests)));
        }
        for (PlayerRef friend : view().friendsOf(player)) {
            announcements.add(Announcement.toPlayer(friend, Message.of(MessageKey.FRIEND_ONLINE,
                    texts.actor(player))));
        }
    }

    public void left(PlayerRef player) {
        online.remove(player);
    }

    public boolean isOnline(PlayerRef player) {
        return online.contains(player);
    }

    public int onlineCount() {
        return online.size();
    }

    public List<String> onlineNames() {
        return online.stream().map(texts::actor).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    // ---- Briefe ----

    /** {@code /civ brief <Spieler> <Betreff> | <Text>} */
    public Reply sendLetter(Caller caller, String recipient, String subjectAndBody) {
        String[] parts = subjectAndBody.split("\\|", 2);
        return sendLetter(caller.player(), caller.name(), recipient, parts[0], parts.length > 1 ? parts[1] : "");
    }

    /** Brief senden, aus dem Spiel, dem Briefkasten oder von der Webseite. */
    public Reply sendLetter(PlayerRef from, String fromName, String recipient, String subject, String body) {
        Optional<PlayerRef> to = players.findByName(recipient.strip());
        if (to.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, recipient.strip()));
        }
        String cleanSubject = subject.strip();
        String cleanBody = body.strip();
        if (cleanSubject.isEmpty() || cleanSubject.length() > Letter.MAX_SUBJECT || cleanBody.length() > Letter.MAX_BODY) {
            return Reply.failure(Message.of(MessageKey.LETTER_INVALID, Letter.MAX_SUBJECT, Letter.MAX_BODY));
        }
        return attempt(() -> {
            Letter letter = app.community().send(from, to.get(), cleanSubject, cleanBody, calendar.today());
            return Reply.success(Message.of(MessageKey.LETTER_SENT, texts.actor(to.get()), letter.subject(),
                    letter.deliverOn().value()));
        });
    }

    /** Postfach eines Spielers, neueste zuerst; nummeriert wie für {@code /civ post lesen}. */
    public List<LetterView> inbox(PlayerRef player) {
        List<Letter> letters = view().inbox(player, calendar.today()).stream()
                .sorted(Comparator.comparing((Letter letter) -> letter.deliverOn().value()).reversed()).toList();
        List<LetterView> views = new ArrayList<>();
        for (int index = 0; index < letters.size(); index++) {
            views.add(view(letters.get(index), index + 1));
        }
        return views;
    }

    /** Gesendete Briefe, neueste zuerst. */
    public List<LetterView> sent(PlayerRef player) {
        List<Letter> letters = view().sent(player).stream()
                .sorted(Comparator.comparing((Letter letter) -> letter.sentOn().value()).reversed()).toList();
        List<LetterView> views = new ArrayList<>();
        for (int index = 0; index < letters.size(); index++) {
            views.add(view(letters.get(index), index + 1));
        }
        return views;
    }

    private LetterView view(Letter letter, int number) {
        return new LetterView(letter.id().value().toString(), number, texts.actor(letter.from()),
                texts.actor(letter.to()), letter.subject(), letter.body(), letter.sentOn().value(),
                letter.deliverOn().value(), letter.read(),
                app.addresses().residenceOf(letter.to()).map(address -> address.line()).orElse(""));
    }

    /** {@code /civ post} */
    public Reply showInbox(Caller caller) {
        List<LetterView> letters = inbox(caller.player());
        if (letters.isEmpty()) {
            return Reply.success(Message.of(MessageKey.MAIL_EMPTY));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.MAIL_HEADER, letters.size(),
                letters.stream().filter(letter -> !letter.read()).count()));
        for (LetterView letter : letters.subList(0, Math.min(10, letters.size()))) {
            lines.add(Message.of(letter.read() ? MessageKey.MAIL_ENTRY : MessageKey.MAIL_ENTRY_NEW, letter.number(),
                    letter.from(), letter.subject()));
        }
        return Reply.success(lines);
    }

    /** {@code /civ post lesen <Nr>} */
    public Reply readLetter(Caller caller, int number) {
        List<LetterView> letters = inbox(caller.player());
        if (number < 1 || number > letters.size()) {
            return Reply.failure(Message.of(MessageKey.LETTER_UNKNOWN, number));
        }
        LetterView letter = letters.get(number - 1);
        markRead(caller.player(), letter.id());
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.LETTER_HEADER, letter.from(), letter.subject(), letter.sentOn()));
        for (String line : letter.body().split("\n")) {
            lines.add(Message.of(MessageKey.LETTER_LINE, line));
        }
        return Reply.success(lines);
    }

    /** Markiert einen Brief des Spielers als gelesen. */
    public boolean markRead(PlayerRef reader, String letterId) {
        return view().inbox(reader, calendar.today()).stream()
                .filter(letter -> letter.id().value().toString().equals(letterId))
                .findFirst()
                .flatMap(letter -> app.community().read(letter.id(), reader, calendar.today()))
                .isPresent();
    }

    /** Tagesende: wer heute Post bekommt und online ist, erfährt es. */
    public void endOfDay(Day newDay) {
        for (Letter letter : view().letters()) {
            if (letter.deliverOn().equals(newDay)) {
                announcements.add(Announcement.toPlayer(letter.to(), Message.of(MessageKey.LETTER_ARRIVED,
                        texts.actor(letter.from()), letter.subject())));
            }
        }
    }

    // ---- Freunde ----

    /** {@code /civ freund <Spieler>}: fragt an oder nimmt eine Anfrage an. */
    public Reply befriend(PlayerRef from, String fromName, String otherName) {
        Optional<PlayerRef> other = players.findByName(otherName.strip());
        if (other.isEmpty() || other.get().equals(from)) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, otherName.strip()));
        }
        String name = texts.actor(other.get());
        if (view().friendsOf(from).contains(other.get())) {
            return Reply.success(Message.of(MessageKey.FRIEND_ALREADY, name));
        }
        return attempt(() -> {
            boolean friends = app.community().befriend(from, other.get(), calendar.today());
            if (friends) {
                announcements.add(Announcement.toPlayer(other.get(), Message.of(MessageKey.FRIEND_ACCEPTED,
                        fromName)));
                return Reply.success(Message.of(MessageKey.FRIEND_ACCEPTED, name));
            }
            announcements.add(Announcement.toPlayer(other.get(), Message.of(MessageKey.FRIEND_REQUEST_RECEIVED,
                    fromName)));
            return Reply.success(Message.of(MessageKey.FRIEND_REQUEST_SENT, name));
        });
    }

    /** {@code /civ freund entfernen <Spieler>}: beendet eine Freundschaft oder zieht eine Anfrage zurück. */
    public Reply unfriend(PlayerRef actor, String otherName) {
        Optional<PlayerRef> other = players.findByName(otherName.strip());
        if (other.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, otherName.strip()));
        }
        return app.community().unfriend(actor, other.get(), calendar.today())
                ? Reply.success(Message.of(MessageKey.FRIEND_REMOVED, texts.actor(other.get())))
                : Reply.failure(Message.of(MessageKey.FRIEND_NOT, texts.actor(other.get())));
    }

    /** Freunde zuerst (online vor offline), dann eingehende und ausgehende Anfragen. */
    public List<FriendView> friends(PlayerRef player) {
        List<FriendView> views = new ArrayList<>();
        view().friendsOf(player).stream().map(friend -> friend(friend, "friend"))
                .sorted(Comparator.comparing(FriendView::online).reversed()
                        .thenComparing(FriendView::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(views::add);
        view().requestsTo(player).stream().map(other -> friend(other, "incoming"))
                .sorted(Comparator.comparing(FriendView::name, String.CASE_INSENSITIVE_ORDER)).forEach(views::add);
        view().requestsFrom(player).stream().map(other -> friend(other, "outgoing"))
                .sorted(Comparator.comparing(FriendView::name, String.CASE_INSENSITIVE_ORDER)).forEach(views::add);
        return views;
    }

    private FriendView friend(PlayerRef player, String state) {
        return new FriendView(player.uuid().toString(), texts.actor(player), online.contains(player), state,
                queries.municipalityOf(player).map(town -> town.name().value()).orElse(""));
    }

    /** {@code /civ freunde} */
    public Reply showFriends(Caller caller) {
        List<FriendView> friends = friends(caller.player());
        if (friends.isEmpty()) {
            return Reply.success(Message.of(MessageKey.FRIENDS_NONE));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.FRIENDS_HEADER, friends.stream().filter(f -> f.state().equals("friend")).count()));
        for (FriendView friend : friends) {
            MessageKey key = switch (friend.state()) {
                case "incoming" -> MessageKey.FRIEND_ENTRY_INCOMING;
                case "outgoing" -> MessageKey.FRIEND_ENTRY_OUTGOING;
                default -> friend.online() ? MessageKey.FRIEND_ENTRY_ONLINE : MessageKey.FRIEND_ENTRY_OFFLINE;
            };
            lines.add(Message.of(key, friend.name(), friend.town()));
        }
        return Reply.success(lines);
    }

    // ---- Chat ----

    /** Eine Nachricht aus dem allgemeinen Chat im Spiel mitschreiben. */
    public void gameChat(PlayerRef from, String text) {
        String clean = clip(text);
        if (!clean.isEmpty()) {
            app.community().post(from, Optional.empty(), clean, clock.millis(), "game", calendar.today());
        }
    }

    /** {@code /civ nachricht <Freund> <Text>} */
    public Reply direct(PlayerRef from, String fromName, String toName, String text) {
        Optional<PlayerRef> to = players.findByName(toName.strip());
        if (to.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, toName.strip()));
        }
        String clean = clip(text);
        if (clean.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CHAT_EMPTY));
        }
        return attempt(() -> {
            app.community().post(from, to, clean, clock.millis(), "game", calendar.today());
            announcements.add(Announcement.toPlayer(to.get(), Message.of(MessageKey.DIRECT_RECEIVED, fromName, clean)));
            return Reply.success(Message.of(MessageKey.DIRECT_SENT, texts.actor(to.get()), clean));
        });
    }

    /**
     * Nachricht von der Webseite: allgemein oder an einen Freund. Sie erscheint im Spiel.
     *
     * @return die gespeicherte Nachricht
     */
    public ChatView webPost(PlayerRef from, Optional<String> toName, String text) {
        String clean = clip(text);
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Leere Nachricht");
        }
        Optional<PlayerRef> to = toName.filter(name -> !name.isBlank()).map(name -> players.findByName(name.strip())
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Empfänger: " + name)));
        ChatMessage message = app.community().post(from, to, clean, clock.millis(), "web", calendar.today());
        String fromName = texts.actor(from);
        if (to.isPresent()) {
            announcements.add(Announcement.toPlayer(to.get(), Message.of(MessageKey.DIRECT_RECEIVED_WEB, fromName,
                    clean)));
        } else {
            announcements.add(Announcement.toAll(Message.of(MessageKey.WEB_CHAT, fromName, clean)));
        }
        return chatView(message, from);
    }

    /** Nachrichten nach {@code afterSeq}, die der Leser sehen darf, älteste zuerst. */
    public List<ChatView> chat(PlayerRef reader, long afterSeq) {
        return view().chat(reader, afterSeq, CHAT_PAGE).stream().map(message -> chatView(message, reader)).toList();
    }

    private ChatView chatView(ChatMessage message, PlayerRef reader) {
        return new ChatView(message.seq(), texts.actor(message.from()), message.to().map(texts::actor).orElse(""),
                message.text(), message.at(), message.origin(), message.from().equals(reader));
    }

    private static String clip(String text) {
        String clean = text == null ? "" : text.strip().replaceAll("[\\p{Cntrl}&&[^\n]]", "");
        return clean.length() > ChatMessage.MAX_TEXT ? clean.substring(0, ChatMessage.MAX_TEXT) : clean;
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    /** Für Tests und die Webseite: die Gemeinde eines Spielers. */
    public Optional<PolityView> townOf(PlayerRef player) {
        return queries.municipalityOf(player);
    }
}
