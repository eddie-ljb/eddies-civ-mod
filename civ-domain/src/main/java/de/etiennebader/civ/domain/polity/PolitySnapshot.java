package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Unveränderliche Kopie einer Polity zu einem Zeitpunkt, z. B. zum Speichern in einem anderen Thread.
 *
 * @param seat     Standort des Gemeindehauses, falls vorhanden
 * @param members  in Beitrittsreihenfolge
 * @param statutes in Kraft befindliche Gesetze in Reihenfolge ihres Erlasses
 */
public record PolitySnapshot(PolityId id, PolityName name, PolityKind kind, SettlementTier tier, CurrencyId currency,
                             Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                             List<EnactedStatute> statutes) {

    public PolitySnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(foundedOn, "foundedOn");
        Objects.requireNonNull(seat, "seat");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        statutes = List.copyOf(Objects.requireNonNull(statutes, "statutes"));
    }

    public static PolitySnapshot of(PolityView polity) {
        Objects.requireNonNull(polity, "polity");
        return new PolitySnapshot(polity.id(), polity.name(), polity.kind(), polity.tier(), polity.currency(),
                polity.foundedOn(), polity.seat(), List.copyOf(polity.members()), polity.laws().inForce());
    }

    /**
     * Baut daraus wieder eine Polity, ohne Events auszulösen.
     *
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben
     */
    public Polity restore() {
        return Polity.restore(id, name, kind, tier, currency, foundedOn, seat, members, statutes);
    }
}
