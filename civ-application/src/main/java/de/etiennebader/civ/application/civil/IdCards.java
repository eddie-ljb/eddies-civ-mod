package de.etiennebader.civ.application.civil;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.population.Gender;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;
import java.util.Optional;

/**
 * Personalausweise: Name, Anschrift und Staatsbürgerschaft eines Akteurs (Spieler oder Villager), siehe #20.
 *
 * <p>Ein Ausweis ist eine Momentaufnahme zum Ausstellungszeitpunkt, kein live mitlaufendes Dokument: wie ein
 * echter Ausweis zeigt er die Daten von damals, auch wenn sich Anschrift oder Staatsbürgerschaft später ändern.
 */
public final class IdCards {

    private final WorldQueries queries;
    private final Addresses addresses;

    public IdCards(WorldQueries queries) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.addresses = new Addresses(queries);
    }

    /**
     * @param holderName   Name zum Ausstellungszeitpunkt
     * @param gender       Geschlecht, falls angegeben (Spieler wählen es, Dorfbewohner haben eines)
     * @param address      Anschrift, falls ein fester Wohnsitz bestand
     * @param citizenOf    Gemeinde, deren Bürger der Inhaber zum Ausstellungszeitpunkt war
     * @param issuedOn     Ausstellungstag
     */
    public record Data(String holderName, Optional<Gender> gender, Optional<String> address, Optional<String> citizenOf, Day issuedOn) {

        public Data {
            Objects.requireNonNull(holderName, "holderName");
            Objects.requireNonNull(gender, "gender");
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(citizenOf, "citizenOf");
            Objects.requireNonNull(issuedOn, "issuedOn");
        }
    }

    /** Stellt die Momentaufnahme für {@code holder} zum heutigen Tag ({@code today}) aus. */
    public Data issue(ActorRef holder, String holderName, Optional<Gender> gender, Day today) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(holderName, "holderName");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(today, "today");
        Optional<String> address = addresses.residenceOf(holder).map(Address::line);
        Optional<String> citizenOf = citizenshipOf(holder);
        return new Data(holderName, gender, address, citizenOf, today);
    }

    /**
     * Ob die Angaben des Ausweises noch mit dem Register übereinstimmen: Name, Anschrift und Staatsbürgerschaft
     * (der Ausstellungstag zählt nicht). Nach Umzug, Einbürgerung oder Umbenennung ist der Ausweis veraltet.
     */
    public boolean isCurrent(ActorRef holder, String currentName, Optional<Gender> currentGender, Data card) {
        Data now = issue(holder, currentName, currentGender, card.issuedOn());
        return now.equals(card);
    }

    private Optional<String> citizenshipOf(ActorRef holder) {
        for (PolityView polity : queries.polities()) {
            if (polity.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            boolean citizen = polity.membershipOf(holder)
                    .map(membership -> membership.type() == MembershipType.CITIZEN)
                    .orElse(false);
            if (citizen) {
                return Optional.of(polity.name().value());
            }
        }
        return Optional.empty();
    }
}
