package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lesender Zugriff auf eine Polity, z. B. für Abfragen außerhalb der Use Cases.
 */
public interface PolityView {

    PolityId id();

    PolityName name();

    PolityKind kind();

    SettlementTier tier();

    CurrencyId currency();

    Day foundedOn();

    /** Standort des Gemeindehauses; leer bei Polities ohne Sitz. */
    Optional<BlockPosition> seat();

    boolean isMember(ActorRef actor);

    Optional<Membership> membershipOf(ActorRef actor);

    /** Alle Mitgliedschaften in Beitrittsreihenfolge. */
    Collection<Membership> members();

    int memberCount();

    LawView laws();

    PolityRef asActor();

    /** Richtung und Wappen, wenn es eine Partei ist. */
    Optional<PartyProfile> partyProfile();

    /** Verfassung: Entwurf oder ausgerufen, mit Präambel. */
    Constitution constitution();

    /** Schlüssel der Ebene: {@code gemeinde} für Gemeinden, sonst z. B. {@code kreis}, {@code land}, {@code nation}. */
    String level();

    /** Die Gliedkörperschaften eines Bundes in Reihenfolge ihres Beitritts; leer bei Gemeinden. */
    default List<PolityRef> constituents() {
        return members().stream()
                .filter(membership -> membership.type() == MembershipType.CONSTITUENT)
                .map(membership -> (PolityRef) membership.member())
                .toList();
    }

    /** Offene Abstimmungen in Reihenfolge ihrer Eröffnung. */
    Collection<Proposal> proposals();

    /** Die Amtszeit des aktuellen Inhabers; leer, wenn das Amt frei ist. */
    Optional<OfficeTerm> officeTerm(Office office);

    /** Alle besetzten Ämter in der Reihenfolge von {@link Office}. */
    Collection<OfficeTerm> offices();

    /** Das gewählte Parlament; {@link Parliament#NONE}, solange keines gewählt ist. */
    default Parliament parliament() {
        return Parliament.NONE;
    }

    /** Ergebnisse beendeter Wahlen, das neueste zuletzt. */
    default List<ElectionResult> results() {
        return List.of();
    }

    /** Wahlversprechen, das neueste zuletzt. */
    default List<Promise> promises() {
        return List.of();
    }

    /** Straßen im Gebiet einer Gemeinde. */
    default List<Street> streets() {
        return List.of();
    }

    /**
     * Postleitzahl der Gemeinde: fünfstellig, fest aus ihrer Kennung abgeleitet. Bünde und Parteien haben keine, sie
     * gilt aber für Vergleiche ebenso.
     */
    default String postalCode() {
        long bits = id().value().getMostSignificantBits() ^ id().value().getLeastSignificantBits();
        return Long.toString(10_000 + Math.floorMod(bits, 90_000L));
    }

    /** Wahlplakate im Gebiet. */
    default List<Poster> posters() {
        return List.of();
    }

    /** Laufende Wahlen in Reihenfolge ihrer Ausrufung. */
    Collection<Election> elections();

    /** Die laufende Wahl für dieses Amt, falls es eine gibt. */
    Optional<Election> electionFor(Office office);

    /**
     * Wer die Gemeinde leitet: der Inhaber des Amts {@link Office#MAYOR}; ist es frei, der dienstälteste Bürger, der kein
     * Dorfbewohner ist. So hat auch eine Gemeinde ohne Wahl immer einen Bürgermeister, anfangs den Gründer.
     */
    default Optional<ActorRef> mayor() {
        Optional<ActorRef> elected = officeTerm(Office.MAYOR).map(OfficeTerm::holder);
        if (elected.isPresent()) {
            return elected;
        }
        return members().stream()
                .filter(membership -> membership.type() == MembershipType.CITIZEN)
                .map(Membership::member)
                .filter(member -> !(member instanceof PersonRef))
                .findFirst();
    }

    /** Ob der Akteur dieses Amt innehat; für {@link Office#MAYOR} gilt {@link #mayor()}. */
    default boolean holds(ActorRef actor, Office office) {
        Objects.requireNonNull(actor, "actor");
        if (office == Office.MAYOR) {
            return mayor().filter(actor::equals).isPresent();
        }
        return officeTerm(office).map(OfficeTerm::holder).filter(actor::equals).isPresent();
    }

    /**
     * Wie viele Mitglieder für die Größe des Gebiets zählen: Spieler und Gemeinden voll, Personen wie Dorfbewohner
     * zu einem Viertel (abgerundet). So lässt sich Gebiet nicht allein durch Dorfbewohner-Zucht vergrößern.
     */
    default int claimWeight() {
        int persons = (int) members().stream().filter(membership -> membership.member() instanceof PersonRef).count();
        return members().size() - persons + persons / 4;
    }
}
