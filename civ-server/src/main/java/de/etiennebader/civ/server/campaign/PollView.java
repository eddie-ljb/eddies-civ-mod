package de.etiennebader.civ.server.campaign;

import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.command.Message;

import java.util.List;
import java.util.Objects;

/**
 * Umfrage zu einer laufenden Wahl.
 *
 * @param entries   Kandidaten mit Anteil, der stärkste zuerst
 * @param lists     Parteien und Parteilose mit Anteil und prognostizierten Sitzen (nur Parlament)
 * @param sample    wie viele Dorfbewohner gefragt wurden
 * @param undecided wie viele niemanden wählen würden
 */
public record PollView(PolityId polity, String polityName, Office office, Message officeName, long closesOn,
                       List<Entry> entries, List<ListEntry> lists, int sample, int undecided) {

    /** Ein Kandidat in der Umfrage. */
    public record Entry(String name, String party, int answers, long percent) {
    }

    /** Eine Partei bzw. ein Parteiloser in der Umfrage. */
    public record ListEntry(String name, String emblem, int answers, long percent, int seats) {
    }

    public PollView {
        Objects.requireNonNull(polity, "polity");
        entries = List.copyOf(entries);
        lists = List.copyOf(lists);
    }
}
