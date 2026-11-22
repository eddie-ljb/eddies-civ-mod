package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyRegistryTest {

    private static final Day DAY = Day.of(1);
    private static final CurrencyId GOLD = new CurrencyId("GOLD");
    private static final CurrencyId SILVER = new CurrencyId("SILVER");

    private final SeededIdGenerator ids = new SeededIdGenerator(4);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CurrencyRegistry registry = CurrencyRegistry.empty();

    private static ErrorCode codeOf(Runnable action) {
        return assertThrows(DomainException.class, action::run).code();
    }

    @Test
    void emeraldAlwaysExistsAndIsProtected() {
        assertTrue(registry.find(CurrencyId.EMERALD).isPresent());
        assertEquals(ErrorCode.CURRENCY_PROTECTED,
                codeOf(() -> registry.changeRate(CurrencyId.EMERALD, new ExchangeRate(2, 1), alice, DAY)));
        assertEquals(ErrorCode.CURRENCY_ALREADY_EXISTS,
                codeOf(() -> registry.create(CurrencyId.EMERALD, "X", "X", ExchangeRate.PARITY, alice, DAY)));
    }

    @Test
    void createRejectsDuplicateCodeAndSymbol() {
        registry.create(GOLD, "Gold", "G", ExchangeRate.PARITY, alice, DAY);
        assertEquals(ErrorCode.CURRENCY_ALREADY_EXISTS,
                codeOf(() -> registry.create(GOLD, "Gold2", "H", ExchangeRate.PARITY, bob, DAY)));
        assertEquals(ErrorCode.CURRENCY_ALREADY_EXISTS,
                codeOf(() -> registry.create(SILVER, "Silber", "G", ExchangeRate.PARITY, bob, DAY)));
        assertEquals(2, registry.all().size()); // Smaragd + Gold
    }

    @Test
    void createRaisesEvent() {
        registry.create(GOLD, "Gold", "G", ExchangeRate.PARITY, alice, DAY);
        assertEquals(List.of(new CurrencyEvent.Created(GOLD, DAY)), registry.pullEvents());
    }

    @Test
    void onlyGovernorMayChangeRateAndBaseRate() {
        registry.create(GOLD, "Gold", "G", ExchangeRate.PARITY, alice, DAY);
        assertEquals(ErrorCode.NOT_CURRENCY_GOVERNOR,
                codeOf(() -> registry.changeRate(GOLD, new ExchangeRate(2, 1), bob, DAY)));
        assertEquals(ErrorCode.NOT_CURRENCY_GOVERNOR, codeOf(() -> registry.setBaseRate(GOLD, 5, bob, DAY)));
        registry.changeRate(GOLD, new ExchangeRate(2, 1), alice, DAY);
        registry.setBaseRate(GOLD, 5, alice, DAY);
        assertEquals(5, registry.require(GOLD).baseRatePercent());
    }

    @Test
    void chairReplacesFounderAsGovernor() {
        registry.create(GOLD, "Gold", "G", ExchangeRate.PARITY, alice, DAY);
        registry.appointChair(GOLD, bob, alice, DAY);
        assertEquals(ErrorCode.NOT_CURRENCY_GOVERNOR, codeOf(() -> registry.setBaseRate(GOLD, 3, alice, DAY)));
        registry.setBaseRate(GOLD, 3, bob, DAY);
    }

    @Test
    void baseRateOutOfRangeIsRejected() {
        registry.create(GOLD, "Gold", "G", ExchangeRate.PARITY, alice, DAY);
        assertThrows(IllegalArgumentException.class, () -> registry.setBaseRate(GOLD, -1, alice, DAY));
        assertThrows(IllegalArgumentException.class, () -> registry.setBaseRate(GOLD, 101, alice, DAY));
        assertEquals(0, registry.require(GOLD).baseRatePercent());
    }

    @Test
    void convertsViaEmeraldAndRoundsDown() {
        registry.create(GOLD, "Gold", "G", new ExchangeRate(1, 10), alice, DAY); // 1 Gold = 10 Smaragd
        registry.create(SILVER, "Silber", "S", new ExchangeRate(3, 1), alice, DAY); // 3 Silber = 1 Smaragd
        assertEquals(Money.of(100, CurrencyId.EMERALD), registry.convert(Money.of(10, GOLD), CurrencyId.EMERALD));
        assertEquals(Money.of(30, SILVER), registry.convert(Money.of(1, GOLD), SILVER));
        assertEquals(Money.of(0, GOLD), registry.convert(Money.of(2, SILVER), GOLD)); // abgerundet
    }

    @Test
    void convertRejectsNegativeUnknownAndOverflow() {
        registry.create(GOLD, "Gold", "G", new ExchangeRate(1, 10), alice, DAY);
        assertEquals(ErrorCode.NEGATIVE_CONVERSION_AMOUNT,
                codeOf(() -> registry.convert(Money.of(-1, GOLD), CurrencyId.EMERALD)));
        assertEquals(ErrorCode.UNKNOWN_CURRENCY,
                codeOf(() -> registry.convert(Money.of(1, GOLD), SILVER)));
        assertThrows(ArithmeticException.class, () -> registry.convert(Money.of(Long.MAX_VALUE, GOLD), CurrencyId.EMERALD));
    }

    @Test
    void restoreKeepsCurrenciesButNeverOverwritesEmerald() {
        registry.create(GOLD, "Gold", "G", ExchangeRate.PARITY, alice, DAY);
        Currency fakeEmerald = new Currency(CurrencyId.EMERALD, "Fake", "F", new ExchangeRate(5, 1),
                java.util.Optional.empty(), java.util.Optional.empty(), 0, java.util.Optional.empty());
        CurrencyRegistry restored = CurrencyRegistry.restore(List.of(registry.require(GOLD), fakeEmerald));
        assertEquals("Smaragd", restored.require(CurrencyId.EMERALD).name());
        assertEquals("Gold", restored.require(GOLD).name());
    }

    @Test
    void itemCanBeSetByGovernorOnlyAndIsValidated() {
        registry.create(GOLD, "Gold", "G", ExchangeRate.PARITY, alice, "minecraft:gold_ingot", DAY);
        assertEquals("minecraft:gold_ingot", registry.require(GOLD).itemId().orElseThrow());
        assertEquals(ErrorCode.NOT_CURRENCY_GOVERNOR,
                codeOf(() -> registry.setItem(GOLD, "minecraft:diamond", bob, DAY)));
        assertThrows(IllegalArgumentException.class, () -> registry.setItem(GOLD, "kein item!", alice, DAY));
        registry.setItem(GOLD, "minecraft:diamond", alice, DAY);
        assertEquals("minecraft:diamond", registry.require(GOLD).itemId().orElseThrow());
    }
}
