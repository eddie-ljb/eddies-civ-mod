# Issue #8: Map Fix

## Problem

Web- und Ingame-Karte färbten Gebiete bisher je Gemeinde einzeln (zufällige Palette nach Erstellungsreihenfolge
bzw. fest grün/rot für eigen/fremd). Gemeinden, die demselben Bund (Land, Nation, …) angehören, erschienen dadurch
in unterschiedlichen Farben, obwohl sie politisch zusammengehören. Es gab keine Möglichkeit, die Einfärbung auf
eine bestimmte Ebene einzugrenzen.

## Lösung

Gebiete färben sich jetzt standardmäßig nach dem **höchsten Bund**, dem die Gemeinde angehört (Nation > Land >
Gemeinde), und ein Filter grenzt das wahlweise auf eine bestimmte Ebene ein. Gehört eine Gemeinde keinem Bund (auf
der gewählten Ebene) an, bleibt ihre eigene Farbe maßgeblich.

## Implementierung

- **`de.etiennebader.civ.server.level.TerritoryColor`** (neu, civ-server): reine, testbare Funktion
  `groupOf(town, ancestors, level)`, die für eine Gemeinde und ihre Vorfahrenkette (`WorldQueries#ancestorsOf`,
  aufsteigend bis zum höchsten Bund) die Polity liefert, deren Farbe zu verwenden ist. Ohne `level` der höchste
  Vorfahre, mit `level` der Vorfahre dieser Ebene (sonst die Gemeinde selbst). Von Web-Karte und Ingame-Karte
  gemeinsam genutzt, damit beide immer dieselbe Farbe zeigen.
- **`TownService#map(Caller, int, int, Optional<String>)`** (civ-server): neue Überladung mit Ebenen-Filter; jedes
  `MapCell` trägt jetzt zusätzlich `group` (die per `TerritoryColor` aufgelöste Polity-Kennung, leer in der
  Wildnis). Die alte Signatur bleibt erhalten und ruft die neue mit "keine Einschränkung" auf.
- **`WebApi`** (civ-server): `GET /api/map` akzeptiert jetzt `level` als Query-Parameter (z. B.
  `?level=nation`); jeder Kartenchunk trägt zusätzlich `group`. `GET /api/world` liefert zusätzlich `levels`
  (Kennung, Anzeigename, Rang aller konfigurierten Ebenen) für die Filter-Auswahl im Web-Frontend.
- **`index.html`** (Web-Karte): neues Auswahlfeld „Ebene" neben „Ansicht"; `chunkColor` färbt nach `c.group`
  statt `c.town`; `colorOf` sucht die stabile Palettenfarbe jetzt auch unter den Bünden (`world.federations`),
  nicht nur unter den Gemeinden; die Legende zeigt die Gruppen-Namen (Gemeinde oder Bund) statt immer nur
  Gemeindenamen.
- **`MapScreen`** (civ-fabric, Ingame-Karte): Die Kacheln sind jetzt farbiges Beton nach `TerritoryColor`-Gruppe
  (13 Beton-Farben, stabil per Hash der Gruppen-Kennung) statt pauschal grün/orange/rot; eigenes/fremdes Gebiet
  bleibt an Namen und Textfarbe erkennbar. Ein neuer Kompass-Knopf unten rechts wechselt zyklisch durch die
  konfigurierten Ebenen (höchste zuerst, siehe `CivServer#federations()#levels()`).
- Neue Übersetzungsschlüssel `eddies-civ-mod.gui.map_level`, `map_level_highest`, `map_level_hint` in
  `de_de.json`/`en_us.json`.

## Tests

- `civ-server/src/test/java/.../level/TerritoryColorTest.java` (neu): deckt höchste Ebene ohne Bund, höchste
  Ebene mit Land+Nation, expliziten Filter auf jede Ebene sowie unbekannte/eigene Ebene (Rückfall auf die
  Gemeinde) ab.
- `./gradlew :civ-server:test :civ-application:test :civ-domain:test :civ-fabric:compileJava` – alles grün
  (Build-Umgebung brauchte JDK 25 für Loom/`release=25`; lokal reicht die normale Projekt-Toolchain).
- Kein Minecraft-Client-Test (Ingame-Karte manuell) möglich in dieser Umgebung.
