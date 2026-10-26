package de.etiennebader.civ.domain.law;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.StatuteId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Das geltende Recht einer Polity. Entity innerhalb des {@code Polity}-Aggregats;
 * Änderungen laufen ausschließlich über die Polity, die dabei die Events auslöst.
 */
public final class LawBook implements LawView {

    private final List<EnactedStatute> statutes = new ArrayList<>();

    /**
     * Setzt ein Gesetz in Kraft. Bei gleichen Parametern hat das zuletzt erlassene Gesetz Vorrang.
     *
     * @throws StatuteAlreadyInForceException wenn ein Gesetz mit derselben ID bereits gilt
     */
    public void enact(Statute statute, Day enactedOn) {
        Objects.requireNonNull(statute, "statute");
        if (find(statute.id()).isPresent()) {
            throw new StatuteAlreadyInForceException(statute.id());
        }
        statutes.add(new EnactedStatute(statute, enactedOn));
    }

    /**
     * Hebt ein geltendes Gesetz auf. Parameter, die es gesetzt hat, fallen auf ältere
     * Gesetze oder den Standardwert zurück.
     *
     * @throws UnknownStatuteException wenn kein Gesetz mit dieser ID gilt
     */
    public EnactedStatute repeal(StatuteId statuteId) {
        EnactedStatute repealed = find(statuteId).orElseThrow(() -> new UnknownStatuteException(statuteId));
        statutes.remove(repealed);
        return repealed;
    }

    public boolean isInForce(StatuteId statuteId) {
        return find(statuteId).isPresent();
    }

    @Override
    public <T> T valueOf(ParameterKey<T> key) {
        Objects.requireNonNull(key, "key");
        for (int index = statutes.size() - 1; index >= 0; index--) {
            Optional<T> value = statutes.get(index).statute().valueOf(key);
            if (value.isPresent()) {
                return value.get();
            }
        }
        return key.defaultValue();
    }

    @Override
    public List<EnactedStatute> inForce() {
        return List.copyOf(statutes);
    }

    private Optional<EnactedStatute> find(StatuteId statuteId) {
        Objects.requireNonNull(statuteId, "statuteId");
        return statutes.stream()
                .filter(enacted -> enacted.statute().id().equals(statuteId))
                .findFirst();
    }
}
