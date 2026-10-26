# civ-application

Application-Schicht von Eddies Civ Mod: Use Cases über dem Domänenkern, ohne Minecraft und ohne konkrete Speichertechnik.

## Aufbau

| Paket | Inhalt |
| --- | --- |
| `polity` | `FoundPolity`, `JoinPolity`, `LeavePolity`, `EnactStatute`, `RepealStatute`, Regel „höchstens eine Gemeinde pro Akteur“ |
| `territory` | `ClaimChunk`, `ReleaseChunk` (verweigert Chunks mit Grundstücken) |
| `land` | `CreatePlot`, `AssignPlot`, `SellPlot` (Grundstück und Kaufpreis atomar), `DissolvePlot` |
| `economy` | `DepositEmeralds`, `WithdrawEmeralds`, `WithdrawCash`, `DepositCash`, `TransferFunds` |
| `day` | `EndOfDay`: Grundsteuer aller Gemeinden, `DayReport` |
| `query` | `WorldQueries`: lesender Zugriff über Lese-Schnittstellen (`PolityView`, `LandView`, `TerritoryView`, `LedgerView`) |
| `port` | `DomainEventPublisher` |
| `inmemory` | In-Memory-Repositories und `RecordingEventPublisher` für Tests und die Simulation |

`CivApplication` verdrahtet alles. Jeder Use Case hat einen `Command`-Record und eine Methode `execute`.

## Ablauf eines Use Cases

1. Aggregate laden (`Aggregates`), unbekannte Gemeinde → `UNKNOWN_POLITY`
2. Regeln prüfen, die mehrere Aggregate betreffen
3. Fachliche Methoden der Aggregate aufrufen
4. Speichern und Events veröffentlichen (`Aggregates.commit`)

Scheitert ein Schritt, werden keine Events veröffentlicht. Bei Use Cases, die mehrere Aggregate ändern (`SellPlot`, `FoundPolity`), wird zuerst alles geprüft, was scheitern kann.
