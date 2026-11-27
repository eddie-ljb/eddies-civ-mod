package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Die Regierungsmehrheit samt Koalitionsvertrag.
 *
 * @param parties   beteiligte Parteien, die beauftragte zuerst
 * @param minority  Minderheitsregierung ohne eigene Mehrheit
 * @param formedOn  Tag der Bildung
 * @param agreement Vorhaben des Koalitionsvertrags
 */
public record Coalition(List<PolityId> parties, boolean minority, Day formedOn, List<Item> agreement) {

    /** Ein Vorhaben: eine Regelung mit Wert (kodiert wie im Gesetzbuch), ob schon umgesetzt. */
    public record Item(String parameter, String encodedValue, boolean done) {
        public Item {
            Objects.requireNonNull(parameter, "parameter");
            Objects.requireNonNull(encodedValue, "encodedValue");
        }
    }

    public Coalition {
        parties = List.copyOf(parties);
        agreement = List.copyOf(agreement);
        if (parties.isEmpty()) {
            throw new IllegalArgumentException("Koalition ohne Partei");
        }
    }

    public PolityId leader() {
        return parties.get(0);
    }

    public boolean includes(PolityId party) {
        return parties.contains(party);
    }

    public Coalition withItem(String parameter, String encodedValue) {
        List<Item> next = new ArrayList<>(agreement);
        next.removeIf(item -> item.parameter().equals(parameter) && !item.done());
        next.add(new Item(parameter, encodedValue, false));
        return new Coalition(parties, minority, formedOn, next);
    }

    public Coalition withDone(int index) {
        List<Item> next = new ArrayList<>(agreement);
        Item item = next.get(index);
        next.set(index, new Item(item.parameter(), item.encodedValue(), true));
        return new Coalition(parties, minority, formedOn, next);
    }
}
