package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.CurrencyId;

import java.util.Objects;
import java.util.Optional;

/**
 * Eine selbst erstellte Währung: Name, Symbol und Wechselkurs zu Smaragd (siehe {@link ExchangeRate}).
 * Wechselkurse zwischen zwei Währungen ergeben sich daraus, dass beide ihren Kurs zu Smaragd als
 * gemeinsamen Nenner nutzen ({@link #convertTo}).
 *
 * <p>Trägt zugleich ihre eigene, minimale Zentralbank: einen Vorsitzenden ({@link #centralBankChair},
 * Spieler oder Dorfbewohner) und einen Leitzins ({@link #baseRatePercent}), siehe Issue #4. Nur der
 * Vorsitzende darf den Leitzins ändern oder einen Nachfolger ernennen; vor der ersten Ernennung darf
 * dies die Gründerin bzw. der Gründer ({@link #issuer}).
 */
public record Currency(CurrencyId id, String name, String symbol, ExchangeRate rateToEmerald,
                       Optional<ActorRef> issuer, Optional<ActorRef> centralBankChair, int baseRatePercent) {

    public static final int MAX_NAME = 32;
    public static final int MAX_SYMBOL = 4;
    public static final int MAX_BASE_RATE_PERCENT = 100;

    public Currency {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rateToEmerald, "rateToEmerald");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(centralBankChair, "centralBankChair");
        name = Objects.requireNonNull(name, "name").strip();
        symbol = Objects.requireNonNull(symbol, "symbol").strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Name der Währung leer oder zu lang: " + name);
        }
        if (symbol.isEmpty() || symbol.length() > MAX_SYMBOL) {
            throw new IllegalArgumentException("Symbol der Währung leer oder zu lang: " + symbol);
        }
        if (baseRatePercent < 0 || baseRatePercent > MAX_BASE_RATE_PERCENT) {
            throw new IllegalArgumentException("Leitzins außerhalb 0-" + MAX_BASE_RATE_PERCENT + ": " + baseRatePercent);
        }
    }

    /** Die eingebaute Leitwährung, immer 1:1 zu sich selbst, ohne Zentralbank. */
    public static Currency emerald() {
        return new Currency(CurrencyId.EMERALD, "Smaragd", "E", ExchangeRate.PARITY, Optional.empty(),
                Optional.empty(), 0);
    }

    public Currency withRate(ExchangeRate rate) {
        return new Currency(id, name, symbol, Objects.requireNonNull(rate, "rate"), issuer, centralBankChair,
                baseRatePercent);
    }

    public Currency withChair(ActorRef chair) {
        return new Currency(id, name, symbol, rateToEmerald, issuer, Optional.of(Objects.requireNonNull(chair, "chair")),
                baseRatePercent);
    }

    public Currency withBaseRatePercent(int percent) {
        return new Currency(id, name, symbol, rateToEmerald, issuer, centralBankChair, percent);
    }

    /** Wer aktuell über Leitzins und Nachfolge entscheiden darf: der Vorsitzende, sonst die Gründerin/der Gründer. */
    public Optional<ActorRef> governor() {
        return centralBankChair.isPresent() ? centralBankChair : issuer;
    }

    /** Wandelt einen Betrag dieser Währung in eine andere um, mit Smaragd als gemeinsamem Nenner. */
    public long convertTo(long amount, Currency target) {
        Objects.requireNonNull(target, "target");
        return target.rateToEmerald.fromEmeraldUnits(rateToEmerald.toEmeraldUnits(amount));
    }
}
