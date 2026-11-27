# Polizei und Militär

Seit Version 1.1.0 (Issue #84) haben Gemeinden eine Polizei mit Dienstgraden, Festnahmen, Handschellen und Zellen. Das Militär hat eine Befehlskette bis zum General, und die Webseite zeigt Kriege auf einer Karte.

## Polizei
**Dienstgrade:** Kadett → Polizist → Wachtmeister → Kommissar. Höhere Ränge befehligen niedrigere.

| Befehl | Wirkung |
|---|---|
| `/civ polizei status` | Beamte, freie Zellen und offene Anzeigen der eigenen Gemeinde |
| `/civ polizei dienen` / `kuendigen` | als Kadett eintreten (der Bürgermeister wird Kommissar) / austreten |
| `/civ polizei ernennen <Spieler> kadett\|polizist\|wachtmeister\|kommissar` | ernennen, befördern, degradieren (Bürgermeister oder Vorgesetzter) |
| `/civ polizei entlassen <Spieler>` | aus dem Dienst entlassen |
| `/civ polizei zellen <Anzahl>` | Zellen bauen lassen, 0–20 (nur Bürgermeister) |
| `/civ polizei anzeigen [<Name> diebstahl\|hausfriedensbruch\|koerperverletzung\|mord]` | Straftat anzeigen / offene Anzeigen ansehen |
| `/civ polizei festnehmen\|fesseln\|loesen\|freilassen <Name>` | festnehmen (Zelle), fesseln, Handschellen lösen, aus der Zelle entlassen |

- **Befugnisse:** Fesseln und Festnehmen ab Polizist, Freilassen ab Wachtmeister oder als Bürgermeister. Vorgesetzte und der Bürgermeister sind nicht festnehmbar. Ohne Anzeige darf nur festnehmen, wer das Gesetz `police_arrest_without_report` erlaubt.
- **Handschellen:** Das Item „Handschellen“ (Rezept: Eisennuggets und -barren, `NIN` / `N N`) fesselt mit Rechtsklick Spieler und Dorfbewohner, ein zweiter Rechtsklick löst sie. Gefesselte und Inhaftierte können nichts abbauen, setzen, benutzen oder schlagen. Gefesselte Dorfbewohner stehen still.
- **Zellen und Haft:** Festgenommene kommen in Untersuchungshaft, solange eine Zelle frei ist; sonst bleiben sie nur gefesselt.
- **Gericht:** Jede Festnahme eröffnet ein Strafverfahren. Die Gemeinde klagt (gebührenfrei) und legt den Polizeibericht als Beweis bei. Der Richter setzt die Haft mit `urteil <Nr> schuldig haft <Tage> | Begründung` fest (bis 60 Tage), sonst gilt die Regelhaft der Tat. Nach Rechtskraft sitzt der Täter ab oder kommt frei. Bei Berufung wandert die Verknüpfung auf das neue Verfahren.
- **Dorfbewohner:** Die Gemeinde stellt Dorfbewohner ein und befördert sie selbst (ein Polizist je acht Einwohner, höchstens 12). Dorfbewohner haben einen Kriminalitätswert aus Bildung, Ordnung, Stärke und Verbundenheit und begehen selten Straftaten. Mitbewohner sehen sie, zeigen sie an und sagen später als Zeugen aus; die Streife nimmt Täter fest.
- **Gesetze:** `police_wage` (Sold je Rang), `police_patrol` (Streife an/aus), `police_arrest_without_report`.

## Richter und Staatsanwälte per Verfassung
Das Gesetz `court_staffing` bestimmt, wie das Gericht besetzt wird: `head` (Oberhaupt ernennt, Standard), `government` (jeder Amtsträger ernennt), `automatic` (die Gemeinde bestimmt den gebildetsten Dorfbewohner) oder `election` (Dorfbewohner wählen). Bei `automatic` und `election` ist Ernennen gesperrt; freie Stellen füllt die Gemeinde täglich. Spieler kandidieren bei `election` freiwillig mit `/civ gericht kandidieren` und ziehen sich mit `zurueckziehen` zurück.

## Militär
**Ränge:** Soldat / Ausspäher → Unteroffizier → Offizier → Hauptmann → General (nur einer je Garnison).

- `/civ krieg befoerdern <Spieler> soldat|unteroffizier|offizier|hauptmann|general`: der Bürgermeister vergibt jeden Rang, sonst befördert nur ein Vorgesetzter und nur unter seinem eigenen Rang.
- Hauptmann und General planen Feldzüge für die eigene Gemeinde; Kriegserklärung, Gefecht und Frieden bleiben beim Bürgermeister.
- Dorfbewohner werden automatisch befördert (ab 10 Mann ein General, je 8 ein Hauptmann, je 6 ein Offizier, je 4 ein Unteroffizier).

## Webseite und Zeitungen
Die Seite „Krieg“ zeigt eine Karte mit beiden Gebieten und dem rot umrandeten Frontabschnitt, die Live-Stände der geplanten Feldzüge (alle 10 Sekunden aktualisiert), das Schlachtenbuch je Krieg und – angemeldet – den **Feldzugplaner** für die eigene Gemeinde. Kriegserklärung, Schlachten, Frieden und Sieg erscheinen in jeder Zeitung mit Dorfbewohner-Redakteur.

Siehe auch [Justiz](Spieler-Justiz.md), [Recht und Verfassung](Spieler-Recht-und-Verfassung.md) und die [Befehlsübersicht](Befehlsuebersicht.md).
