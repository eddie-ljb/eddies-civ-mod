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
 * @param statutes  in Kraft befindliche Gesetze in Reihenfolge ihres Erlasses
 * @param proposals offene Abstimmungen in Reihenfolge ihrer Eröffnung
 * @param offices   besetzte Ämter
 * @param elections laufende Wahlen in Reihenfolge ihrer Ausrufung
 * @param constitution Verfassung (Entwurf oder ausgerufen)
 * @param parliament   gewähltes Parlament oder {@link Parliament#NONE}
 */
public record PolitySnapshot(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                             CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                             List<EnactedStatute> statutes, List<Proposal> proposals, List<OfficeTerm> offices,
                             List<Election> elections, Constitution constitution, Optional<PartyProfile> party,
                             Parliament parliament, List<ElectionResult> results, List<Promise> promises,
                             List<Poster> posters, List<Street> streets) {

    /** Ohne Straßen. */
    public PolitySnapshot(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                          CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                          List<EnactedStatute> statutes, List<Proposal> proposals, List<OfficeTerm> offices,
                          List<Election> elections, Constitution constitution, Optional<PartyProfile> party,
                          Parliament parliament, List<ElectionResult> results, List<Promise> promises,
                          List<Poster> posters) {
        this(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes, proposals, offices, elections,
                constitution, party, parliament, results, promises, posters, List.of());
    }

    /** Ohne Wahlergebnisse, Versprechen und Plakate. */
    public PolitySnapshot(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                          CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                          List<EnactedStatute> statutes, List<Proposal> proposals, List<OfficeTerm> offices,
                          List<Election> elections, Constitution constitution, Optional<PartyProfile> party,
                          Parliament parliament) {
        this(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes, proposals, offices, elections,
                constitution, party, parliament, List.of(), List.of(), List.of(), List.of());
    }

    /** Ohne Parlament. */
    public PolitySnapshot(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                          CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                          List<EnactedStatute> statutes, List<Proposal> proposals, List<OfficeTerm> offices,
                          List<Election> elections, Constitution constitution, Optional<PartyProfile> party) {
        this(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes, proposals, offices, elections,
                constitution, party, Parliament.NONE);
    }

    /** Ohne Parteiprofil. */
    public PolitySnapshot(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                          CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                          List<EnactedStatute> statutes, List<Proposal> proposals, List<OfficeTerm> offices,
                          List<Election> elections, Constitution constitution) {
        this(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes, proposals, offices, elections,
                constitution, Optional.empty());
    }

    /** Mit Verfassungsentwurf ohne Präambel. */
    public PolitySnapshot(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                          CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                          List<EnactedStatute> statutes, List<Proposal> proposals, List<OfficeTerm> offices,
                          List<Election> elections) {
        this(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes, proposals, offices, elections,
                Constitution.DRAFT);
    }

    /** Mit der Standardebene der Art: {@code gemeinde} für Gemeinden, {@code land} für Bünde. */
    public PolitySnapshot(PolityId id, PolityName name, PolityKind kind, SettlementTier tier, CurrencyId currency,
                          Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                          List<EnactedStatute> statutes, List<Proposal> proposals, List<OfficeTerm> offices,
                          List<Election> elections) {
        this(id, name, kind, kind == PolityKind.TERRITORIAL ? Polity.MUNICIPAL_LEVEL : "land", tier, currency,
                foundedOn, seat, members, statutes, proposals, offices, elections);
    }

    public PolitySnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(foundedOn, "foundedOn");
        Objects.requireNonNull(seat, "seat");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        statutes = List.copyOf(Objects.requireNonNull(statutes, "statutes"));
        proposals = List.copyOf(Objects.requireNonNull(proposals, "proposals"));
        offices = List.copyOf(Objects.requireNonNull(offices, "offices"));
        elections = List.copyOf(Objects.requireNonNull(elections, "elections"));
        Objects.requireNonNull(constitution, "constitution");
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(parliament, "parliament");
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        promises = List.copyOf(Objects.requireNonNull(promises, "promises"));
        posters = List.copyOf(Objects.requireNonNull(posters, "posters"));
        streets = List.copyOf(Objects.requireNonNull(streets, "streets"));
    }

    public static PolitySnapshot of(PolityView polity) {
        Objects.requireNonNull(polity, "polity");
        return new PolitySnapshot(polity.id(), polity.name(), polity.kind(), polity.level(), polity.tier(),
                polity.currency(), polity.foundedOn(), polity.seat(), List.copyOf(polity.members()), polity.laws().inForce(),
                List.copyOf(polity.proposals()), List.copyOf(polity.offices()), List.copyOf(polity.elections()),
                polity.constitution(), polity.partyProfile(), polity.parliament(), polity.results(), polity.promises(),
                polity.posters(), polity.streets());
    }

    /**
     * Baut daraus wieder eine Polity, ohne Events auszulösen.
     *
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben
     */
    public Polity restore() {
        return Polity.restore(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes, proposals,
                offices, elections, constitution, party, parliament, results, promises, posters, streets);
    }
}
