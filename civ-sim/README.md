# civ-sim

Headless-Simulation: spielt den Kern über `civ-application` viele Tage lang durch, ohne Minecraft. Entwicklungswerkzeug, nicht Teil des Mod-JARs.

## Starten

```bash
./gradlew :civ-sim:run --args="--scenario small"
./gradlew :civ-sim:run --args="--scenario medium --seed 7 --days 500 --event-log"
./gradlew :civ-sim:run --args="--scenario pfad/zu/eigenes.properties --out build/mein-lauf"
```

Ausgabe in `build/simulation/` (relativ zum Projektwurzelverzeichnis):

- `metrics.csv`: eine Zeile pro Tag (Gemeinden, Mitglieder, Chunks, Grundstücke, Geldmenge, Bargeld, Steuern, Zufriedenheit, …)
- `events.log` (mit `--event-log`): jedes Domain Event als `Tag<TAB>Typ<TAB>Inhalt`

Exit-Codes: `0` Erfolg, `1` Invariante verletzt, `2` fehlerhafte Kommandozeile oder fehlerhaftes Szenario.

## Szenarien

`src/main/resources/scenarios/defaults.properties` enthält alle Stellschrauben mit Kommentaren. `small`, `medium` und `stress` überschreiben nur einzelne Werte, eigene Dateien ebenso. Unbekannte Schlüssel und ungültige Werte werden mit allen Fehlern auf einmal gemeldet.

## Modell

**Akteure** (Spieler und Villager gleich behandelt) haben eine feste Persönlichkeit (Ehrgeiz, Geselligkeit, Sparsamkeit, Bargeldvorliebe, Produktivität) und drei Bedürfnisse: Zugehörigkeit, Wohnen, Sicherheit. Täglich: Einkommen und Lebenshaltung, dann die Handlung mit dem höchsten Nutzen (`AgentBrain`): gründen, beitreten, austreten, Grundstück kaufen, Bargeld abheben, einzahlen oder bar bezahlen.

**Gemeindeverwaltungen** zahlen Unterhalt pro Chunk, weisen Bauland nach Bedarf aus (und lösen überzähliges auf), erweitern ihr Kerngebiet, passen die Grundsteuer an ihre Rücklage an, erlauben ab einer Größe Außenposten und richten Botschaften für andere Gemeinden ein.

**Zufriedenheit** sinkt mit Steuerlast, fehlendem Grundstück und Steuerschulden; unter einer Schwelle treten Mitglieder aus.

## Garantien

- **Determinismus:** gleicher Seed → byte-identische `metrics.csv` und `events.log` (getestet in `SimulationTest`)
- **Invarianten nach jedem Tag** (`InvariantChecker`): Geldmengenerhaltung, Bargeld in Taschen = umlaufendes Bargeld, keine negativen Konten, Mitgliedschaften stimmen mit dem Kern überein, keine leeren Gemeinden, Grundstücke im eigenen Gebiet und überlappungsfrei, Kerngebiet zusammenhängend

## Basiswerte (Seed 42)

| Szenario | Akteure | Tage | Laufzeit |
| --- | --- | --- | --- |
| small | 20 | 365 | < 1 s |
| medium | 200 | 1.000 | ca. 3 s |
| stress | 2.000 | 1.000 | ca. 13 s |

Gemessen in der Entwicklungsumgebung; auf anderen Rechnern abweichend. Die Laufzeit steht nach jedem Lauf in der Konsole.
