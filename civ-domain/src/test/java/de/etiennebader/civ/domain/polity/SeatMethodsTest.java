package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.law.LawRecognizer;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeatMethodsTest {

    private static Map<String, Long> votes() {
        Map<String, Long> votes = new LinkedHashMap<>();
        votes.put("A", 6_000L);
        votes.put("B", 2_500L);
        votes.put("C", 1_000L);
        votes.put("D", 500L);
        return votes;
    }

    @Test
    @DisplayName("D'Hondt bevorzugt große Listen, Sainte-Laguë und Hare-Niemeyer kleinere")
    void methods() {
        assertEquals(Map.of("A", 6, "B", 3, "C", 1, "D", 0), Map.copyOf(SeatAllocation.sainteLague(votes(), Map.of(), 10)));
        assertEquals(Map.of("A", 7, "B", 2, "C", 1, "D", 0), Map.copyOf(SeatAllocation.dhondt(votes(), Map.of(), 10)));
        assertEquals(Map.of("A", 6, "B", 3, "C", 1, "D", 0),
                Map.copyOf(SeatAllocation.hareNiemeyer(votes(), Map.of(), 10)));
        assertEquals(Map.of("A", 7, "B", 3, "C", 0, "D", 0),
                Map.copyOf(SeatAllocation.allocate("sainte_lague", votes(), Map.of(), 10, 15)),
                "C hat 10 % und scheitert an 15 %");
        assertEquals(Map.of("A", 6, "B", 2, "C", 1, "D", 1),
                Map.copyOf(SeatAllocation.hareNiemeyer(votes(), Map.of("B", 2), 10)),
                "B hat nur zwei Kandidaten, der Restsitz geht an D");
    }

    @Test
    @DisplayName("Gesetzestexte zu Wahlverfahren, Sperrklausel, Amtszeiten, Wahlpflicht, Presse und Gericht")
    void recognizer() {
        assertEquals(List.of(ParameterValue.of(StandardParameters.OFFICE_VOTING, "runoff")),
                LawRecognizer.recognize("Der Bürgermeister braucht die absolute Mehrheit, sonst gibt es eine Stichwahl.")
                        .values());
        assertEquals(List.of(ParameterValue.of(StandardParameters.PARLIAMENT_VOTING, "dhondt")),
                LawRecognizer.recognize("Die Sitze werden nach D'Hondt verteilt.").values());
        assertEquals(List.of(ParameterValue.of(StandardParameters.PARLIAMENT_VOTING, "mixed")),
                LawRecognizer.recognize("Das Parlament wird in personalisierter Verhältniswahl gewählt.").values());
        assertEquals(List.of(ParameterValue.of(StandardParameters.BALLOT_TYPE, "list")),
                LawRecognizer.recognize("Es gilt die Listenwahl.").values());
        assertEquals(List.of(ParameterValue.of(StandardParameters.THRESHOLD_PERCENT, 5L)),
                LawRecognizer.recognize("Die Sperrklausel beträgt 5 %.").values());
        assertEquals(List.of(ParameterValue.of(StandardParameters.TERM_LIMIT, 2L)),
                LawRecognizer.recognize("Niemand darf mehr als 2 Amtszeiten in Folge regieren.").values());
        assertEquals(List.of(ParameterValue.of(StandardParameters.COMPULSORY_VOTING, true)),
                LawRecognizer.recognize("Es gilt Wahlpflicht.").values());
        assertEquals(List.of(ParameterValue.of(StandardParameters.PRESS_FREEDOM, false)),
                LawRecognizer.recognize("Zensur ist erlaubt.").values());
        assertEquals(List.of(ParameterValue.of(StandardParameters.JURY_SIZE, 7L)),
                LawRecognizer.recognize("Über Straftaten entscheiden 7 Geschworene.").values());
    }
}
