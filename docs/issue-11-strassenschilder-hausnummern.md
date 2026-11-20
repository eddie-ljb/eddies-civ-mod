# Issue #11: Straßenschilder und Hausnummern

## Problem
> Es soll Straßenschilder inklusive Items und Blöcken, die man Setzen kann, geben. Diese sollen dann
> individuell beschriftet werden können. Außerdem soll es Hausnummern als eigenen Items und setzbare
> Blöcke geben, die dann auch Individuell beschrift werden können. [...] Zusätzlich soll es auch
> Ortsschilder, Länderschilder und nationenschidler usw geben, die mit mehreren Zeilen selbst
> beschriftet und gefärbt werden können sollen. Auch als eigene Items und setzbare Blöcke.

Der Mod hatte bereits ein rein kommandobasiertes, automatisches Adresssystem (`/civ strasse anlegen`,
`/civ adresse`) für Gebäude-Nummerierung, aber keine physischen, frei beschriftbaren Deko-Blöcke.

## Lösung
Fünf neue Blöcke/Items, die sich einen gemeinsamen `LabelBlock`/`LabelBlockEntity`-Unterbau teilen:

| Id | Zeilen | Zweck |
|---|---|---|
| `street_sign` | 1 | Straßenschild, z. B. "Hauptstraße" |
| `house_number` | 1 | Hausnummer, z. B. "12" |
| `town_sign` | mehrzeilig (bis 4) | Ortsschild |
| `country_sign` | mehrzeilig (bis 4) | Länderschild |
| `nation_sign` | mehrzeilig (bis 4) | Nationenschild |

Alle sind zusätzlich färbbar (Rechtsklick mit Farbstoff).

## Implementierung
- `LabelBlock` (civ-fabric, Block + `EntityBlock`): übernimmt beim Platzieren den im Amboss vergebenen
  Item-Namen als Text (`setPlacedBy`), bei mehrzeiligen Schildern getrennt durch `|`. Rechtsklick ohne
  Item zeigt den Text in der Actionbar; Rechtsklick mit Farbstoff färbt das Schild (shrinkt den
  Farbstoff im Überlebensmodus) statt den Text anzuzeigen.
- `LabelBlockEntity`: hält Zeilen (max. 32 Zeichen je Zeile, auf `maxLines` begrenzt) und optionale
  `DyeColor`. Persistenz über die in Minecraft 26.3 neue `ValueOutput`/`ValueInput`-API (nicht mehr
  direkt `CompoundTag`): Zeilen als `Codec.STRING`-Liste, Farbe als String.
- Da `DyeItem` in 26.3 kein `getDyeColor()` mehr besitzt (Vanilla-Farbstoffe sind jetzt eine
  `ColorCollection<Item>` unter `Items.DYE` ohne Rückweg Item→Farbe), baut `LabelBlock` sich beim
  Klassenladen einmalig eine `Map<Item, DyeColor>` aus den 16 `Items.DYE.<farbe>()`-Konstanten.
- Registrierung in `CivContent`: fünf `labelBlock(...)`-Aufrufe (neue private Fabrikmethode analog zu
  `simpleBlock`), ein gemeinsamer `BlockEntityType<LabelBlockEntity>` (in 26.3 direkt per
  `new BlockEntityType<>(factory, Set.of(blocks...))` statt des in älteren Versionen vorhandenen
  `BlockEntityType.Builder`), Aufnahme in den Kreativ-Reiter sowie Item-Registrierung.
- Assets: Blockstates, einfache Block-Modelle (Platzhalter-Texturen in Holz- bzw. Metalloptik je nach
  Schildtyp), Standard-Loot-Tables (Abbau droppt den Block – Text und Farbe gehen dabei verloren, der
  Beschriftungsvorgang muss nach erneutem Setzen wiederholt werden; kein `copy_components` eingebaut)
  und Rezepte (siehe Tabelle unten) sowie `de_de.json`/`en_us.json`-Einträge für alle fünf Blöcke plus
  die Einfärbe-Nachricht.
- Rezepte: Straßenschild (Stöcke + Eichenbretter), Hausnummer (Eisennuggets), Ortsschild
  (Eichenbretter + Stock), Länder-/Nationenschild (Dunkeleichenbretter + Stock, beim Nationenschild
  zusätzlich ein Smaragd).

### Abweichung von der ursprünglichen Minecraft-1.20-artigen Annahme
Minecraft 26.3 hat gegenüber älteren Versionen mehrere Signatur-Änderungen, auf die der erste Entwurf
noch nicht abgestimmt war; beim Fortsetzen nach einer Rate-Limit-Unterbrechung wurden diese korrigiert:
`BlockEntity#saveAdditional`/`loadAdditional` nehmen jetzt `ValueOutput`/`ValueInput` statt
`CompoundTag`, `StringTag#asString()`/`CompoundTag#getString(...)` liefern `Optional`,
`BlockEntityType.Builder` existiert nicht mehr, `DyeItem#getDyeColor()` ist entfallen, und
`Player#displayClientMessage` gibt es nicht mehr (stattdessen `ServerPlayer#sendSystemMessage(Component, boolean)`).
Alle Fundstellen wurden anhand der tatsächlichen Minecraft-26.3-Klassen (per `javap` auf das
Loom-Mapping-Jar) korrigiert, nicht nur geraten.

## Tests
- `JAVA_HOME=/opt/jdk25 ./gradlew :civ-fabric:compileJava :civ-fabric:test --console=plain` –
  **BUILD SUCCESSFUL** (inkl. bestehendem `ErrorTranslationsTest`, der u. a. vollständige Lang-Einträge
  erzwingt).
- Kein automatisierter Test für die eigentliche Block-/BlockEntity-Logik: das civ-fabric-Modul hat
  keinen Minecraft-Test-Bootstrap (das einzige vorhandene Test ist rein Lang-Datei-basiert), echte
  Block-Registry-Objekte (`BlockPos`, `BlockState`, ...) lassen sich hier nicht ohne laufenden Client/
  Server instanziieren. Manuelles Testen im Spiel (Platzieren, Amboss-Beschriftung, Färben, Abbauen)
  ist daher nicht durch diese Session abgedeckt.
