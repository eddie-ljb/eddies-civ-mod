package de.etiennebader.civ.server.web;

import de.etiennebader.civ.domain.actor.PlayerRef;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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

    private record Failures(Instant windowStart, int count) {
    }

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> codes = new ConcurrentHashMap<>();
    private final Map<String, Pending> sessions = new ConcurrentHashMap<>();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public WebSessions(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Ein neuer Anmeldecode für den Spieler; ältere Codes desselben Spielers verfallen. */
    public String issueCode(PlayerRef player, String name) {
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
        Pending pending = codes.remove(code.strip().toUpperCase(java.util.Locale.ROOT));
        if (pending == null || pending.expires().isBefore(now)) {
            failures.merge(remoteAddress, new Failures(now, 1), (old, added) ->
                    old.windowStart().plusSeconds(60).isAfter(now) ? new Failures(old.windowStart(), old.count() + 1) : added);
            return Optional.empty();
        }
        byte[] token = new byte[32];
        random.nextBytes(token);
        String session = HexFormat.of().formatHex(token);
        sessions.put(session, new Pending(pending.login(), now.plus(SESSION_LIFETIME)));
        return Optional.of(session);
    }

    public Optional<Login> session(String token) {
        Pending pending = sessions.get(token);
        if (pending == null) {
            return Optional.empty();
        }
        if (pending.expires().isBefore(clock.instant())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(pending.login());
    }

    public void logout(String token) {
        sessions.remove(token);
    }
}
