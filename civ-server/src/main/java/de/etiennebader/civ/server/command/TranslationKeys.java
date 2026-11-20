package de.etiennebader.civ.server.command;

import de.etiennebader.civ.application.population.Satisfaction;
import de.etiennebader.civ.domain.conjuncture.BusinessPhase;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.population.Trait;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.server.federation.Level;
import de.etiennebader.civ.server.federation.Levels;

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
    /** Zahl mit zwei Nachkommastellen, z. B. „12,05“; das Dezimalzeichen hängt von der Sprache ab. */
    public static final String MONEY_AMOUNT = PREFIX + "money_amount";
    /** Spitze eines Bundes, statt „Bürgermeister“. */
    public static final String FEDERATION_HEAD = PREFIX + "office.head";
    /** Gegenstand eines Antrags über einen Paragraphen: „§ Titel“. */
    public static final String MOTION_PARAGRAPH = PREFIX + "motion.paragraph";
    /** Vorschlag „erlassen (n Regelungen)“. */
    public static final String MOTION_ENACT = PREFIX + "motion.enact";
    /** Vorschlag „aufheben“. */
    public static final String MOTION_REPEAL = PREFIX + "motion.repeal";

    private TranslationKeys() {
    }

    /** Name einer Regelung des Rechts, z. B. „Fremde dürfen bauen“. */
    /** Anzeigename einer Auswahl wie {@code sainte_lague}. */
    public static String lawOption(String option) {
        return PREFIX + "law_option." + option;
    }

    /** Ob dieser Text ein Wert eines Auswahl-Parameters ist. */
    public static boolean isLawOption(String value) {
        return de.etiennebader.civ.domain.law.StandardParameters.all().stream()
                .anyMatch(key -> key.options().contains(value));
    }

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

    public static String office(Office office) {
        return PREFIX + "office." + office.name().toLowerCase(Locale.ROOT);
    }

    public static String platform(Platform platform) {
        return PREFIX + "platform." + platform.name().toLowerCase(Locale.ROOT);
    }

    public static String trait(Trait trait) {
        return PREFIX + "trait." + trait.name().toLowerCase(Locale.ROOT);
    }

    /** Was eine Eigenschaft bedeutet, als Erklärung im Menü. */
    public static String traitHint(Trait trait) {
        return PREFIX + "trait_hint." + trait.name().toLowerCase(Locale.ROOT);
    }

    /** Was ein Wahlprogramm verspricht. */
    public static String platformDescription(Platform platform) {
        return PREFIX + "platform_description." + platform.name().toLowerCase(Locale.ROOT);
    }

    public static String moodFactor(Satisfaction.Factor factor) {
        return PREFIX + "mood_factor." + factor.name().toLowerCase(Locale.ROOT);
    }

    /** Woraus sich eine Einflussgröße der Zufriedenheit ergibt. */
    public static String moodFactorHint(Satisfaction.Factor factor) {
        return PREFIX + "mood_factor_hint." + factor.name().toLowerCase(Locale.ROOT);
    }

    public static String plotType(PlotType type) {
        return PREFIX + "plot_type." + type.name().toLowerCase(Locale.ROOT);
    }

    /** Name einer Konjunkturphase, z. B. im {@code /civ konjunktur}-Befehl. */
    public static String conjuncturePhase(BusinessPhase phase) {
        return PREFIX + "conjuncture_phase." + phase.name().toLowerCase(Locale.ROOT);
    }

    /** Alle Schlüssel, die jede Sprachdatei übersetzen muss. */
    public static List<String> all() {
        return Stream.of(
                        Arrays.stream(ErrorCode.values()).map(ErrorCode::translationKey),
                        Arrays.stream(MessageKey.values()).map(MessageKey::key),
                        Arrays.stream(GuiKeys.values()).map(GuiKeys::key),
                        Arrays.stream(SettlementTier.values()).map(TranslationKeys::tier),
                        Arrays.stream(ClaimType.values()).map(TranslationKeys::claimType),
                        Arrays.stream(PlotType.values()).map(TranslationKeys::plotType),
                        Arrays.stream(Office.values()).map(TranslationKeys::office),
                        Arrays.stream(Platform.values()).map(TranslationKeys::platform),
                        Arrays.stream(Trait.values()).map(TranslationKeys::trait),
                        Arrays.stream(Trait.values()).map(TranslationKeys::traitHint),
                        Arrays.stream(Platform.values()).map(TranslationKeys::platformDescription),
                        Arrays.stream(Satisfaction.Factor.values()).map(TranslationKeys::moodFactor),
                        Arrays.stream(Satisfaction.Factor.values()).map(TranslationKeys::moodFactorHint),
                        Arrays.stream(BusinessPhase.values()).map(TranslationKeys::conjuncturePhase),
                        StandardParameters.all().stream().map(TranslationKeys::law),
                        Levels.DEFAULT.all().stream().map(level -> Level.translationKey(level.id())),
                        Stream.of(yesNo(true), yesNo(false)),
                        Stream.of(currency(CurrencyId.EMERALD), MONEY, MONEY_AMOUNT, FEDERATION_HEAD, MOTION_PARAGRAPH,
                                MOTION_ENACT, MOTION_REPEAL))
                .flatMap(keys -> keys)
                .sorted()
                .toList();
    }
}
