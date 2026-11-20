# Issue #5: Economy data and fine tune

## Problem

> Fein-tuning der Wirtschaftssimulationsdaten. Diese sollen auch per Config definierbar und tunebar sein.
> Z.B. Inflation, oder Ähnlich

Das Preisniveau des Servermarkts (`de.etiennebader.civ.domain.market.PriceLevel`) – die zentrale
"Inflation/Deflation"-Mechanik des Mods, siehe README-Abschnitt "Servermarkt mit dynamischen Preisen" –
war bisher vollständig hart codiert: die Grenzen des Preisfaktors (0,5 bis 4,0) und die Dämpfung der
Reaktion auf die Geldmenge je Kopf (feste Quadratwurzel-Formel) ließen sich nicht ohne Codeänderung
anpassen.

## Lösung

Die drei Stellschrauben des Preisniveaus sind jetzt über die Server-Konfiguration einstellbar, mit den
bisherigen festen Werten als Standard (bestehende Welten verhalten sich also unverändert, bis der Owner
etwas ändert):

| Config-Schlüssel          | Bedeutung                                                                 | Standard |
|----------------------------|----------------------------------------------------------------------------|----------|
| `price_level_min`          | Kleinster Preisfaktor bei sehr geringer Geldmenge je Kopf                 | `0.5`    |
| `price_level_max`          | Größter Preisfaktor bei sehr hoher Geldmenge je Kopf (Inflation)          | `4.0`    |
| `price_level_damping`      | Dämpfung der Preisreaktion auf die Geldmenge je Kopf (0,5 = Quadratwurzel) | `0.5`    |

`price_level_damping` ist der Exponent auf das Verhältnis (Geldmenge je Kopf / `price_reference_...`):
0,5 entspricht exakt der bisherigen Formel, 1,0 ließe die Preise linear/proportional zur Geldmenge je
Kopf schwanken (stärkere Inflation/Deflation), Werte darunter dämpfen stärker als bisher.

## Implementierung

- **`civ-domain/.../market/PriceLevel.java`**: neue Überladung
  `of(Money supply, int population, Money reference, double minFactor, double maxFactor, double damping)`
  mit Validierung (`minFactor`/`maxFactor` müssen innerhalb von `PriceLevel.MIN`/`MAX` liegen und
  `minFactor < maxFactor`; `damping > 0`). Die bestehende dreistellige `of(...)`-Methode bleibt als
  Komfort-Überladung erhalten und ruft die neue mit den bisherigen Standardwerten auf – kein Verhaltensbruch
  für andere Aufrufer.
- **`civ-application/.../query/WorldQueries.java`**: neue Überladung `priceLevel(population, reference,
  minFactor, maxFactor, damping)`, die bestehende zweistellige Methode bleibt unverändert bestehen
  (Fallback auf Standardwerte). Die Anwendungsschicht bleibt damit weiterhin konfigurationsunabhängig –
  die konkreten Werte reicht ausschließlich die Server-Schicht durch.
- **`civ-server/.../ServerSettings.java`**: neuer verschachtelter Record `EconomySettings` (nach dem
  gleichen Muster wie `ShowSettings`/`PartySettings` als zusätzliche, optionale Komponente mit
  Standardwert-Konstruktor-Überladung angehängt) mit den drei Feldern und eigener Validierung.
- **`civ-server/.../ServerConfig.java`**: neue Properties-Schlüssel `price_level_min`, `price_level_max`,
  `price_level_damping`, Parsing/Schreiben inkl. neuem `decimal(...)`-Hilfsparser (analog zum bestehenden
  `number(...)` für Ganzzahlen), Aufnahme in die Migrations-`KEYS`-Liste (damit bestehende
  `eddies-civ-mod.properties`-Dateien beim nächsten Laden automatisch um die neuen Schlüssel ergänzt
  werden) und in den Datei-Header-Kommentar.
- **`civ-server/.../market/MarketService.java`**: `priceLevel()` reicht jetzt `settings.economy()` an
  die neue `WorldQueries`-Überladung durch.

Bewusst **nicht** angefasst: die hart codierten Gewichte in `civ-sim/.../world/SatisfactionModel.java`
(`DAILY_BASE`, `TAX_WEIGHT`, `HOUSING_WEIGHT`, `ARREAR_PENALTY`) – das ist eher Zufriedenheits- als
Marktsimulation und würde eine eigene, in `civ-sim` verankerte Konfigurationsschnittstelle brauchen
(dort gibt es noch keinen äquivalenten "lies aus Properties"-Mechanismus wie `ServerConfig`). Das ist
ein guter Kandidat für eine eigene, kleinere Folge-Änderung, falls gewünscht.

## Tests

- `civ-domain/.../market/MarketTest.java`: neuer Test `priceLevelHonoursConfiguredBoundsAndDamping` –
  prüft Standard-Dämpfung, abweichende Dämpfung mit engerer Obergrenze, abweichende Untergrenze, sowie
  beide neuen Validierungsfehler (ungültige Grenzen, nicht-positive Dämpfung).
- `civ-server/.../ServerConfigTest.java`: drei neue Tests – gültige Werte werden übernommen
  (`readsEconomyTuning`), ungültige Kombination (`min >= max`) fällt komplett auf den Standard-Record
  zurück (`invalidEconomyTuningFallsBackToDefaults`), und ein Schreib-/Lese-Durchlauf erhält die Werte
  (`economyTuningRoundTrips`).
- `./gradlew :civ-domain:test --tests "*PriceLevel*" --tests "*Market*" :civ-server:test --tests "*ServerConfig*"` –
  **BUILD SUCCESSFUL**, alle neuen und bestehenden Tests zu Preisniveau und Server-Config grün.
- `./gradlew :civ-server:test` (voller Modul-Testlauf): ein einzelner, inhaltlich nicht betroffener Test
  (`CivServerTest.savesInBackgroundAndRestores`) schlug in dieser Session mit `ClassFormatError: Truncated
  class file` beim Laden des SQLite-JDBC-Treibers fehl – reproduzierbar auch ohne Gradle-Daemon, aber die
  Jar-Datei selbst ist laut `unzip -t` intakt. Das betrifft ausschließlich Datenbank-Persistenz, nicht den
  Preisniveau-Code dieser Änderung, und trat vermutlich durch gleichzeitig laufende Sibling-Builds auf,
  die denselben `~/.gradle`-Cache teilen. Sollte das auch in CI auftreten, ist es ein Umgebungs-/Cache-Problem,
  kein Regressions-Fehler dieser PR.
