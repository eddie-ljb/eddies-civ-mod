package de.etiennebader.civ.domain.law;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.etiennebader.civ.domain.law.StandardParameters.AMENDMENT_MAJORITY_PERCENT;
import static de.etiennebader.civ.domain.law.StandardParameters.EXPLOSIONS_ALLOWED;
import static de.etiennebader.civ.domain.law.StandardParameters.FEDERAL_LAW_PREVAILS;
import static de.etiennebader.civ.domain.law.StandardParameters.FIRE_SPREAD_ALLOWED;
import static de.etiennebader.civ.domain.law.StandardParameters.LAND_TAX_FREE_BLOCKS;
import static de.etiennebader.civ.domain.law.StandardParameters.LAND_TAX_GRACE_DAYS;
import static de.etiennebader.civ.domain.law.StandardParameters.LAND_TAX_PER_BLOCK;
import static de.etiennebader.civ.domain.law.StandardParameters.LAWS_BY_VOTE;
import static de.etiennebader.civ.domain.law.StandardParameters.MAX_OUTPOSTS;
import static de.etiennebader.civ.domain.law.StandardParameters.OUTSIDERS_MAY_BUILD;
import static de.etiennebader.civ.domain.law.StandardParameters.OUTSIDERS_MAY_INTERACT;
import static de.etiennebader.civ.domain.law.StandardParameters.OUTSIDERS_MAY_OPEN_CONTAINERS;
import static de.etiennebader.civ.domain.law.StandardParameters.SALES_TAX_PERCENT;
import static de.etiennebader.civ.domain.law.StandardParameters.TAX_EMBASSIES;
import static de.etiennebader.civ.domain.law.StandardParameters.TERM_DAYS;
import static de.etiennebader.civ.domain.law.StandardParameters.VILLAGER_VOTE_PERCENT;
import static de.etiennebader.civ.domain.law.StandardParameters.VOTE_DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LawRecognizerTest {

    @Test
    @DisplayName("Verbote und Erlaubnisse: Explosionen, Feuer, Fremde mit fortgeltendem Subjekt und Verneinung")
    void permissions() {
        assertEquals(List.of(v(EXPLOSIONS_ALLOWED, false)), values("Explosionen sind verboten."));
        assertEquals(List.of(v(EXPLOSIONS_ALLOWED, true)), values("TNT ist erlaubt"));
        assertEquals(List.of(v(FIRE_SPREAD_ALLOWED, false)), values("Kein Feuer im Dorf!"));
        assertEquals(List.of(v(OUTSIDERS_MAY_BUILD, false), v(OUTSIDERS_MAY_OPEN_CONTAINERS, false)),
                values("Fremde dürfen weder bauen noch Truhen öffnen."));
        assertEquals(List.of(v(OUTSIDERS_MAY_INTERACT, true), v(OUTSIDERS_MAY_BUILD, false)),
                values("Gäste dürfen Türen benutzen. Besucher dürfen nicht bauen."));
        assertEquals(List.of(v(EXPLOSIONS_ALLOWED, false), v(OUTSIDERS_MAY_BUILD, true)),
                values("Explosions are banned. Outsiders may build"));
    }

    @Test
    @DisplayName("Zahlen: Steuern, Außenposten, Fristen, Wahlrecht, Mehrheiten")
    void numbers() {
        assertEquals(List.of(v(SALES_TAX_PERCENT, 10L)), values("Die Umsatzsteuer beträgt 10 %."));
        assertEquals(List.of(v(LAND_TAX_PER_BLOCK, 5L)), values("Die Grundsteuer beträgt 0,05 Smaragde je Block."));
        assertEquals(List.of(v(LAND_TAX_FREE_BLOCKS, 64L)), values("64 Blöcke je Bürger sind steuerfrei."));
        assertEquals(List.of(v(LAND_TAX_GRACE_DAYS, 7L)), values("Neubürger sind 7 Tage steuerfrei."));
        assertEquals(List.of(v(TAX_EMBASSIES, false)), values("Botschaften sind steuerfrei."));
        assertEquals(List.of(v(MAX_OUTPOSTS, 3)), values("Höchstens 3 Außenposten."));
        assertEquals(List.of(v(MAX_OUTPOSTS, 0)), values("Keine Außenposten."));
        assertEquals(List.of(v(TERM_DAYS, 30L)), values("Die Amtszeit beträgt 30 Tage."));
        assertEquals(List.of(v(VOTE_DAYS, 3L)), values("Abstimmungen dauern 3 Tage."));
        assertEquals(List.of(v(VILLAGER_VOTE_PERCENT, 50L)), values("Dorfbewohner wählen mit 50 %."));
        assertEquals(List.of(v(VILLAGER_VOTE_PERCENT, 0L)), values("Dorfbewohner dürfen nicht wählen."));
        assertEquals(List.of(v(LAWS_BY_VOTE, true)), values("Gesetze werden per Volksabstimmung beschlossen."));
        assertEquals(List.of(v(AMENDMENT_MAJORITY_PERCENT, 75L)),
                values("Die Verfassung ändert sich nur mit drei Vierteln der Stimmen."));
        assertEquals(List.of(v(FEDERAL_LAW_PREVAILS, true)), values("Bundesrecht bricht Gemeinderecht."));
        assertEquals(List.of(v(StandardParameters.FEDERAL_LEVY_PERCENT, 5L)), values("Die Bundesumlage beträgt 5 %."));
        assertEquals(List.of(v(StandardParameters.PARLIAMENT_SEATS, 9L)), values("Das Parlament hat 9 Sitze."));
        assertEquals(List.of(v(StandardParameters.PARLIAMENT_SEATS, 0L)), values("Es gibt kein Parlament."));
        assertEquals(List.of(v(StandardParameters.PARLIAMENT_SEATS, 15L)),
                values("The council has 15 seats."));
        assertEquals(List.of(v(StandardParameters.SESSION_DAYS, 3L)), values("Das Parlament tagt alle 3 Tage."));
        assertEquals(List.of(v(StandardParameters.BALLOT_BOX_REQUIRED, true)),
                values("Gewählt wird nur an der Urne."));
        assertEquals(List.of(v(StandardParameters.BALLOT_BOX_REQUIRED, false)),
                values("Eine Wahlurne ist nicht nötig."));
    }

    @Test
    @DisplayName("Baukasten: PvP, Monster, Tiere")
    void gameRules() {
        assertEquals(List.of(v(StandardParameters.PVP_ALLOWED, false)), values("PvP ist verboten."));
        assertEquals(List.of(v(StandardParameters.HOSTILE_MOBS_ALLOWED, false)), values("Keine Monster in der Stadt!"));
        assertEquals(List.of(v(StandardParameters.OUTSIDERS_MAY_HURT_ANIMALS, false)), values("Tiere sind geschützt."));
        assertEquals(List.of(v(StandardParameters.OUTSIDERS_MAY_HURT_ANIMALS, true)),
                values("Fremde dürfen Tiere jagen."));
    }

    @Test
    @DisplayName("Unerkanntes bleibt Text; Ungültiges wird nicht übernommen; Letzte Fundstelle gilt")
    void rest() {
        LawRecognizer.Recognition recognition = LawRecognizer.recognize(
                "Wer stiehlt, wird verbannt. Explosionen sind erlaubt. Explosionen sind verboten.");
        assertEquals(List.of(v(EXPLOSIONS_ALLOWED, false)), recognition.values());
        assertEquals(List.of("Wer stiehlt", "wird verbannt."), recognition.unrecognized());
        assertTrue(values("Die Umsatzsteuer beträgt 80 %.").isEmpty(), "über 50 % ist ungültig");
    }

    private static List<ParameterValue<?>> values(String text) {
        return LawRecognizer.recognize(text).values();
    }

    private static <T> ParameterValue<?> v(ParameterKey<T> key, T value) {
        return new ParameterValue<>(key, value);
    }
}
