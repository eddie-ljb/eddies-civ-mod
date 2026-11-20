# Issue #14: Loyalität als Charakterwert für Dorfbewohner

## Problem

> Es soll Loyalität geben und jede Villager soll einen bestimmten Charakterwert zur Loyalität besitzen. Dabei
> Loyalität zu politischen Ebenene (Gemeinde, Land, Nation, usw.) sowie zur Partei, Parteivorsitz, Religion,
> Unternehmen, usw. Die Loyalität soll sich auch auf das Verhalten der Villager auswirken.

Bisher gab es nur die feste Persönlichkeit (Ordnung/Freiheit, Sparsamkeit/Großzügigkeit, Heimatverbundenheit) und
die tagesaktuelle Zufriedenheit. Beides sagt nichts darüber aus, wie sehr ein Dorfbewohner über die Zeit an seiner
aktuellen Gemeinde, Partei, deren Vorsitz oder seinem Arbeitgeber hängt — ein neu zugezogener und ein langjähriger
Bürger mit derselben Tageszufriedenheit verhielten sich identisch.

## Lösung

Neue Klasse `VillagerLoyalty` (civ-application, Paket `population`) berechnet Loyalität (-100 bis 100) **deterministisch**
aus bereits vorhandenen Daten — nichts Neues muss persistiert werden:

- **Basis:** die angeborene Heimatverbundenheit der Persönlichkeit, halbiert.
- **Drift:** mit wachsender Mitglieds-, Amts- oder Anstellungsdauer (volles Gewicht ab `FULL_WEIGHT_DAYS` = 60 Tagen)
  schlägt die seitherige Zufriedenheit/Nähe zunehmend durch (bis zu `DRIFT_SHARE` = 60 % Anteil). Dauerhaft
  zufriedene Dorfbewohner werden so loyaler, dauerhaft unzufriedene weniger loyal oder illoyal — unabhängig von der
  tagesaktuellen Stimmung allein.

Vier Dimensionen:

| Methode | Bezug | Woraus |
|---|---|---|
| `toPolity` | Gemeinde oder ein Bund darüber | Mitgliedsdauer + `Satisfaction`-Score in dieser Ebene |
| `toParty` | eigene Partei | Mitgliedsdauer + Nähe von Ordnung/Sparsamkeit zum Parteiprofil (`PartyProfile.distanceTo`) als Stimmungs-Ersatz |
| `toChairperson` | aktueller Parteivorsitz bzw. Bürgermeister/Vorsitz eines Bundes | Amtszeit des Amtsinhabers (0, solange das Amt unbesetzt ist) |
| `toEmployer` | Arbeitgeber (Unternehmen) | Anstellungsdauer (ohne eigenes Unternehmens-Zufriedenheitsmodell nur mit der Heimatverbundenheit gewichtet) |

**Religion** (Issue #13) existiert noch nicht auf `master`; eine eigene Loyalitäts-Dimension dafür wurde deshalb
bewusst nicht gebaut, um keine Religion-Attrappe einzuführen. Sobald #13 landet, ist `VillagerLoyalty` der
richtige Ort für eine `toReligion(...)`-Methode nach demselben Muster.

### Auswirkung auf das Verhalten

- **Wahlverhalten** (`PersonalityVoter`): der Bonus für die eigene Partei (`partyAffinity`) wird jetzt optional mit
  der Parteiloyalität gewichtet — bei voller Loyalität (100) der volle bisherige Bonus, bei minimaler/negativer
  Loyalität nur noch 40 % davon. Ohne Loyalitätsangabe (Rückwärtskompatibilität) verhält sich die Methode wie zuvor.
- **Abwanderung** (`Emigration`): die Abwanderungswahrscheinlichkeit (`chance`) wird jetzt optional durch die
  Loyalität zur bisherigen Gemeinde gedämpft — bei voller Loyalität höchstens die Hälfte der ungedämpften Chance,
  bei 0 oder negativer Loyalität unverändert. `VillagerService.emigrate(...)` übergibt dazu
  `VillagerLoyalty::toPolity` als Loyalitätsquelle.
- **Anzeige:** `VillagerService.describe(...)` zeigt nach der Zufriedenheit jetzt zusätzlich die Loyalität zur
  Gemeinde (`VILLAGER_LOYALTY`) und, falls Parteimitglied, zu Partei und Vorsitz (`VILLAGER_PARTY_LOYALTY`) — an
  derselben Stelle, an der bisher schon Zufriedenheit und Persönlichkeit beim Anschauen mit Schleichen und
  Rechtsklick erscheinen.

## Implementierung

- `civ-application/.../population/VillagerLoyalty.java` (neu)
- `civ-application/.../population/PersonalityVoter.java`: neue `partyAffinity(..., Optional<Integer> ownPartyLoyalty)`-Überladung
- `civ-application/.../population/Emigration.java`: neue `plan(..., BiFunction<PersonRef, PolityView, Integer> loyaltyOf)`- und `chance(..., int loyaltyToHome)`-Überladungen
- `civ-server/.../villager/VillagerService.java`: neuer Konstruktor-Parameter `Calendar`, Loyalitätszeilen in `describe()`, Loyalitätsdämpfung in `emigrate()`
- `civ-server/.../CivServer.java`: Konstruktoraufruf angepasst
- `civ-server/.../command/MessageKey.java`, Lang-Dateien: `VILLAGER_LOYALTY`, `VILLAGER_PARTY_LOYALTY`

## Tests

- `VillagerLoyaltyTest` (neu): `derive()`-Kernformel (frisch, volle Gewöhnungszeit, Clamping/negative Dauer),
  `toPolity` (Loyalität wächst mit anhaltend guter Stimmung und Dauer), `toChairperson` (Wertebereich mit
  Amtsinhaber), `toEmployer` (wächst mit Dauer bei hoher Heimatverbundenheit).
- `PersonalityVoterTest` (neu): `partyAffinity` ohne Loyalitätsangabe wie bisher; mit Loyalität skaliert der
  Bonus zwischen 40 % und 100 %.
- `EmigrationTest`: neuer Test `loyaltyDampensChance` (volle Loyalität höchstens halbe Chance, negative Loyalität
  dämpft nicht zusätzlich, Zwischenwerte liegen dazwischen).
- `JAVA_HOME=/opt/jdk25 ./gradlew :civ-domain:test :civ-application:test :civ-server:test` — **BUILD SUCCESSFUL**.
  `civ-fabric` wurde nicht gebaut, da nur Lang-Ressourcen (keine Java-Quellen) geändert wurden.
