# civ-application

Application-Schicht von Eddies Civ Mod: Use Cases über dem Domänenkern, ohne Minecraft und ohne konkrete Speichertechnik.

## Aufbau

| Paket | Inhalt |
| --- | --- |
| `polity` | `FoundPolity`, `JoinPolity`, `LeavePolity`, `EnactStatute`, `RepealStatute`, Regeln „höchstens eine Gemeinde pro Akteur“ und „Gemeindenamen eindeutig, ohne Groß-/Kleinschreibung“ |
| `territory` | `ClaimChunk`, `ReleaseChunk` (verweigert Chunks mit Grundstücken) |
| `land` | `CreatePlot`, `AssignPlot`, `SellPlot` (Grundstück und Kaufpreis atomar), `DissolvePlot` |
| `economy` | `DepositEmeralds`, `WithdrawEmeralds`, `WithdrawCash`, `DepositCash`, `TransferFunds` |
| `day` | `EndOfDay`: Grundsteuer aller Gemeinden, `DayReport`; `Calendar`: zählt Spielticks und meldet Tagesenden, unabhängig von der Tageszeit der Welt |
| `directory` | `PlayerDirectory`: Spielernamen zu UUIDs für Ausgaben, wird mitgespeichert |
| `query` | `WorldQueries`: lesender Zugriff über Lese-Schnittstellen (`PolityView`, `LandView`, `TerritoryView`, `LedgerView`), Suche nach Gemeindenamen |
| `persistence` | `ChangeTracker` (Änderungen aus Events), `WorldPersistence` (`restore()` beim Start; `prepareSave()` erfasst auf dem Spielthread ein unveränderliches `StateDelta`, `write()` schreibt es auch aus einem anderen Thread, `requeue()` nach Fehlern; `flush()` beides synchron), Port `StateStore`, `StateDump` (kanonischer Zustand für Vergleiche) |
| `port` | `DomainEventPublisher` |
| `inmemory` | In-Memory-Repositories und `RecordingEventPublisher` für Tests und die Simulation |

`CivApplication` verdrahtet alles. Jeder Use Case hat einen `Command`-Record und eine Methode `execute`.

## Ablauf eines Use Cases

1. Aggregate laden (`Aggregates`), unbekannte Gemeinde → `UNKNOWN_POLITY`
2. Regeln prüfen, die mehrere Aggregate betreffen
3. Fachliche Methoden der Aggregate aufrufen
4. Speichern und Events veröffentlichen (`Aggregates.commit`)

Scheitert ein Schritt, werden keine Events veröffentlicht. Bei Use Cases, die mehrere Aggregate ändern (`SellPlot`, `FoundPolity`), wird zuerst alles geprüft, was scheitern kann.
