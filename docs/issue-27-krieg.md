# Issue #27 – Krieg, Jobsuche, Familien

## Umgesetzt
- **Kriegsgrundlage** (`civ-domain/.../war`, `WarService`): Kriegserklärung, Garnison, Gefecht, Frieden, Neutralität gegen
  Gebühr, Sold aus der Gemeindekasse, Reparationen; kriegerische Dorfbewohner melden sich selbst.
- **Gebietseroberung:** der Sieger erobert Grenz-Chunks ohne Grundstücke (`conquest_chunks_per_win`).
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
    Abstand. Auskunft beim Schleichen+Rechtsklick (Ehepartner, Kinder); Web: `GET /api/families`.
  - Abschaltbar mit `villager_ambitions=false`.

## Noch offen
- **Kinder im Spiel:** `FamilyService.takeBirths()` liefert Geburten, aber der Fabric-Teil, der daraus ein Baby-Dorfbewohner-
  Entity erzeugt (`setUUID(child.id())`), ist nicht angebunden (kann ohne Fabric-Build nicht geprüft werden). Bis dahin
  existieren Kinder nur im Register; sie zählen als Dorfbewohner, sobald das Spiel sie als Erwachsene meldet.
- Kein Befehl und keine Webseite für Familien (nur API); Bildung kennt noch keine Schulen.

## Testen
1. Krieg: zwei Gemeinden gründen, `/civ krieg anspruch`/`erklaeren`, `dienen`, `front`, `gefecht`, `frieden`.
2. Familien: Gemeinde mit mehreren Dorfbewohnern, Tage vergehen lassen; `GET /api/families` und Dorfbewohner anschleichen.
Automatisch: `./gradlew :civ-domain:test :civ-application:test :civ-server:test` (`FamilyTest`, `FamilyServiceTest`).
