package de.etiennebader.civ.server.constitution;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.office.OfficeView;
import de.etiennebader.civ.server.town.LawSetting;
import de.etiennebader.civ.server.vote.ProposalView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Eine Verfassung, wie Menü, Chat und Webseite sie zeigen.
 *
 * @param level            Name der Ebene, übersetzbar ({@code Message}) oder frei
 * @param proclaimedOn     Tag des Ausrufens; leer beim Entwurf
 * @param articles         Verfassungsartikel mit geltendem Wert
 * @param amendmentPercent Mehrheit, die Änderungen der Artikel brauchen, sobald ausgerufen
 * @param offices          Organe: Ämter mit Inhabern und laufenden Wahlen
 * @param amendments       offene Abstimmungen über Verfassungsartikel
 * @param parent           übergeordneter Bund
 * @param constituents     Glieder eines Bundes
 * @param head             ob der Betrachter an der Spitze steht
 * @param member           ob der Betrachter Mitglied ist
 */
public record ConstitutionView(PolityId id, String name, Object level, boolean federation, Optional<Long> proclaimedOn,
                               String preamble, List<LawSetting> articles, long amendmentPercent,
                               List<OfficeView> offices, List<ProposalView> amendments, Optional<Link> parent,
                               List<Link> constituents, boolean head, boolean member) {

    /** Verweis auf eine andere Gemeinde oder einen Bund. */
    public record Link(PolityId id, String name) {

        public Link {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }

    public ConstitutionView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(proclaimedOn, "proclaimedOn");
        Objects.requireNonNull(preamble, "preamble");
        articles = List.copyOf(articles);
        offices = List.copyOf(offices);
        amendments = List.copyOf(amendments);
        Objects.requireNonNull(parent, "parent");
        constituents = List.copyOf(constituents);
    }

    public boolean proclaimed() {
        return proclaimedOn.isPresent();
    }
}
