package de.etiennebader.civ.server.web;

import de.etiennebader.civ.domain.actor.PlayerRef;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anmeldung an der Webseite: Im Spiel holt sich ein Spieler mit {@code /civ web} einen kurzen Code, den er auf
 * der Webseite eingibt. Dafür bekommt er eine Sitzung als Cookie. Codes gelten 10 Minuten und nur einmal,
 * Sitzungen 7 Tage oder bis zum Neustart. Fehlversuche werden je Adresse begrenzt.
 */
public final class WebSessions {

    static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    static final Duration SESSION_LIFETIME = Duration.ofDays(7);
    static final int MAX_FAILURES_PER_MINUTE = 10;
    /** Fehlversuche aller Adressen zusammen je Minute, gegen verteiltes Raten. */
    static final int MAX_GLOBAL_FAILURES_PER_MINUTE = 200;
    /** Eine Sitzung, die so lange nicht benutzt wurde, verfällt. */
    static final Duration IDLE_LIFETIME = Duration.ofDays(2);
    static final int MAX_SESSIONS_PER_PLAYER = 8;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    /** Wer angemeldet ist. */
    public record Login(PlayerRef player, String name) {

        public Login {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(name, "name");
        }
    }

    private record Pending(Login login, Instant expires) {
    }

    private record Session(Login login, Instant expires, Instant created, Instant lastUsed) {
    }

    private record Failures(Instant windowStart, int count) {
    }

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> codes = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private volatile Failures global = new Failures(Instant.EPOCH, 0);
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public WebSessions(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Ein neuer Anmeldecode für den Spieler; ältere Codes desselben Spielers verfallen. */
    public String issueCode(PlayerRef player, String name) {
        purge();
        codes.values().removeIf(pending -> pending.login().player().equals(player));
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        codes.put(code.toString(), new Pending(new Login(player, name), clock.instant().plus(CODE_LIFETIME)));
        return code.toString();
    }

    /**
     * Löst einen Code gegen eine Sitzung ein.
     *
     * @return das Sitzungs-Token; leer bei falschem, abgelaufenem Code oder zu vielen Fehlversuchen
     */
    public Optional<String> redeem(String code, String remoteAddress) {
        Instant now = clock.instant();
        Failures recent = failures.get(remoteAddress);
        if (recent != null && recent.windowStart().plusSeconds(60).isAfter(now)
                && recent.count() >= MAX_FAILURES_PER_MINUTE) {
            return Optional.empty();
        }
        if (global.windowStart().plusSeconds(60).isAfter(now) && global.count() >= MAX_GLOBAL_FAILURES_PER_MINUTE) {
            return Optional.empty();
        }
        Pending pending = codes.remove(code.strip().toUpperCase(java.util.Locale.ROOT));
        if (pending == null || pending.expires().isBefore(now)) {
            failures.merge(remoteAddress, new Failures(now, 1), (old, added) ->
                    old.windowStart().plusSeconds(60).isAfter(now) ? new Failures(old.windowStart(), old.count() + 1) : added);
            Failures before = global;
            global = before.windowStart().plusSeconds(60).isAfter(now)
                    ? new Failures(before.windowStart(), before.count() + 1) : new Failures(now, 1);
            return Optional.empty();
        }
        byte[] token = new byte[32];
        random.nextBytes(token);
        String session = HexFormat.of().formatHex(token);
        List<Map.Entry<String, Session>> own = sessions.entrySet().stream()
                .filter(entry -> entry.getValue().login().player().equals(pending.login().player()))
                .sorted(java.util.Comparator.comparing(entry -> entry.getValue().created()))
                .toList();
        for (int index = 0; index <= own.size() - MAX_SESSIONS_PER_PLAYER; index++) {
            sessions.remove(own.get(index).getKey());
        }
        sessions.put(session, new Session(pending.login(), now.plus(SESSION_LIFETIME), now, now));
        return Optional.of(session);
    }

    public Optional<Login> session(String token) {
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (session.expires().isBefore(now) || session.lastUsed().plus(IDLE_LIFETIME).isBefore(now)) {
            sessions.remove(token);
            return Optional.empty();
        }
        sessions.put(token, new Session(session.login(), session.expires(), session.created(), now));
        return Optional.of(session.login());
    }

    /** Meldet alle Sitzungen eines Spielers ab, z. B. mit {@code /civ web abmelden}. */
    public int logoutAll(PlayerRef player) {
        int before = sessions.size();
        sessions.values().removeIf(session -> session.login().player().equals(player));
        return before - sessions.size();
    }

    /** Räumt abgelaufene Codes, Sitzungen und alte Fehlversuche weg. */
    private void purge() {
        Instant now = clock.instant();
        codes.values().removeIf(pending -> pending.expires().isBefore(now));
        sessions.values().removeIf(session -> session.expires().isBefore(now)
                || session.lastUsed().plus(IDLE_LIFETIME).isBefore(now));
        failures.values().removeIf(failure -> failure.windowStart().plusSeconds(60).isBefore(now));
    }

    public void logout(String token) {
        sessions.remove(token);
    }
}
