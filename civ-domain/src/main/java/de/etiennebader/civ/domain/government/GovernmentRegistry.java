package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Die Regierungen aller politischen Ebenen (Issue #79). Eine Regierung entsteht beim ersten Zugriff. */
public final class GovernmentRegistry {

    private final Map<PolityId, Government> governments = new LinkedHashMap<>();

    public static GovernmentRegistry empty() {
        return new GovernmentRegistry();
    }

    public static GovernmentRegistry restore(Map<PolityId, Government.Snapshot> snapshots) {
        GovernmentRegistry registry = new GovernmentRegistry();
        snapshots.forEach((id, snapshot) -> registry.governments.put(id, Government.restore(snapshot)));
        return registry;
    }

    public Map<PolityId, Government.Snapshot> snapshot() {
        Map<PolityId, Government.Snapshot> result = new LinkedHashMap<>();
        governments.forEach((id, government) -> result.put(id, government.snapshot()));
        return result;
    }

    /** Die Regierung der Ebene; wird bei Bedarf mit Standardwerten angelegt. */
    public Government of(PolityId polity) {
        return governments.computeIfAbsent(polity, id -> new Government());
    }

    /** Nur vorhandene Regierungen, ohne anzulegen. */
    public Optional<Government> find(PolityId polity) {
        return Optional.ofNullable(governments.get(polity));
    }

    public Map<PolityId, Government> all() {
        return Map.copyOf(governments);
    }

    public void remove(PolityId polity) {
        governments.remove(polity);
    }
}
