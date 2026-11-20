# Issue #3: Claim-Kosten für Gemeinden

## Problem

> Die Claims einer Gemeinde sollen künftig einen Gegenpreis besitzen, also finanziell oder
> monetär bezahlt werden müssen. Dabei z.B. Smaragde, Währung oder Ähnlich. Die Kosten soll
> in den Configs einstellbar sein, es soll aber auch einen Standardwert geben.

Bisher war das Beanspruchen eines Chunks (Kerngebiet oder Außenposten) komplett kostenlos.

## Lösung

Ein neuer Rechtsparameter `claim_cost_per_chunk` (Standard: `0` = kostenlos, wie bisher) legt
fest, wie viel ein Chunk-Claim aus der Gemeindekasse kostet, in Hundertstel Smaragd (also wie
die Grundsteuer `land_tax_per_block`). Reicht die Kasse nicht, wird der Claim abgelehnt, bevor
sich irgendetwas am Gebiet ändert.

Der Standardwert bleibt bei `0`, damit sich bestehende Welten und die Dorfbewohner-Expansion in
`civ-sim` nicht unangekündigt ändern. Server-Betreiber, die eine spürbare Gebühr wollen, setzen
z. B. 200 (= 2 Smaragde) per Gesetz/Verfassung.

## Implementierung

- **`StandardParameters.CLAIM_COST_PER_CHUNK`** (civ-domain): neuer Rechtsparameter, `Long`,
  Standard `0`, muss `>= 0` sein.
- **`ClaimChunk`** (civ-application, Use Case für `/civ beanspruchen`): liest den geltenden
  Satz aus dem effektiven Recht der Gemeinde (inklusive übergeordnetem Bundesrecht). Ist er
  `> 0`, prüft der Use Case zuerst den Kontostand der Gemeindekasse
  (`Ledger.balanceOf(polity.asActor(), ...)`); reicht er nicht, wirft er eine
  `ClaimRejectedException` mit dem neuen Grund `INSUFFICIENT_FUNDS` (fehlender Betrag als
  Argument) – **bevor** der Chunk beansprucht wird. Erst danach beansprucht er den Chunk wie
  bisher und zieht den Betrag per `Ledger.burn(..., SupplyChange.TERRITORY_CLAIM, ...)` aus der
  Kasse ab (das Geld verlässt den Umlauf, wie bei einer Investition – es gibt keinen einzelnen
  Empfänger für eine Claim-Gebühr).
- **`ClaimRejection.INSUFFICIENT_FUNDS`** (civ-domain) mit neuem `ErrorCode.CLAIM_INSUFFICIENT_FUNDS`
  und einer zusätzlichen `ClaimRejectedException`-Überladung, die den fehlenden Betrag als
  drittes Nachrichtenargument mitgibt.
- **`SupplyChange.TERRITORY_CLAIM`** (civ-domain): neuer Grund, aus dem Geld vernichtet wird.
- Übersetzungen (`de_de.json` / `en_us.json`): `eddies-civ-mod.error.claim_insufficient_funds`
  (Spielermeldung) und `eddies-civ-mod.law.claim_cost_per_chunk` (Anzeige im Rechtsmenü/-editor).
- Keine Schema-Änderung nötig: Rechtsparameter sind bereits generisch persistiert.

`FoundPolity` (Gründung der Gemeinde samt erstem Chunk) ruft `TerritoryRegistry.claim(...)`
weiterhin direkt auf, nicht über `ClaimChunk` – der erste Chunk beim Gründen bleibt also
absichtlich immer kostenlos.

Die Dorfbewohner-Expansion in `civ-sim`/`MunicipalGovernment` nutzt denselben `ClaimChunk`-Use
Case über `attempt(...)`: eine abgelehnte Gebühr lässt diesen Expansionsversuch einfach
scheitern, genau wie jede andere Claim-Ablehnung (Limit erreicht, nicht angrenzend, …). Das ist
beabsichtigt und braucht keine Sonderbehandlung.

## Tests

- `TerritoryUseCasesTest.claimCostIsDeductedFromTreasury`: Gesetz setzt die Gebühr auf 200,
  die Gemeindekasse hat 500 → nach dem Claim sind noch 300 übrig, der Chunk gehört der Gemeinde.
- `TerritoryUseCasesTest.claimRejectedWhenTreasuryInsufficient`: Gesetz setzt die Gebühr auf
  200, die Kasse hat nur 100 → `ErrorCode.CLAIM_INSUFFICIENT_FUNDS`, der Chunk bleibt frei und
  die Kasse unverändert.
- Alle bestehenden Claim-Tests (Standard-Gebühr `0`) laufen unverändert weiter.
- `./gradlew :civ-domain:test :civ-application:test :civ-server:test` und
  `:civ-fabric:compileJava` – alle grün.
