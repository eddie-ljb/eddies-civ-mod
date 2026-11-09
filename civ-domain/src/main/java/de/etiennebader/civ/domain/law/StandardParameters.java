package de.etiennebader.civ.domain.law;

import java.util.List;

/**
 * Katalog der Standardparameter, die Gesetze setzen können.
 *
 * <p>In Phase 3 entstehen Parameter zusätzlich datengetrieben aus Datapacks.
 */
public final class StandardParameters {

    /** Grundsteuer in kleinster Währungseinheit pro Block Grundstücksfläche und Tag. */
    public static final ParameterKey<Long> LAND_TAX_PER_BLOCK =
            ParameterKey.constrained("land_tax_per_block", Long.class, 0L, rate -> rate >= 0);

    /** Steuerfreie Grundstücksfläche je Inhaber in Blöcken; erst darüber hinaus wird Grundsteuer fällig. */
    public static final ParameterKey<Long> LAND_TAX_FREE_BLOCKS =
            ParameterKey.constrained("land_tax_free_blocks", Long.class, 0L, blocks -> blocks >= 0);

    /** So viele Tage nach dem Erwerb eines Grundstücks ist es steuerfrei, z. B. für Neubürger. */
    public static final ParameterKey<Long> LAND_TAX_GRACE_DAYS =
            ParameterKey.constrained("land_tax_grace_days", Long.class, 0L, days -> days >= 0);

    /** Ob Botschaften Grundsteuer zahlen. */
    public static final ParameterKey<Boolean> TAX_EMBASSIES =
            ParameterKey.of("tax_embassies", Boolean.class, false);

    /** Wie viele Außenposten eine Polity höchstens beanspruchen darf. */
    public static final ParameterKey<Integer> MAX_OUTPOSTS =
            ParameterKey.constrained("max_outposts", Integer.class, 0, max -> max >= 0);

    /** Ob Nicht-Mitglieder im Gebiet bauen und abbauen dürfen. */
    public static final ParameterKey<Boolean> OUTSIDERS_MAY_BUILD =
            ParameterKey.of("outsiders_may_build", Boolean.class, false);

    /** Ob Nicht-Mitglieder Türen, Tore, Knöpfe, Hebel und Betten benutzen dürfen. */
    public static final ParameterKey<Boolean> OUTSIDERS_MAY_INTERACT =
            ParameterKey.of("outsiders_may_interact", Boolean.class, false);

    /** Ob Nicht-Mitglieder Truhen, Öfen und andere Behälter öffnen dürfen. */
    public static final ParameterKey<Boolean> OUTSIDERS_MAY_OPEN_CONTAINERS =
            ParameterKey.of("outsiders_may_open_containers", Boolean.class, false);

    /** Ob Explosionen im Gemeindegebiet Blöcke zerstören. */
    public static final ParameterKey<Boolean> EXPLOSIONS_ALLOWED =
            ParameterKey.of("explosions_allowed", Boolean.class, false);

    /** Ob Feuer im Gemeindegebiet Blöcke verbrennt. */
    public static final ParameterKey<Boolean> FIRE_SPREAD_ALLOWED =
            ParameterKey.of("fire_spread_allowed", Boolean.class, false);

    /** Ob Rechtsänderungen eine Abstimmung der Bürger brauchen, statt sofort zu gelten. */
    public static final ParameterKey<Boolean> LAWS_BY_VOTE =
            ParameterKey.of("laws_by_vote", Boolean.class, false);

    /** Nach wie vielen Tagen eine Abstimmung ohne klare Mehrheit ausgezählt wird. */
    public static final ParameterKey<Long> VOTE_DAYS =
            ParameterKey.constrained("vote_days", Long.class, 2L, days -> days >= 1);

    /** Prozent jedes Ladenverkaufs im Gemeindegebiet, die an die Gemeindekasse gehen. */
    public static final ParameterKey<Long> SALES_TAX_PERCENT =
            ParameterKey.constrained("sales_tax_percent", Long.class, 0L, percent -> percent >= 0 && percent <= 50);

    /** Alle Standardparameter in fester Reihenfolge. */
    public static List<ParameterKey<?>> all() {
        return List.of(LAND_TAX_PER_BLOCK, LAND_TAX_FREE_BLOCKS, LAND_TAX_GRACE_DAYS, TAX_EMBASSIES, MAX_OUTPOSTS,
                OUTSIDERS_MAY_BUILD, OUTSIDERS_MAY_INTERACT, OUTSIDERS_MAY_OPEN_CONTAINERS, EXPLOSIONS_ALLOWED,
                FIRE_SPREAD_ALLOWED, LAWS_BY_VOTE, VOTE_DAYS, SALES_TAX_PERCENT);
    }

    private StandardParameters() {
    }
}
