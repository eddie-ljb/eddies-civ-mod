package de.etiennebader.civ.domain.government;

import java.util.Locale;
import java.util.Set;

/**
 * Handlungsbereich eines Ministeriums: bestimmt, welche Regelungen (Parameter) der Minister vorschlagen oder per
 * Verordnung setzen darf.
 */
public enum Portfolio {
    FINANCE(Set.of("land_tax_per_block", "land_tax_free_blocks", "land_tax_grace_days", "tax_embassies",
            "sales_tax_percent", "income_tax_percent", "federal_levy_percent", "claim_cost_per_chunk",
            "build_permit_fee", "office_salary")),
    INTERIOR(Set.of("outsiders_may_build", "outsiders_may_interact", "outsiders_may_open_containers",
            "outsiders_may_hurt_animals", "explosions_allowed", "fire_spread_allowed", "pvp_allowed",
            "hostile_mobs_allowed", "plot_rights_override", "street_required", "build_permit_required",
            "max_tenants_per_plot", "max_outposts", "jury_size", "max_fine", "exile_allowed", "press_freedom",
            "court_fee", "voting_fine", "compulsory_voting", "ballot_box_required", "postal_vote_allowed",
            "full_turnout_required")),
    DEFENCE(Set.of("war_reparations_per_strength_point", "soldier_wage", "officer_wage")),
    ECONOMY(Set.of("conjuncture_stimulus_percent", "bank_lending_allowed", "default_currency")),
    /** Staatsorganisation: darf vorschlagen, aber nie per Verordnung ändern. */
    CHANCELLERY(Set.of("laws_by_vote", "vote_days", "term_days", "election_days", "villager_vote_percent",
            "amendment_majority_percent", "federal_law_prevails", "parliament_seats",
            "parliament_motion_min_open_days", "session_days", "office_voting", "parliament_voting", "ballot_type",
            "threshold_percent", "term_limit", "election_debates", "party_list", "party_list_by_vote")),
    /** Kein gesetzlicher Bereich (z. B. Bildung): nur eigene Entscheidungen und der Etat. */
    FREE(Set.of());

    private final Set<String> parameters;

    Portfolio(Set<String> parameters) {
        this.parameters = parameters;
    }

    public boolean owns(String parameterId) {
        return parameters.contains(parameterId);
    }

    /** Ob der Minister hier per Verordnung entscheiden darf, wenn das Oberhaupt es ihm erlaubt. */
    public boolean decreeable() {
        return this != CHANCELLERY && this != FREE;
    }

    /** Rät den Bereich aus dem Namen des Ministeriums. */
    public static Portfolio guess(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("finanz")) {
            return FINANCE;
        }
        if (n.contains("innen")) {
            return INTERIOR;
        }
        if (n.contains("verteidig") || n.contains("außen") || n.contains("aussen") || n.contains("krieg")) {
            return DEFENCE;
        }
        if (n.contains("wirtschaft") || n.contains("minen")) {
            return ECONOMY;
        }
        if (n.contains("kanzler") || n.contains("präsid") || n.contains("praesid") || n.contains("bürgermeister")
                || n.contains("buergermeister")) {
            return CHANCELLERY;
        }
        return FREE;
    }
}
