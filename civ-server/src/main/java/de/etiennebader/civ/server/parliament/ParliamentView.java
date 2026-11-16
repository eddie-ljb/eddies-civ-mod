package de.etiennebader.civ.server.parliament;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.office.ElectionView;
import de.etiennebader.civ.server.vote.ProposalView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein Parlament, wie Menü, Chat und Webseite es zeigen.
 *
 * @param seatsByLaw Sitze, die das Recht vorsieht ({@code parliament_seats}); 0 heißt kein Parlament
 * @param factions   Fraktionen, die größte zuerst; leer, solange kein Parlament gewählt ist
 * @param president  Name des Präsidiums; leer ohne Parlament
 * @param electedOn  Tag der Wahl
 * @param until      letzter Tag der Wahlperiode; leer, wenn unbefristet
 * @param election   die laufende Parlamentswahl
 * @param agenda     offene Anträge, über die das Parlament abstimmt
 * @param member     ob der Betrachter einen Sitz hat
 * @param citizen    ob der Betrachter Bürger ist und kandidieren darf
 */
public record ParliamentView(PolityId polity, String name, long seatsByLaw, List<Faction> factions,
                             Optional<String> president, Optional<Long> electedOn, Optional<Long> until,
                             Optional<ElectionView> election, List<ProposalView> agenda, boolean member,
                             boolean citizen) {

    /**
     * Eine Fraktion.
     *
     * @param party  die Partei; leer für Fraktionslose
     * @param name   Name der Partei; leer für Fraktionslose
     * @param emblem Item-ID des Wappens
     */
    public record Faction(Optional<PolityId> party, Optional<String> name, String emblem, List<Member> members) {

        public Faction {
            Objects.requireNonNull(party, "party");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(emblem, "emblem");
            members = List.copyOf(members);
        }
    }

    /**
     * Ein Abgeordneter.
     *
     * @param votes     persönliche Stimmen bei der Wahl (100 = eine Bürgerstimme)
     * @param villager  ob es ein Dorfbewohner ist
     * @param president ob er das Präsidium hat
     */
    public record Member(String name, long votes, boolean villager, boolean president) {

        public Member {
            Objects.requireNonNull(name, "name");
        }
    }

    public ParliamentView {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(name, "name");
        factions = List.copyOf(factions);
        Objects.requireNonNull(president, "president");
        Objects.requireNonNull(electedOn, "electedOn");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(election, "election");
        agenda = List.copyOf(agenda);
    }

    /** Ob ein Parlament gewählt ist. */
    public boolean seated() {
        return !factions.isEmpty();
    }

    public int seats() {
        return factions.stream().mapToInt(faction -> faction.members().size()).sum();
    }
}
