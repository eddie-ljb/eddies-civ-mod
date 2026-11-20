# Issue #9: Wahlkarte mit Wahlbezirken

## Problem

> Es soll eine Wahlkarte in den Wahlen ergänzt werden, die zeigt, wie in den einzelnen Wahlbezirken, durch die
> Wahlmänner usw. (je nach Wahlsystem) gewählt wurde. Dies soll sowohl Ingame als auch im Web gezeigt werden.
> Wichtig ist, dass wenn ich in einer Gemeinde drei Wahlbezirke definiere, dann sollen auf der Karte auch diese drei
> Bezirke getrennt dargestellt werden. Hierzu soll es auch Filter geben.

## Lösung

Die **Web-Karte** konnte das bereits vor diesem Issue: `index.html` lädt `/api/elections`, füllt daraus die
Auswahl „Ansicht" (`Gebiete` vs. je vergangene Wahl `Wahlkarte: <Gemeinde>, <Amt> (Tag N)`) und färbt jeden Chunk
client-seitig nach dem Sieger seines Wahlbezirks (`chunkColor()`), inklusive Partei-Filter (`eparty`). Das wurde
geprüft und nicht verändert.

Es fehlte die **Ingame-Karte**. Dafür:

- **`ElectionMapColor`** (neu, `civ-server/.../election/`): reine, getestete Logik, die für ein Gebiet seinen
  Wahlbezirk auflöst (`districtOf`, über `CivicView#constituencyFor`, dieselbe Zuordnung wie bei der Auszählung)
  und daraus die Kartenfarbe bestimmt (`colorOf`): Partei des Siegers im Bezirk, sonst der Siegerakteur selbst,
  sonst eine feste, ergebnisunabhängige Bezirkskennung (damit Bezirksgrenzen schon vor einer Auszählung sichtbar
  sind). `latestResult` findet das jeweils letzte Ergebnis einer Polity, optional zu einem bestimmten Amt.
- **`TownService#mapByElection(...)`**: wie die bestehende `map(...)`, aber die Farbgruppe jedes Feldes kommt aus
  `ElectionMapColor` statt aus der Bundeshierarchie (`TerritoryColor`, Issue #8).
- **`MapScreen`** (Ingame): neuer Knopf „Wahlkarte: an/aus" neben dem bestehenden Ebenen-Filter-Knopf. Im
  Wahlkarte-Modus zeigt die Karte die eigene Gemeinde (ermittelt über `municipalityOf`) nach Wahlbezirken gefärbt;
  ohne eigene Gemeinde bleibt die normale Bundes-Einfärbung. Neue Übersetzungsschlüssel `map_election_on/off/hint`
  in `de_de.json`/`en_us.json`.

Bewusst **nicht** verändert: `WebApi`/`index.html`. Ein ursprünglicher Zwischenstand hatte dort zusätzlich einen
serverseitigen `election`/`office`-Parameter für `/api/map` ergänzt (mit derselben `ElectionMapColor`-Logik) — das
wurde wieder entfernt, weil kein Frontend-Code ihn je aufruft: Die Web-Karte berechnet den Wahlbezirks-Sieger
bereits vollständig und korrekt client-seitig aus den Rohdaten von `/api/elections`. Zwei parallele, unabhängige
Implementierungen derselben Regel (eine serverseitig ungenutzt, eine client-seitig aktiv) wären nur eine
Fehlerquelle für künftige Divergenz gewesen.

## Implementierung

- `civ-server/src/main/java/de/etiennebader/civ/server/election/ElectionMapColor.java` (neu)
- `civ-server/src/main/java/de/etiennebader/civ/server/town/TownService.java`: `mapByElection(...)`
- `civ-fabric/src/main/java/de/etiennebader/civ/fabric/screen/MapScreen.java`: `electionMode`-Feld, Knopf,
  `electionIcon()`
- `civ-fabric/src/main/resources/assets/eddies-civ-mod/lang/{de_de,en_us}.json`: `map_election_on/off/hint`

## Tests

- `civ-server/src/test/java/de/etiennebader/civ/server/election/ElectionMapColorTest.java` (neu, 5 Tests):
  Bezirkszuordnung gruppiert Gemeinden desselben Wahlkreises, fällt ohne eigenen Wahlkreis auf die Gemeinde selbst
  zurück; Farbauflösung bevorzugt Partei vor Akteur vor fester Bezirkskennung; `latestResult` wählt das zuletzt
  geschlossene Ergebnis, optional nur zu einem Amt.
- `JAVA_HOME=/opt/jdk25 ./gradlew :civ-domain:test :civ-application:test :civ-server:test :civ-fabric:compileJava`
  → **BUILD SUCCESSFUL**.
- Kein automatisierter Test für das tatsächliche Rendering der Ingame-Karte möglich (braucht einen laufenden
  Minecraft-Client); manuell im Spiel zu prüfen.
