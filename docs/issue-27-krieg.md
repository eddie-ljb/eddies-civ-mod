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
