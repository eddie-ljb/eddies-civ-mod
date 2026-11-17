package de.etiennebader.civ.server.web;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PlayerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSessionsTest {

    private static final class MovingClock extends Clock {
        private Instant now = Instant.parse("2026-11-17T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovingClock clock = new MovingClock();
    private final WebSessions sessions = new WebSessions(clock);
    private final PlayerRef alice = new PlayerRef(new PlayerId(UUID.randomUUID()));

    @Test
    @DisplayName("Sitzungen verfallen nach zwei Tagen ohne Nutzung, Benutzung verlängert")
    void idleSessionsExpire() {
        String token = sessions.redeem(sessions.issueCode(alice, "Alice"), "1.2.3.4").orElseThrow();
        clock.advance(Duration.ofDays(1));
        assertTrue(sessions.session(token).isPresent());
        clock.advance(Duration.ofDays(1).plusHours(12));
        assertTrue(sessions.session(token).isPresent(), "letzte Nutzung vor 36 Stunden");
        clock.advance(Duration.ofDays(2).plusMinutes(1));
        assertEquals(Optional.empty(), sessions.session(token));
    }

    @Test
    @DisplayName("Höchstens acht Sitzungen je Spieler; Abmelden überall")
    void sessionCapAndLogoutAll() {
        String first = sessions.redeem(sessions.issueCode(alice, "Alice"), "1.2.3.4").orElseThrow();
        for (int i = 0; i < 8; i++) {
            clock.advance(Duration.ofSeconds(1));
            sessions.redeem(sessions.issueCode(alice, "Alice"), "1.2.3.4").orElseThrow();
        }
        assertTrue(sessions.session(first).isEmpty(), "älteste Sitzung fällt weg");
        assertEquals(8L, (long) sessions.logoutAll(alice));
    }

    @Test
    @DisplayName("Zu viele Fehlversuche einer Adresse sperren sie für eine Minute")
    void bruteForceIsThrottled() {
        for (int i = 0; i < WebSessions.MAX_FAILURES_PER_MINUTE; i++) {
            assertTrue(sessions.redeem("ZZZZZZ", "6.6.6.6").isEmpty());
        }
        String code = sessions.issueCode(alice, "Alice");
        assertTrue(sessions.redeem(code, "6.6.6.6").isEmpty(), "gesperrt, auch mit richtigem Code");
        clock.advance(Duration.ofSeconds(61));
        assertTrue(sessions.redeem(sessions.issueCode(alice, "Alice"), "6.6.6.6").isPresent());
    }
}
