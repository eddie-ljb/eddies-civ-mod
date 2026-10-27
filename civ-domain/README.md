# civ-domain

Fachlicher Kern von Eddies Civ Mod: reines Java 25, ohne Minecraft, Fabric oder andere Frameworks.
Basis-Package: `de.etiennebader.civ.domain`.

## Aggregate

| Aggregat | Paket | Konsistenzgrenze |
| --- | --- | --- |
| `Polity` | `polity` | Mitglieder und geltendes Recht einer Polity |
| `TerritoryRegistry` | `territory` | Hoheit über Chunks, weltweit |
| `LandRegistry` | `land` | Grundstücke einer Polity |
| `Ledger` | `economy` | Konten und Bargeldumlauf, weltweit |

`LandTaxCollector` (`fiscal`) und `ProtectionRules` (`protection`, Gebietsschutz) verbinden sie als zustandslose Domain Services.

## Paketregeln

| Paket | darf verwenden |
| --- | --- |
| `shared` | nichts |
| `actor` | `shared` |
| `law` | `shared` |
| `territory` | `shared`, `actor` |
| `land` | `shared`, `actor`, `territory` |
| `economy` | `shared`, `actor` |
| `polity` | `shared`, `actor`, `law`, `territory` (Sitz) |
| `fiscal` | alle anderen |
| `protection` | `shared`, `actor`, `law`, `territory`, `land`, `polity` |

`PackageDependencyRulesTest` bricht den Build bei Verstößen und bei Imports aus `net.minecraft`, `net.fabricmc` oder `com.mojang`.

## Konventionen

- `record` für Value Objects, IDs und Events; Validierung im kompakten Konstruktor.
- `final class` für Aggregate und Entities; Änderungen nur über fachliche Methoden, jede Änderung erzeugt ein Event (`pullEvents()`).
- `sealed interface` je Paket für Events und feste Varianten (`ActorRef`, `TransferResult`).
- Der Tag (`Day`) wird immer übergeben, nie ermittelt. IDs kommen über einen `IdGenerator`.
- Kein `null`: `Optional` für fehlende Werte, `Objects.requireNonNull` für Parameter.

## Fehler

- Regelverstöße, die ein Spieler auslösen kann, sind `DomainException`s mit `ErrorCode` und Argumenten. Der Kern enthält keine Texte; `civ-fabric` übersetzt `ErrorCode.translationKey()` über `assets/eddies-civ-mod/lang/*.json`.
- Erwartbare Fälle sind keine Fehler: `Ledger.tryTransfer` liefert `TransferResult.Rejected`, z. B. beim Steuereinzug.
- Programmierfehler (negativer Tag, Betrag ≤ 0, Überweisung an sich selbst) sind `IllegalArgumentException`s.

## Tests

`./gradlew :civ-domain:test` — Testfälle aus dem Domänenmodell inklusive Abnahmeszenario „Nordhain“ in `LandTaxCollectorTest`.
