# Nachbesserung Wahlkarte und Wahlen-Tab (Feedback zu Issue #9)

## Problem

Nach dem Merge von Issue #9 kam Feedback vom Owner:

1. Die Web-Karte zeigte bei Gemeinden mit mehreren Wahlbezirken **keine** getrennten Bezirke mit Grenzen – obwohl
   genau das der Kernwunsch von Issue #9 war ("wenn ich in einer Gemeinde drei Wahlbezirke definiere, dann sollen
   auf der Karte auch diese drei Bezirke getrennt dargestellt werden").
2. Es sollte einen **eigenen, expliziten Filter** mit klaren Grenzen für die Wahlbezirks-Ansicht geben.
3. Es sollte für jeden Bezirk die **volle Kandidaten-Rangfolge** sichtbar sein, nicht nur der Gewinner.
4. Der Wahlen-Tab wurde als **unübersichtlich** empfunden.

## Ursache

Die serverseitige Logik zur Bezirksauflösung (`ElectionMapColor`, genutzt von der Ingame-Karte über
`TownService#mapByElection`) war von Anfang an korrekt und je-Chunk-genau (ein Wahlbezirk kann aus `Area`-Rechtecken
bestehen, die nur einen Teil einer Gemeinde abdecken). Der `GET /api/map`-Endpunkt der **Web**-Karte kannte diese
Logik aber gar nicht – er lieferte immer nur die normale Bundes-Einfärbung (`TerritoryColor`, Issue #8). Die
Web-Oberfläche hat sich stattdessen clientseitig beholfen und jeden Chunk anhand seiner **Gemeinde**-ID
(`c.town`) einem Bezirksergebnis zugeordnet (`result.districts.find(x => x.town === c.town)`). Das funktioniert
nur, solange ein Wahlbezirk genau einer ganzen Gemeinde entspricht. Sobald eine Gemeinde in mehrere Wahlbezirke
geteilt ist, hat jeder dieser Bezirke eine eigene, von der Gemeinde-ID verschiedene Bezirks-ID – die clientseitige
Zuordnung fand dafür nie eine Übereinstimmung und malte solche Chunks grau statt in Bezirksfarben, ganz ohne
sichtbare Grenze.

## Lösung

### Backend (`WebApi.java`)
- `GET /api/map` akzeptiert jetzt `election=<PolityId>` (+ optional `office=<Amt>`). Mit diesen Parametern liefert
  die neue Methode `mapElection(...)` (Pendant zu `TownService#mapByElection`, aber für die ganze Welt statt
  Spieler-zentriert) jeden Chunk mit der bereits vorhandenen, korrekten, je-Chunk-genauen Logik aus
  `ElectionMapColor.colorOf`/`districtOf`. Jeder Chunk trägt jetzt zusätzlich sein Feld `district` (die
  tatsächliche Bezirks-ID), mit dem das Frontend echte Bezirksgrenzen zeichnen kann.
- `GET /api/elections` liefert je Bezirk jetzt zusätzlich `lines`: die volle, nach Stimmen sortierte
  Kandidatenliste (vorher nur `leader`/`party`/`votes`).

### Frontend (`index.html`)
- `chunkColor()` nutzt direkt das vom Server gelieferte `group`/`district` statt eigener (fehlerhafter)
  Bezirkszuordnung.
- Neuer, **eigener Filter** „Partei" im Kartenwerkzeugleiste (`#mapparty`), nur sichtbar in der Wahlkarten-Ansicht,
  unabhängig vom Partei-Filter im Wahlen-Tab.
- Neuer Schalter „Bezirksgrenzen" (`#showborders`, standardmäßig an): zieht beim Zeichnen eine Linie entlang jeder
  Chunk-Kante, deren Nachbar-Chunk einem anderen Bezirk angehört – echte, sichtbare Wahlkreisgrenzen.
- Die Ebenen-Auswahl (die für Bundesfarben gilt) blendet sich in der Wahlkarten-Ansicht aus, da sie dort nicht
  greift – klarere Trennung der beiden Modi.
- Legende zeigt jeden Bezirk einzeln mit Führendem; ein aufklappbares „Rangfolge (n)" darunter zeigt die komplette,
  nach Stimmen sortierte Kandidatenliste mit Prozentanteilen.
- Wahlen-Tab: neues Suchfeld („Gemeinde/Bund suchen") zusätzlich zu Ebene/Partei. Die bisherige, zu einer einzigen
  Textzeile zusammengequetschte Bezirksliste eines Ergebnisses ist jetzt ein aufklappbares „N Wahlbezirke", darunter
  pro Bezirk wieder die aufklappbare volle Rangfolge – standardmäßig eingeklappt, damit Ergebnisse mit vielen
  Bezirken (z. B. Nationalwahlen) die Übersicht nicht sprengen.

## Tests

- `./gradlew :civ-domain:test :civ-application:test :civ-server:test` – BUILD SUCCESSFUL (keine Regression).
- Die Kernlogik (`ElectionMapColor.districtOf`/`colorOf`, je-Bezirk statt je-Gemeinde) ist bereits durch
  `ElectionMapColorTest` abgedeckt (aus Issue #9); diese PR ändert daran nichts, sondern verdrahtet sie zusätzlich
  mit dem Web-Endpunkt. Für `WebApi` selbst existiert im Projekt kein HTTP-Test-Harness (auch vorher nicht für
  `/api/map` o. ä.); die neue `mapElection`-Methode wurde manuell gegen den Code-Pfad von
  `TownService#mapByElection` nachvollzogen, den sie bewusst spiegelt.
- Die JavaScript-Änderungen wurden mit `node --check` auf Syntaxfehler geprüft; ein echter Browser-Test (Chunks mit
  mehreren Wahlbezirken, Grenzen, Partei-Filter, aufklappbare Rangfolge) ließ sich in dieser Umgebung ohne
  laufenden Minecraft-Server nicht durchführen – das wäre der nächste sinnvolle manuelle Check vor dem Merge.
