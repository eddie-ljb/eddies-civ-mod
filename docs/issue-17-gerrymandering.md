# Issue #17: Gerrymandering

## Problem

> "Es soll Gerrymandering möglich sein."

Wahlkreise (`Constituency`) existierten bereits als freie Sammlung von Gebieten (`Area`) und
ganzen Gemeinden (`PolityId`), ohne Zusammenhangs- oder Nachbarschaftszwang — beliebige, auch
nicht zusammenhängende Gebiete konnten einem Wahlkreis zugeordnet werden (`addArea`/`addTown`).
Was fehlte: ein Wahlkreis ließ sich nach dem Anlegen nicht mehr gezielt *verkleinern* —
einzelne Gebiete oder Gemeinden konnten nicht wieder herausgenommen werden, ohne den ganzen
Wahlkreis zu leeren (`clear`). Damit war ein nachträgliches "Gerrymandering" — gezieltes
Umzeichnen bestehender Wahlkreisgrenzen zum politischen Vorteil — nicht möglich.

## Lösung

Wahlkreise lassen sich jetzt beliebig neu zuschneiden:

- **Hinzufügen** (`addArea`/`addTown`) gab es bereits und unterliegt weiterhin keiner
  Zusammenhangsprüfung — beliebig verstreute Gebiete sind erlaubt.
- **Entfernen** ist neu: `removeArea`/`removeTown` nehmen gezielt ein einzelnes Gebiet bzw.
  eine Gemeinde aus einem bestehenden Wahlkreis heraus, ohne die übrigen Zuordnungen
  anzutasten. Kombiniert mit `addArea`/`addTown` lässt sich so jeder Wahlkreis beliebig oft
  neu zuschneiden.

## Implementierung

- **`civ-domain/.../civic/CivicRegistry.java`**: `removeArea(id, area, day)` und
  `removeTown(id, town, day)`. Beide werfen `IllegalArgumentException`, wenn das Gebiet/die
  Gemeinde nicht Teil des Wahlkreises ist.
- **`civ-domain/.../civic/Constituency.java`**: `hasArea`, `withoutArea`, `withoutTown` als
  package-private Hilfsmethoden, analog zu den bestehenden `withArea`/`withTown`.
- **`civ-application/.../civic/CivicUseCases.java`**: `removeArea`/`removeTown` prüfen wie
  die bestehenden Methoden über `requireReshapable`, ob der Aufrufer die Wahlkreise der Polity
  ändern darf.
- **`civ-server/.../civic/CivicService.java`**: `removeArea` nutzt wie `addArea` die aktuelle
  Grundstücksstab-Auswahl (`PlotService.Selection`) als zu entfernendes Rechteck;
  `removeTown` nimmt den Gemeindenamen als Argument.
- **`civ-server/.../level/LevelService.java`**: neue Aktion `"gebiet-entfernen"` im
  `constituency(...)`-Dispatcher sowie `constituencyTownRemove(...)`.
- **Befehle** (`civ-fabric/.../CivCommandTree.java`, `EnglishCommands.java`):
  `/civ <Ebene> wahlkreis gebiet-entfernen <Name>` (markiertes Rechteck entfernen) und
  `/civ <Ebene> wahlkreis gemeinde-entfernen <Name> <Gemeinde>`; englische Übersetzung
  `area-remove`/`town-remove`.
- **Nachrichten**: neue `MessageKey`-Einträge `CONSTITUENCY_AREA_REMOVED` /
  `CONSTITUENCY_TOWN_REMOVED`, deutsche und englische Texte in `de_de.json`/`en_us.json`,
  Hilfetext (`help_show`) aktualisiert.

## Tests

- `civ-domain/src/test/java/de/etiennebader/civ/domain/civic/CivicRegistryTest.java` (neu):
  - beliebige, nicht zusammenhängende Gebiete im selben Wahlkreis erlaubt;
  - `removeArea` entfernt genau ein Gebiet, lässt die übrigen unverändert;
  - `removeArea` lehnt ein nicht zugehöriges Gebiet ab;
  - `removeTown` entfernt genau eine Gemeinde, lässt die übrigen unverändert;
  - `removeTown` lehnt eine nicht zugehörige Gemeinde ab;
  - End-to-End: ein bestehender Wahlkreis wird durch Entfernen + erneutes Hinzufügen
    tatsächlich umgezeichnet (der eigentliche Gerrymandering-Fall).
- `JAVA_HOME=/opt/jdk25 ./gradlew :civ-domain:test :civ-application:test :civ-server:test :civ-fabric:compileJava` — **BUILD SUCCESSFUL**.
