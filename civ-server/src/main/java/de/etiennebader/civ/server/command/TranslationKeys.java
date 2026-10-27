package de.etiennebader.civ.server.command;

import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.territory.ClaimType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Schlüssel für Begriffe der Domäne, die in Texten als Argument auftauchen, und die Liste aller
 * Schlüssel, die die Sprachdateien enthalten müssen.
 */
public final class TranslationKeys {

    private static final String PREFIX = "eddies-civ-mod.";
    /** Format eines Geldbetrags, z. B. „5 Smaragde“. */
    public static final String MONEY = PREFIX + "money";

    private TranslationKeys() {
    }

    /** Name einer Regelung des Rechts, z. B. „Fremde dürfen bauen“. */
    public static String law(ParameterKey<?> key) {
        return PREFIX + "law." + key.id();
    }

    /** „ja“ oder „nein“. */
    public static String yesNo(boolean value) {
        return PREFIX + "value." + value;
    }

    public static String tier(SettlementTier tier) {
        return PREFIX + "tier." + tier.name().toLowerCase(Locale.ROOT);
    }

    public static String claimType(ClaimType type) {
        return PREFIX + "claim_type." + type.name().toLowerCase(Locale.ROOT);
    }

    public static String currency(CurrencyId currency) {
        return PREFIX + "currency." + currency.code().toLowerCase(Locale.ROOT);
    }

    /** Alle Schlüssel, die jede Sprachdatei übersetzen muss. */
    public static List<String> all() {
        return Stream.of(
                        Arrays.stream(ErrorCode.values()).map(ErrorCode::translationKey),
                        Arrays.stream(MessageKey.values()).map(MessageKey::key),
                        Arrays.stream(SettlementTier.values()).map(TranslationKeys::tier),
                        Arrays.stream(ClaimType.values()).map(TranslationKeys::claimType),
                        StandardParameters.all().stream().map(TranslationKeys::law),
                        Stream.of(yesNo(true), yesNo(false)),
                        Stream.of(currency(CurrencyId.EMERALD), MONEY))
                .flatMap(keys -> keys)
                .sorted()
                .toList();
    }
}
