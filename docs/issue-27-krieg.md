# Issue #27 – Krieg, Jobsuche, Familien

## Umgesetzt
- **Kriegsgrundlage** (`civ-domain/.../war`, `WarService`): Kriegserklärung, Garnison, Gefecht, Frieden, Neutralität gegen
  Gebühr, Sold aus der Gemeindekasse, Reparationen; kriegerische Dorfbewohner melden sich selbst.
- **Gebietseroberung und Reparationen per Siegpunkten:** Ein entschiedener Krieg gibt dem Sieger Siegpunkte (ein Punkt je
  Soldat Kräftevorsprung, `WarRegistry.victoryPoints`, mindestens einer; `PeaceTerms.points`). Der Bürgermeister des Siegers gibt sie
  mit `/civ krieg beute <Nr> <Chunks> <Punkte für Reparationen>` aus (`WarService.spoils`): ein Grenz-Chunk ohne Grundstücke kostet
  `conquest_points_per_chunk` Punkte (Standard 4), höchstens `conquest_chunks_per_win` Chunks (Obergrenze, Standard 2), ein Punkt
  Reparationen bringt `war_reparations_per_strength_point`. Chunks, die sich nicht erobern lassen (nicht angrenzend, mit Grundstücken, der
  Sitz, letzter Chunk des Verlierers), werden als Reparationen verrechnet. Offene Punkte verteilt das Spiel nach 3 Tagen selbst
  (`WarService.SPOILS_DAYS`: so viele Chunks wie möglich, Rest Reparationen); Dorfbewohner-Bürgermeister verteilen sofort.
  Persistenz: `WAR`-Zeilen in `war.tsv` tragen zusätzlich Punkte und „verteilt“ (alte Zeilen laden als verteilt).
- **Feldzüge und Schlachtplanung:** Angriff, Hinterhalt (mit Ausspähern) und Verteidigung werden für einen Tag geplant
  (`BattleKind`, `Campaign`) und am Tagesende ausgetragen; Entscheidungsschlacht beendet den Krieg.
- **Ausspäher** (`MilitaryRank.SCOUT`), **Frontverlauf** (`WarRegistry.frontOf`) im Spiel (`/civ krieg front`) und im Web
  (`GET /api/war`, Webseite „Krieg“).
- **Ausrüstung und Training** aus der Gemeindekasse (`TRAINING_COST_PER_HEAD`), **Lazarett** mit kürzerer Ausfallzeit.
- **Allianzen** zwischen Gemeinden/Bünden, **Ansprüche** und **Kriegswille** (`Will`: Persönlichkeit der Dorfbewohner,
  Partei des Bürgermeisters, Ansprüche, Kräfteverhältnis). Dorfbewohner-Bürgermeister führen Krieg selbst.
- **Persistenz** des Kriegszustands in `war.tsv`.
- **Jobsuche** aller arbeitslosen Dorfbewohner nach Persönlichkeit (`VillagerJobSearch`).
- **Familien** (`civ-domain/.../family`, `FamilyService`, `family.tsv`):
  - Ledige einer Gemeinde lernen sich täglich paarweise kennen; die *Beziehung* (0–100) wächst oder schwindet mit der
    *Verträglichkeit* der Persönlichkeiten (fünf Achsen). Ab Beziehung 20 und guter Verträglichkeit wird geheiratet.
    Verwandte (Eltern/Kinder, Geschwister) heiraten nicht.
  - Ehepaare mit dauerhaft schlechter Verträglichkeit lassen sich scheiden; höhere *Bildung* (stabiler Grundwert aus der
    Identität, +1 je 30 Tage im Dorf, bis +20) macht das seltener und senkt die Wunschzahl an Kindern.
  - Kinder: ab Beziehung 40, höchstens 4, Wunschzahl aus Gemeinsinn, Heimatverbundenheit und Bildung, mindestens 10 Tage
    Abstand. Auskunft beim Schleichen+Rechtsklick (Ehepartner, Kinder); Befehl `/civ familie liste|kinder` (en: `/civ family list|children`);
    Webseite „Familien“ (`GET /api/families`).
  - **Kinder im Spiel:** `VillagerCensus` bringt vorgemerkte Geburten (`FamilyService.pendingBirths()`) als Baby-Dorfbewohner
    mit der Kennung des Kindes neben einem geladenen Elternteil zur Welt (`born` quittiert); ist kein Elternteil geladen,
    bleibt die Geburt vorgemerkt. Sobald das Baby erwachsen ist, meldet es der Census wie jeden Dorfbewohner.
  - Abschaltbar mit `villager_ambitions=false`.

## Noch offen
- Bildung kennt noch keine Schulen; Kinder wachsen mit der normalen Dorfbewohner-Reifezeit heran.

## Testen
1. Krieg: zwei Gemeinden gründen, `/civ krieg anspruch`/`erklaeren`, `dienen`, `front`, `gefecht`, `frieden`.
2. Familien: Gemeinde mit mehreren Dorfbewohnern, Tage vergehen lassen; `/civ familie liste`, `/civ familie kinder`, Seite „Familien“, Dorfbewohner anschleichen; Babys erscheinen neben den Eltern.
Automatisch: `./gradlew :civ-domain:test :civ-application:test :civ-server:test` (`FamilyTest`, `FamilyServiceTest`).
