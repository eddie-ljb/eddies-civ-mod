# Issue #84 – Polizei und Militär

## Umgesetzt
- **Polizei** (`civ-domain/.../police`, `PoliceService`, `police.tsv`): Dienstgrade Kadett → Polizist → Wachtmeister →
  Kommissar mit Befehlskette (`PoliceRank`). Nur höhere Ränge ernennen, befördern und entlassen niedrigere; der
  Bürgermeister darf alles. Festnehmen und Fesseln ab Polizist; Freilassen ab Wachtmeister. Vorgesetzte und der
  Bürgermeister sind nicht festnehmbar.
- **Besetzung ohne Spieler:** Die Gemeinde stellt täglich Dorfbewohner (ordnungsliebend, wehrhaft) bis etwa ein Achtel der
  Einwohner ein (höchstens 12) und befördert sie (ein Kommissar, je vier Mann ein Wachtmeister, je zwei ein Polizist).
  Spieler werden nie automatisch befördert. Das Militär besetzt sich schon seit #27 selbst (`WarService.villagersEnlist`).
- **Gesetze:** `police_wage` (Sold, je Rang vielfach, aus der Gemeindekasse; Dorfbewohner kündigen, wenn nicht gezahlt
  wird), `police_patrol` (Streife an/aus), `police_arrest_without_report` (Festnahme ohne Anzeige).
- **Kriminalität** (`Criminality`): 0–30 aus Bildung, Ordnung/Freiheit, Stärke und Verbundenheit, im Durchschnitt gering.
  Daraus ergibt sich täglich eine kleine Tatwahrscheinlichkeit (Diebstahl, Hausfriedensbruch, Körperverletzung, Mord).
  Zeugen (60 %) oder eine Streife bringen die Tat zur Anzeige. Die Streife nimmt täglich so viele Dorfbewohner fest, wie
  Polizisten im Dienst sind – nur wenn eine Zelle frei ist; Anzeigen verfallen nach 7 Tagen.
- **Zellen und Haft:** Der Bürgermeister baut 0–20 Zellen. Festgenommene kommen in Untersuchungshaft (Dauer nach Tat);
  ohne freie Zelle bleiben sie nur gefesselt. Haft endet automatisch.
- **Handschellen:** Gefesselte (1 Tag) und Inhaftierte können nicht abbauen, setzen, benutzen oder schlagen
  (`ProtectionListener`, Spieler). Villager-Polizei setzt dieselben Regeln über die Haft durch.
- **Befehle:** `/civ polizei status|dienen|kuendigen|ernennen <Name> kadett|polizist|wachtmeister|kommissar|entlassen <Name>|zellen <n>|anzeigen [<Name> diebstahl|hausfriedensbruch|koerperverletzung|mord]|festnehmen|fesseln|loesen|freilassen <Name>`.

## Noch offen (aus dem Issue, nicht Teil dieses Schritts)
- Handschellen als Gegenstand (aktuell per Befehl), Haft per Gerichtsurteil (Anbindung an `justice`), Staatsanwälte/Richter
  aus Villagern per Verfassung, Beweise/Zeugen durch Dorfbewohner, Gefängnis-/Streifen-Darstellung im Spiel.
- Web: Kriegskarte mit Fronten, Feldzugplaner, Live-Stände und Zeitungsberichte; Militärhierarchie über Soldat/Offizier/Ausspäher hinaus.

## Testen
1. Gemeinde gründen, zweiten Spieler beitreten lassen. `/civ polizei dienen`, `/civ polizei zellen 2`.
2. `/civ polizei ernennen <Spieler> polizist`; `/civ polizei anzeigen <Name> diebstahl`; als Polizist `/civ polizei festnehmen <Name>`.
   Der Festgenommene kann nichts abbauen/schlagen; `/civ polizei freilassen <Name>` als Wachtmeister/Bürgermeister.
3. Mit Dorfbewohnern: Tage vergehen lassen, `/civ polizei status` zeigt eingestellte Beamte und Zellen.
Automatisch: `./gradlew :civ-domain:test :civ-server:test` (`PoliceRegistryTest`, `PoliceServiceTest`).
