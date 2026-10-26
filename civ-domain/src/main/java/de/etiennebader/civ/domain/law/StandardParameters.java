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

    /** Ob Botschaften Grundsteuer zahlen. */
    public static final ParameterKey<Boolean> TAX_EMBASSIES =
            ParameterKey.of("tax_embassies", Boolean.class, false);

    /** Wie viele Außenposten eine Polity höchstens beanspruchen darf. */
    public static final ParameterKey<Integer> MAX_OUTPOSTS =
            ParameterKey.constrained("max_outposts", Integer.class, 0, max -> max >= 0);

    /** Alle Standardparameter in fester Reihenfolge. */
    public static List<ParameterKey<?>> all() {
        return List.of(LAND_TAX_PER_BLOCK, TAX_EMBASSIES, MAX_OUTPOSTS);
    }

    private StandardParameters() {
    }
}
