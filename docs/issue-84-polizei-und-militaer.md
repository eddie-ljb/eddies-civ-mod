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

- **Handschellen als Gegenstand:** Item „Handschellen“ (Rezept: 3 Eisenbarren-/Nuggets-Muster `NIN`/`N N`). Rechtsklick mit
  Polizeirang ab Polizist fesselt Spieler und Dorfbewohner, ein zweiter Rechtsklick löst sie. Dieselben Rang- und
  Befehlskettenregeln wie bei den Befehlen (`ProtectionListener`, `PoliceService.handcuff`).
- **Haft per Gerichtsurteil:** Jede Festnahme eröffnet ein Strafverfahren (die Gemeinde klagt, gebührenfrei) mit
  Polizeibericht als Beweis (`CaseLink`, `CaseListener`). Der Richter legt Haft mit `urteil <Nr> schuldig haft <Tage> | …`
  fest (höchstens 60), sonst gilt die Regelhaft der Tat. Rechtskräftig: Schuldspruch → Zelle (oder gefesselt, wenn keine frei),
  Freispruch → frei.
- **Richter/Staatsanwälte per Verfassung:** Gesetz `court_staffing` = `head` (Standard, Oberhaupt), `government` (jeder
  Amtsträger ernennt), `automatic` (Gemeinde bestimmt den gebildetsten Dorfbewohner) oder `election` (Dorfbewohner wählen
  nach Eignung). Bei `automatic`/`election` ist Ernennen gesperrt; Vakanzen füllt `CourtStaffing` täglich (1 Stelle, ab 24
  Mitgliedern 2). Villager waren bereits Richter, Geschworene und Zeugen; neu ist die Besetzung per Verfassung.
- **Zeugen und Beweise durch Dorfbewohner:** Dorfbewohner sehen Taten (je 60 %, bis 3 Zeugen), zeigen sie an und werden nach
  Eröffnung automatisch als Zeugen geladen (sie sagen sofort aus). Das Verfahren erhält den Polizeibericht als Beweis.
- **Militärhierarchie:** Soldat/Ausspäher < Unteroffizier < Offizier < Hauptmann < General (`MilitaryRank.level`). Höhere Ränge
  befördern nur Untergebene und nur unter ihren eigenen Rang; nur ein General je Garnison. Hauptmann und General planen
  Feldzüge, Dorfbewohner werden automatisch befördert (ab 10 Mann ein General, je 8 ein Hauptmann …). `/civ krieg befoerdern <Name> soldat|unteroffizier|offizier|hauptmann|general`.
- **Web (Seite „Krieg“):** Karte mit beiden Gebieten und rot umrandetem Frontabschnitt, Feldzugplaner (`POST /api/war/campaign`),
  Live-Stände der Feldzüge (Aktualisierung alle 10 s), Schlachtenbuch je Krieg (`war.tsv`, `LOG`).
- **Zeitungen:** Kriegserklärung, Schlachten, Frieden und Sieg erscheinen in jeder Zeitung mit Dorfbewohner-Redakteur.

- **Spieler bei `election`:** Spieler kandidieren freiwillig mit `/civ gericht kandidieren` (Ebenen-Varianten wie bei `richter`),
  `zurueckziehen` nimmt es zurück (`court_candidacies.tsv`). Die Dorfbewohner wählen unter allen Dorfbewohnern und den
  kandidierenden Spielern (Spieler zählen mit neutraler Eignung 50); wer gewählt wird, nimmt den Sitz ein.
- **Berufung:** Legt die unterlegene Seite Berufung ein (auch an ein höheres Gericht), wandert die Verknüpfung auf das neue
  Verfahren (`PoliceRegistry.moveLink`); Zeugen werden dort neu geladen, das Urteil der Vorinstanz vollstreckt nichts mehr.
  Urteil, Haft und Freilassung folgen dem Berufungsurteil.
- **Gefesselte Dorfbewohner stehen still:** Solange sie gefesselt oder inhaftiert sind, ist ihre KI aus (`NoAI`, alle 2 s
  abgeglichen; Markierung `eddies_civ_cuffed` überlebt Neustarts und wird beim Laden aufgehoben, wenn die Fessel verfallen ist).

## Testen
1. Gemeinde gründen, zweiten Spieler beitreten lassen. `/civ polizei dienen`, `/civ polizei zellen 2`.
2. `/civ polizei ernennen <Spieler> polizist`; `/civ polizei anzeigen <Name> diebstahl`; als Polizist `/civ polizei festnehmen <Name>`.
   Der Festgenommene kann nichts abbauen/schlagen; `/civ polizei freilassen <Name>` als Wachtmeister/Bürgermeister.
3. Handschellen craften (`/give @s eddies-civ-mod:handcuffs`), mit Polizeirang einen Spieler anklicken. `/civ gericht fall <Nr>` zeigt das Verfahren; `urteil <Nr> schuldig haft 5 | …`.
4. Verfassung: Gesetz `court_staffing` auf `automatic` oder `election` setzen, Tage vergehen lassen, `/civ gericht` zeigt Richter und Staatsanwalt.
5. Krieg: `/civ krieg befoerdern <Name> hauptmann`; Webseite „Krieg“ öffnen (Karte, Planer, Live).
6. Mit Dorfbewohnern: Tage vergehen lassen, `/civ polizei status` zeigt eingestellte Beamte und Zellen.
Automatisch: `./gradlew :civ-domain:test :civ-server:test` (`PoliceRegistryTest`, `PoliceServiceTest`).
