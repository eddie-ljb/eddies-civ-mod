[Home](Home.md) › Recht, Verfassung und Bünde

# Recht, Verfassung und Bünde

## Recht (Regelungen)
Der Bürgermeister (oder mit Abstimmungen die Bürger) stellt diese Regelungen im Menü **Recht** des Gemeindehauses ein:

| Regelung | Bedeutung |
|---|---|
| Grundsteuer pro Block und Tag | in Hundertstel Smaragd |
| Steuerfreie Blöcke je Inhaber | Freibetrag auf die Gesamtfläche eines Inhabers |
| Steuerfreie Tage nach Erwerb | Schonfrist nach dem Kauf eines Grundstücks |
| Botschaften zahlen Grundsteuer | ja/nein |
| Höchstzahl an Außenposten | |
| Fremde dürfen bauen / benutzen / Behälter öffnen | ja/nein |
| Explosionen zerstören Blöcke / Feuer verbrennt Blöcke | ja/nein |
| Rechtsänderungen per Abstimmung | ja/nein |
| Dauer einer Abstimmung in Tagen | Standard 2 |
| Umsatzsteuer auf Ladenverkäufe in Prozent | 0–50 |
| Amtszeit gewählter Ämter in Tagen (`term_days`) | 0 = unbefristet (Standard) |
| Dauer einer Wahl in Tagen (`election_days`) | Standard 2 |
| Stimmgewicht von Dorfbewohnern in Prozent (`villager_vote_percent`) | 0 = kein Wahlrecht (Standard) |
| Sitze im Parlament (`parliament_seats`) | 0 = kein Parlament (Standard), bis 99 |
| Sitzungen alle … Tage (`session_days`) | 1–7, Standard 1 |
| Gewählt wird nur an der Wahlurne (`ballot_box_required`) | ja/nein, Standard nein |
| Wahlverfahren, Sperrklausel, Amtszeitbegrenzung, Wahlpflicht, Debatten | siehe [Politik](Spieler-Politik.md#wahlverfahren) |
| Pressefreiheit (`press_freedom`) | aus: kritische Artikel über die Gemeinde/ihre Amtsträger erscheinen dort nicht |
| Geschworene je Strafprozess (`jury_size`) | 0–12, Standard 5 |
| Höchste Geldstrafe (`max_fine`) | Hundertstel Smaragd, Standard 5000 |
| Verbannung erlaubt (`exile_allowed`) | ja/nein, Standard ja |
| Gerichtsgebühr (`court_fee`) | Hundertstel Smaragd, Standard 100 |

Zusätzliche Spielregeln aus dem Gesetzes-Baukasten:

| Regel | Wirkung |
|---|---|
| `pvp_allowed` (Standard ja) | Spieler können einander im Gebiet nicht verletzen, wenn nein |
| `hostile_mobs_allowed` (Standard ja) | Feindliche Monster verschwinden im Gebiet alle 5 Sekunden, wenn nein; benannte bleiben |
| `outsiders_may_hurt_animals` (Standard nein) | Fremde können Tiere nicht verletzen |
| `plot_rights_override` (Standard ja) | Grundstücksinhaber dürfen auf ihrem Grundstück abweichend von Gemeinde-, Länder- und Nationenrecht einstellen, wer bauen/benutzen darf und ob PvP, Monster, Explosionen, Feuer und Tierschutz gelten (`/civ grundstueck rechte`). Bei nein gilt das Recht auch auf Grundstücken: Was es Fremden erlaubt, ist überall erlaubt |
| `federal_levy_percent` (0–10, nur im Bund) | Bundesumlage: jedes Glied zahlt täglich diesen Anteil seiner Kasse an den Bund |

## Gesetzbuch (Item)
Neben den Regelungen gibt es **Paragraphen**: Titel plus frei formulierter Text. Was das Spiel im Text erkennt, setzt es als Regelung durch; der Rest gilt als Text und wird Sache der Gerichte.
- **Erkannt werden** (Deutsch und Englisch, auch ohne Umlaute): Explosionen/TNT, Feuer, Fremdenrechte, Grundsteuer, steuerfreie Blöcke/Tage, Botschaftssteuer, Umsatzsteuer, Außenposten-Limit, Amtszeit, Wahl-/Abstimmungsdauer, Wahlrecht der Dorfbewohner, Abstimmungspflicht, Verfassungsmehrheit, Vorrang des Bundesrechts. Beispiele: „Explosionen sind verboten.“, „Die Umsatzsteuer beträgt 10 %.“
- **Erlassen:** `/civ gesetz erlassen <Titel> | <Text>`, oder Buch mit Feder beschreiben und `/civ gesetz erlassen <Titel>` (erste Zeile = Titel im Menü). `/civ gesetz pruefen <Text>` zeigt vorher, was erkannt würde.
- **Wer?** Ohne Abstimmungspflicht erlässt die Spitze sofort; sonst (oder wenn ein ausgerufener Verfassungsartikel berührt ist) wird ein Antrag daraus (Verfassungsmehrheit). Aufheben folgt denselben Regeln.
- **Bundesrecht bricht Gemeinderecht:** Regelt ein Bund etwas ausdrücklich und gilt `federal_law_prevails` (Standard ja), gilt sein Wert in allen Gliedern bei Gebietsschutz, Explosionen, Feuer, PvP, Monstern/Tieren, Steuern, Außenposten-Limit und Dorfbewohner-Zufriedenheit. Verfahrensregeln bleiben Sache jeder Ebene.

## Verfassung (Block)
Jede Gemeinde und jeder Bund hat eine Verfassung. Anfangs ein **Entwurf**: Die Spitze (Bürgermeister/Vorsitz) legt Präambel und Artikel frei fest. Mit dem **Ausrufen** tritt sie in Kraft; danach steht die Präambel fest, Artikel ändern sich nur noch durch Abstimmung mit der Verfassungsmehrheit (Standard 66 %).
- **Verfassungsartikel:** `laws_by_vote`, `vote_days`, `term_days`, `election_days`, `villager_vote_percent`, `amendment_majority_percent` (50–100), `federal_law_prevails`.
- **Block „Verfassung“** (Rezept: Goldbarren, Buch und Feder, Feder, Schwarzeichenbretter): Rechtsklick zeigt die Verfassung der Gemeinde vor Ort. Die Spitze ruft per Umschalt-Klick aus. Auch im Gemeindehaus unten mittig.
- **Abschrift:** „Abschrift nehmen“ im Menü gibt ein Item, das per Rechtsklick diese Verfassung überall öffnet.

## Bünde und Ebenen
Gemeinden schließen sich zu **Bünden** zusammen: Länder, Nationen und eigene Ebenen wie Kreis oder Bund (`federation_levels`). Eingebaut: Gemeinde (Rang 0), Land (20), Nation (30); ein Bund steht immer höher als jedes Glied.
- **Gründen:** `/civ bund gruenden <Ebene> <Name>` gründet einen Bund über der eigenen Gemeinde/dem eigenen Bund.
- **Aufnehmen:** `/civ bund einladen <Gemeinde oder Bund>`, Beitritt mit `/civ bund beitreten <Bund>`. Einladungen gelten bis zum Neustart.
- **Mitglieder:** Alle Bürger und Dorfbewohner der Glieder gehören automatisch auch zum Bund und allen darüberliegenden Bünden.
- **Austreten/Auflösen:** `/civ bund austreten` (oberste selbst geführte Ebene; letztes Glied kann nicht austreten), `/civ bund aufloesen` (Kasse geht an den Vorsitz).
- **Befehle je Ebene:** `/civ gemeinde|land|nation …` bzw. `/civ ebene <Ebene> …`: `info`, `menue`, `aemter`, `wahl …`, `amt …`, `parlament`, `recht [setzen]`, `abstimmungen`, `abstimmen`, `verfassung [ausrufen]`, `gesetze`, `umfrage`, `wahlergebnis`. `/civ ebenen` zeigt die eigene Kette.
- Bünde haben eigene Organe: **Vorsitz** (statt Bürgermeister) und **Schatzmeister**, gewählt von allen Bürgern der Glieder.
