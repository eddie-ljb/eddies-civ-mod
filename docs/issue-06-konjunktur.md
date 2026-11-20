# Issue #6: Konjunkturphasen je Wirtschaftszone

## Problem

> Es sollen Konjunkturphasen eingeführt werden. Dabei inklusive mit Gesetzen, Regierungsmaßnahmen und mehr. Dabei
> sollen alle Phasen eingeführt werden und mithilfe geeigneter Mechanismen sinnvoll simuliert werden. Tendenziell
> soll es auch nicht nur einen Konjunkturzyklus geben, sondern für jede getrennte Wirtschaftszone seine eigene
> Konjunktur definiert werden, die sich aber auch beeinflussen lassen. Natürlich haben diese auch Auswirkungen auch
> Kaufverhalten und mehr und Aktienkurse ...

Bisher gab es keinerlei Konjunkturzyklus: Produktion, Dorfbewohner-Einkommen und Preise liefen unabhängig von einem
wirtschaftlichen Auf und Ab.

## Lösung

Jede **Wirtschaftszone** (der höchste Bund, dem eine Gemeinde angehört – Nation > Land > Gemeinde, dieselbe Regel
wie bei der Kartenfarbe aus Issue #8; eine Gemeinde ohne Bund ist ihre eigene Zone) durchläuft eigenständig vier
Konjunkturphasen:

| Phase | Deutsch | Roh-Multiplikator |
|---|---|---|
| `EXPANSION` | Aufschwung | ×1,10 |
| `PEAK` | Hochkonjunktur | ×1,25 |
| `CONTRACTION` | Abschwung | ×0,90 |
| `TROUGH` | Rezession | ×0,70 |

Eine Zone durchläuft diese vier Phasen nacheinander in einem Zyklus von 24–48 Tagen (fest aus ihrer Kennung
ausgewürfelt, siehe unten). Zonen beeinflussen sich leicht gegenseitig: Der tatsächliche Effekt einer Zone mischt
ihren eigenen Phasenwert zu 85 % mit dem Durchschnitt aller Zonen zu 15 %.

**Regierungsmaßnahme:** der neue Rechtsparameter `conjuncture_stimulus_percent` (0–100, Standard 0) dämpft die
Abweichung vom Neutralwert 1,0 um bis zu 80 % bei 100 % Stimulus – eine Zone kann die Konjunktur also abfedern,
aber nicht vollständig neutralisieren.

### Implementierung

- **`civ-domain/.../conjuncture/BusinessPhase.java`** — die vier Phasen mit ihrem Roh-Multiplikator.
- **`civ-domain/.../conjuncture/BusinessCycle.java`** — reine, zustandslose Domänenlogik:
  - `phaseOf(PolityId zone, Day day)`: Zykluslänge und Versatz werden deterministisch aus der `PolityId` der Zone
    ausgewürfelt (`java.util.Random` mit einem aus der UUID abgeleiteten Seed). Dadurch braucht die Phase **keine
    eigene Speicherung** – kein Neustart-Drift, keine Datenbankmigration – und ist trotzdem reproduzierbar: derselbe
    Tag ergibt immer dieselbe Phase.
  - `effectiveMultiplier(zone, day, globalAverageMultiplier, stimulusPercent)`: mischt eigene Phase mit globalem
    Durchschnitt, dämpft per Regierungsmaßnahme, begrenzt auf 0,5–1,5.
- **`civ-server/.../conjuncture/ConjunctureService.java`** — verbindet die reine Domänenlogik mit der Spielwelt:
  - `zoneOf(town)`: höchster Bund-Vorfahre einer Gemeinde (oder die Gemeinde selbst).
  - `globalAverageMultiplier(day)`: Durchschnitt aller bekannten Zonen; auch Rückfallwert für Firmen (siehe unten).
  - `multiplierOf(town, day)` / `describe(town, day)`: tatsächlicher Effekt bzw. Ansicht für Menü/Befehl.
- **Auswirkungen (die beiden im Issue genannten "Kaufverhalten und mehr"):**
  - **Dorfbewohner-Einkommen** (`VillagerService#dailyIncome(town, day)`, aufgerufen aus `endOfDay(day)`): die
    tägliche Gemeindekasse-Einnahme wird mit `multiplierOf(town, day)` der **eigenen Zone** der Gemeinde skaliert.
    (Hinweis: `dailyIncome(town)` ohne Tag – für die `/civ stimmung`-Vorschau – nutzt weiterhin `calendar.today()`;
    `endOfDay` wurde umgestellt, explizit den übergebenen `day` statt implizit `calendar.today()` zu verwenden, damit
    beide nie auseinanderlaufen können.)
  - **Firmenproduktion** (`CompanyService#produce(day)`): da Firmen in diesem Modell nicht geografisch verankert
    sind (keine Gemeinde, in der sie sitzen), wirkt hier der **globale Durchschnitt aller Zonen** statt einer
    einzelnen auf das Preisniveau, mit dem produziert/verkauft wird.
- **`/civ konjunktur`** (`VillagerService#conjunctureReply`, verdrahtet in `CivCommandTree`/`EnglishCommands` als
  `conjuncture`): meldet Zonenname, aktuelle Phase und Effekt in Prozent der eigenen Gemeinde.
- Neuer Rechtsparameter in `StandardParameters` + Übersetzungen (inkl. der vier Phasennamen) in beiden Sprachdateien.

### Bewusst nicht umgesetzte Teilaspekte

Dieses Issue ist sehr breit angelegt ("Erarbeite das noch weiter und größer" sinngemäß für Nachbar-Issues); für eine
solide, getestete erste Version wurde bewusst **nicht** umgesetzt:

- **Aktienkurse:** der Börsen-/Aktienmechanismus (`CompanyFigures`, `HoldingView`, `InMemoryExchangeRepository`)
  wird von der Konjunktur noch nicht beeinflusst. Anknüpfungspunkt wäre, den Aktienkurs einer Firma zusätzlich mit
  `ConjunctureService#globalAverageMultiplier` (oder, falls Firmen künftig eine Gemeinde bekommen, deren Zonen-Wert)
  zu skalieren.
  - **Kaufverhalten der Dorfbewohner** (Nachfrageseite) jenseits des Einkommens: aktuell wirkt die Konjunktur nur auf
  die Angebotsseite (Produktion) und auf das Einkommen, nicht zusätzlich auf z. B. `TownMood`/Zufriedenheit oder auf
  individuelle Kaufentscheidungen der Dorfbewohner im Laden.
- **Web-API/Webseite:** die Konjunkturphase ist nur per `/civ konjunktur`-Befehl einsehbar, noch nicht über die
  eingebettete Webseite.
- **Weitere Regierungsmaßnahmen:** nur eine Maßnahme (`conjuncture_stimulus_percent`) ist umgesetzt; das Issue
  erwähnt "Gesetze, Regierungsmaßnahmen und mehr" allgemein – z. B. eine Maßnahme, die gezielt nur die Produktion
  oder nur das Einkommen dämpft, statt beides gleich, wäre eine sinnvolle Erweiterung.

## Tests

- **`BusinessCycleTest`** (civ-domain): Phasenreihenfolge über einen vollen Zyklus, Determinismus (gleicher Tag →
  gleiche Phase), Effekt von Kopplung und Dämpfung, Clamping auf 0,5–1,5.
- **`ConjunctureServiceTest`** (civ-server): Zone ohne Bund ist die Gemeinde selbst; Zone mit Bund ist dessen
  höchste Ebene; eine Regierungsmaßnahme der Zone (nicht der Gemeinde) dämpft den Effekt; `/civ konjunktur` meldet
  Zone/Phase/Effekt; globaler Durchschnitt ist ohne bekannte Zonen neutral (1,0).
- Ein bestehender Test (`VillagerServiceTest.villagersEarnAndPromote`) ging von einem konjunkturunabhängigen,
  fest verdrahteten Einnahmewert aus; er wurde so angepasst, dass er den tatsächlichen Konjunktur-Multiplikator der
  Zone für den Testtag selbst berechnet, statt eine Zahl hart zu kodieren (robust gegenüber künftigen Anpassungen
  an den Phasen-Multiplikatoren).
- `JAVA_HOME=/opt/jdk25 ./gradlew :civ-domain:test :civ-application:test :civ-server:test` – **BUILD SUCCESSFUL**.
