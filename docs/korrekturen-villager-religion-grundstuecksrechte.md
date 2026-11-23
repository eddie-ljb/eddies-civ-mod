# Villager-Gründungen, Familien, Religion im Web, Grundstücksrechte und Bauen durch Gemeinde und Firmen

## Ursachen der Fehler

- **Gemeinden:** `VillagerFoundations` gründete am letzten Aufenthaltsort des Dorfbewohners. Dorfbewohner sind aber nur
  Mitglied einer Gemeinde, weil sie in deren Gebiet gesehen wurden (`VillagerService#seen`). Der Chunk war also immer schon
  beansprucht, die Claim-Prüfung lehnte jede Gründung ab. Selbst bei Erfolg hätte `seen` den Gründer beim nächsten Rundgang
  zurück in die alte Gemeinde sortiert. Außerdem reichten die Ersparnisse (Obergrenze 50 Smaragde) nie für die Gebühr von 50.
- **Zeitungen und Religionen:** Es gab nur Dorfbewohner, die in bestehenden Zeitungen schreiben, aber nie eine Gründung.
- **Heirat und Kinder:** Täglich zufällige Paarung; ein bestimmtes Paar traf sich nur alle paar Tage, die Beziehung wuchs
  nur schleppend. Ohne Heirat gab es keine Kinder.
- **Bauen:** Dorfbewohner beauftragten nur Baufirmen, deren Chef ein Dorfbewohner derselben Gemeinde war, und Villager-Firmen
  wählten die Branche Bau nur zufällig. Wer niemals ein Grundstück kaufte (außer Vermietern), baute nie.

## Änderungen

| Bereich | Klassen |
|---|---|
| Gemeinde gründen | `VillagerFoundations#site`, `VillagerService#relocate`, Siedlergebühr 20 % |
| Zeitungen, Religionen | `PressService#villagersFound`, `ReligionService#villagersFound` |
| Familie | `FamilyService#meet` (Paarbildung nach Verträglichkeit), `Family` (Hochzeitsbonus, Geburtenwahrscheinlichkeit) |
| Web | `GET|POST /api/religions`, Reiter „Religion“ in `index.html` |
| Grundstücksrechte | `PlotRights`, `PlotFlag`, `Stance`, `ProtectionRules`, `PlotRightsService`, `PlotRightsScreen`, Gesetz `plot_rights_override` |
| Bauen | `ConstructionService#villagersOrder`, `TownPlanning`, `BuiltInBlueprints` (57 Pläne), `VillagerBusiness#builderNeeded` |

## Tests

`VillagerFoundationsTest`, `VillagerMediaFoundingTest`, `FamilyServiceTest`, `PlotRightsRulesTest`,
`PlotRightsServiceTest`, `VillagerUrbanismTest`, `WebIntegrationTest`, `ConstructionServiceTest`.
