# Issue #27 – Krieg (erste Ausbaustufe)

## Umgesetzt
- **Domain** (`civ-domain/.../war`): `WarRegistry` (Krieg, Garnisonen, Neutralität), `BattleResolver` (deterministisch),
  `PeaceTerms` (Reparationen). Vergleich der Gefechtsstärke jetzt ganzzahlig (Heimvorteil 110 %), damit ein exakter
  Gleichstand tatsächlich als `DRAW` erkannt wird – mit Gleitkomma (`1.1`) trat er praktisch nie ein.
- **Server** (`WarService`): Kriegserklärung, Dienst, Gefecht, Frieden und Neutralität nur durch bzw. für Bürgermeister;
  täglicher Sold aus der Gemeindekasse (bei leerer Kasse Entlassung); kriegerische Dorfbewohner
  (`Personality.peace() <= -20`, Offizier ab `<= -60`) melden sich im Krieg selbst; Reparationen vom Verlierer an den Sieger.
- **Befehl** `/civ krieg ...`, neues Gesetz `neutrality_fee_per_day`, Übersetzungen de/en.

## Bewusst nicht umgesetzt (Folgearbeit)
Gebietseroberung, mehrteilige Feldzüge/Schlachtplanung, Ausspäher, Frontverlauf im Spiel und im Web, Ausrüstung/Training,
Lazarett mit Ausfallzeit, Allianzen/Bündnisse, Abhängigkeit des Kriegswillens von Parteien/Ansprüchen, Persistenz des
Kriegszustands über Neustarts, Jobsuche aller Villager.

## Testen
1. Zwei Gemeinden gründen (`/civ gruenden`), `/civ krieg erklaeren "<Gemeinde>"`.
2. `/civ krieg dienen soldat|offizier` auf beiden Seiten, `/civ krieg liste` zeigt Garnisonsstärken.
3. `/civ krieg gefecht 1` entscheidet den Krieg; `/civ krieg frieden 1` beendet ihn ohne Sieger.
4. Gemeinde in einem Bund: `/civ krieg neutralitaet 5`, danach ist eine Kriegserklärung gegen sie abgelehnt.
