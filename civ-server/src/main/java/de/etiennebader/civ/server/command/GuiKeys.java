package de.etiennebader.civ.server.command;

import java.util.Locale;

/**
 * Beschriftungen der Menüs. Der Schlüssel in den Sprachdateien ist
 * {@code eddies-civ-mod.gui.<name in Kleinbuchstaben>}.
 */
public enum GuiKeys {

    // Titel
    TOWN_TITLE,
    BANK_TITLE,
    MARKET_TITLE,
    MAP_TITLE,
    LAWS_TITLE,
    MEMBERS_TITLE,
    // Gemeindehaus
    OVERVIEW,
    OVERVIEW_TIER,
    OVERVIEW_FOUNDED,
    OVERVIEW_MAYOR,
    OVERVIEW_MEMBERS,
    OVERVIEW_TREASURY,
    OVERVIEW_VILLAGERS,
    OVERVIEW_ROLE,
    ROLE_OUTSIDER,
    ROLE_RESIDENT,
    ROLE_CITIZEN,
    ROLE_MAYOR,
    MEMBERS,
    MEMBERS_HINT,
    LAWS,
    LAWS_HINT,
    TREASURY,
    TREASURY_PAY_IN_HINT,
    TREASURY_TAKE_HINT,
    MAP,
    MAP_HINT,
    CLAIM_HERE,
    CLAIM_HERE_HINT,
    JOIN,
    JOIN_HINT,
    LEAVE,
    LEAVE_HINT,
    BACK,
    // Recht
    LAW_VALUE,
    LAW_ID,
    LAW_TOGGLE_HINT,
    LAW_ADJUST_HINT,
    LAW_CITIZENS_ONLY,
    // Karte
    MAP_WILDERNESS,
    MAP_OWN,
    MAP_FOREIGN,
    MAP_COORDS,
    MAP_HERE,
    MAP_CLAIM_HINT,
    MAP_OUTPOST_HINT,
    // Bank
    BANK_BALANCE,
    BANK_DEPOSIT,
    BANK_DEPOSIT_ALL,
    BANK_WITHDRAW,
    BANK_WITHDRAW_HINT,
    BANK_TRANSFER_HINT,
    // Markt
    MARKET_HELP,
    MARKET_HELP_SELL,
    MARKET_HELP_SELL_ONE,
    MARKET_HELP_BUY,
    MARKET_LEVEL,
    MARKET_BUY_PRICE,
    MARKET_SELL_PRICE,
    MARKET_STOCK,
    MARKET_EMPTY,
    PAGE_PREVIOUS,
    PAGE_NEXT,
    PAGE,
    // Grundstücke
    PLOTS,
    PLOTS_HINT,
    PLOTS_TITLE,
    PLOTS_EMPTY,
    PLOT_ENTRY,
    PLOT_ENTRY_COORDS,
    PLOT_ENTRY_HOLDER,
    PLOT_ENTRY_PRICE,
    PLOT_ENTRY_NOT_OFFERED,
    PLOT_BUY_HINT,
    PLOT_HOW_TO,
    // Abstimmungen
    VOTES,
    VOTES_HINT,
    VOTES_TITLE,
    VOTES_EMPTY,
    VOTE_ENTRY,
    VOTE_TALLY,
    VOTE_PROPOSER,
    VOTE_DEADLINE,
    VOTE_OWN_YES,
    VOTE_OWN_NO,
    VOTE_HINT,
    DISSOLVE,
    DISSOLVE_HINT,
    DISSOLVE_CONFIRM,
    SHOP_TITLE,
    SHOP_OWNER,
    SHOP_PRICE,
    SHOP_STOCK,
    SHOP_BUY,
    SHOP_BUY_PRICE,
    // Ämter und Wahlen
    OFFICES,
    OFFICES_HINT,
    OFFICES_TITLE,
    OFFICE_HOLDER,
    OFFICE_ACTING,
    OFFICE_VACANT_LINE,
    OFFICE_UNTIL,
    OFFICE_ELECTION,
    OFFICE_BALLOTS,
    OFFICE_OWN_CANDIDACY,
    OFFICE_STAND_HINT,
    OFFICE_CALL_HINT,
    OFFICE_NO_CANDIDATES,
    OFFICE_CANDIDATE_FOR,
    OFFICE_OWN_CHOICE,
    OFFICE_VOTE_HINT,
    OFFICE_RULES,
    OFFICE_RULES_TERM,
    OFFICE_RULES_TERM_OPEN,
    OFFICE_RULES_DURATION,
    OFFICE_RULES_VILLAGERS,
    OFFICE_RULES_NO_VILLAGERS,
    OFFICE_RULES_HINT,
    OFFICE_PLATFORM,
    PLATFORM_TITLE,
    PLATFORM_HINT,
    // Stimmung
    OVERVIEW_MOOD,
    OVERVIEW_MOOD_NONE,
    OVERVIEW_MOOD_HINT,
    MOOD_TITLE,
    MOOD_EMPTY,
    MOOD_SUMMARY,
    MOOD_COUNTS,
    MOOD_PRODUCTIVITY,
    MOOD_POINTS,
    MOOD_TRAIT_COUNT,
    MOOD_AT_RISK;

    private static final String PREFIX = "eddies-civ-mod.gui.";

    public String key() {
        return PREFIX + name().toLowerCase(Locale.ROOT);
    }

    /** Die Beschriftung mit Platzhalterwerten. */
    public Message text(Object... args) {
        return Message.of(key(), args);
    }
}
