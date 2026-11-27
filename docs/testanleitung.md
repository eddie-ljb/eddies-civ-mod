# Testanleitung Eddies Civ Mod (Version 1.0.9)

Stand: Funktionsumfang laut README, Wiki und Changelog. Jeder Test hat **Schritte** und ein **erwartetes Ergebnis** (→). Die Reihenfolge baut aufeinander auf: Kapitel 0–3 zuerst, danach beliebig.

## 0. Vorbereitung

**Testumgebung**
- Fabric-Server Minecraft 26.3, Fabric Loader ≥ 0.19.5, Fabric API ≥ 0.164.0, Java 25. Das JAR liegt auf Server **und** Client.
- Mindestens **zwei Spielerkonten** (A = Bürgermeister, B = Fremder/Bürger). Ein drittes (C) hilft bei Wahlen, Justiz und Polizei.
- A ist Operator (Spielleiter). Kreativmodus für den Materialtest, später Überlebensmodus.
- Beschleunigung: `/civ admin tag` beendet den Spieltag sofort. Für längere Abläufe `ticks_per_day` in `config/eddies-civ-mod.properties` verkleinern (z. B. 2400).
- Mehrere Dorfbewohner (Villager) in der Nähe, am besten 10 und mehr.
- Browser für die Webseite (Port 8765).

**Automatische Tests**
```bash
./gradlew test
./gradlew build
```
→ Alle Tests grün, JAR entsteht in `civ-fabric/build/libs`.

**Start-Check**
1. Server starten. → Keine Fehler im Log, Datei `config/eddies-civ-mod.properties` wird angelegt, `<Welt>/data/eddies-civ-mod/civ.sqlite` entsteht.
2. Neuer Spieler betritt die Welt. → Er bekommt das Civ-Handbuch. `/civ handbuch` öffnet es erneut.
3. `/civ hilfe` → Überblick; Tab-Vervollständigung zeigt bei Argumenten Vorschläge mit Erklärung.
4. Englischer Zwilling: `/civ court case 1`, `/civ company list`. → Gleiche Funktion wie die deutschen Befehle.
5. Kreativ-Inventar → Reiter **Eddies Zivilisation** enthält alle Blöcke, den Grundstücksstab und die Test-Items.

---

## 1. Gemeinde, Gebiet, Mitglieder

| # | Schritte | Erwartung |
|---|---|---|
| 1.1 | Gemeindehaus craften (Smaragd, Steinziegel, Glocke: `ESE/SBS/ESE`), im Amboss benennen, aufstellen | Chunk wird Kerngebiet, Menü zur Wahl von Kultur und Rasse erscheint |
| 1.2 | Gemeindehaus abbauen versuchen | Block ist unzerstörbar |
| 1.3 | Rechtsklick auf das Gemeindehaus | Menü: Steckbrief, Mitglieder, Recht, Kasse, Karte, Chunk beanspruchen, Grundstücke, Abstimmungen, Beitreten/Austreten, Auflösen |
| 1.4 | Gebiet betreten/verlassen | Gemeindename als Titel; auf Grundstücken Inhaber und ggf. Preis über der Hotbar |
| 1.5 | `/civ info`, `/civ liste`, `/civ gemeinde [Name]`, `/civ grenzen` | Daten stimmen, Grenzen 10 s als Partikel |
| 1.6 | B: `/civ beitreten <Name>`, dann `/civ austreten` | Mitgliedschaft wechselt, Ämter, Kandidatur und Stimme verfallen beim Austritt |
| 1.7 | `/civ karte`: Linksklick = Kerngebiet, Rechtsklick = Außenposten | Chunk wird beansprucht, Claim-Limit (`claim_base` + `claim_per_member`) greift |
| 1.8 | `/civ beanspruchen`, `/civ freigeben` | Beanspruchen/Freigeben des aktuellen Chunks |
| 1.9 | Gemeindehaus verlegen: neues Haus im eigenen Gebiet setzen | Altes verschwindet und landet im Inventar |
| 1.10 | Auflösen: im Menü zweimal Shift-Klick (nur Bürgermeister) | Gemeinde ist weg; B kann nicht auflösen |
| 1.11 | Kultur/Rasse: `/civ kultur`, `/civ kultur setzen <Kultur> <Rasse>`, `/civ kultur menue` | Kleidung der Dorfbewohner folgt der Kultur, Körpergröße der Rasse (Zwerg 0,8, Elf 1,15, Ork 1,25) |
| 1.12 | Siedlungsstufe: Einwohner auf 10 / 30 / 80 bringen | Marktgemeinde / Stadt / Metropole, je +8 Chunks Gebiet |
| 1.13 | Admin: `/civ gruenden <Name>` | Gemeinde ohne Haus (nur mit Recht `found`) |

## 2. Gebietsschutz

Testen mit B als Fremdem im Gebiet von A, Gesetze auf Standard.

| # | Test | Erwartung |
|---|---|---|
| 2.1 | B baut, öffnet Truhe, benutzt Tür/Knopf/Bett | Alles blockiert |
| 2.2 | B schlägt Tiere/Dorfbewohner, ändert Rüstungsständer/Rahmen/Bilder | Blockiert; Handel mit Dorfbewohnern bleibt erlaubt |
| 2.3 | TNT/Creeper/Feuer | Zerstört keine Blöcke |
| 2.4 | Kolben über Gebietsgrenze, Enderman | Kein Blockbewegen, Enderman nimmt nichts |
| 2.5 | Recht-Menü: „Fremde dürfen bauen/benutzen/Behälter“ auf ja | Wirkt sofort |
| 2.6 | A: `/civ admin umgehen` | Schutz für A aus/an |
| 2.7 | Gesetz `pvp_allowed=nein` | Spieler können sich nicht verletzen |
| 2.8 | `hostile_mobs_allowed=nein` | Monster verschwinden alle 5 s, benannte bleiben |
| 2.9 | `outsiders_may_hurt_animals=ja` | Fremde dürfen Tiere verletzen |

## 3. Geld, Bank, Markt, Läden

| # | Schritte | Erwartung |
|---|---|---|
| 3.1 | Bank craften (`GIG/IEI/SSS`), Rechtsklick, Smaragde ein-/auszahlen | Konto in Hundertsteln; `/civ konto` stimmt |
| 3.2 | `/civ einzahlen 5`, `/civ abheben 2`, `/civ ueberweisen B 2,5`, `/civ ueberweisen <Gemeinde> 1` | Beträge wie 0,25 gehen |
| 3.3 | `/civ kasse einzahlen 5`, `kasse entnehmen 2` | Entnehmen nur Bürgermeister/Schatzmeister |
| 3.4 | Markt craften (`WRW/PEP/PCP`): Stapel anklicken, Rechtsklick, Lager-Klick, Shift-Klick | Ganzer Stapel / 1 Stück / kaufen 1 / kaufen 16 |
| 3.5 | `/civ preis`, `/civ verkaufen`, `/civ kaufen 5 <Item>` | Preise: mehr Lager = billiger, Ankauf/Verkauf 10 % Abstand |
| 3.6 | `/civ admin tag` | Lager schrumpft 5 %; verzaubert/umbenannt/beschädigt wird nicht angekauft |
| 3.7 | `price_level_min/max` / Geldmenge ändern | Preisniveau bleibt zwischen 50 % und 400 % |
| 3.8 | Laden: Kiste ansehen, Item in der Hand, `/civ laden eroeffnen 1,5`; Ware einlegen | Nur, wo gebaut werden darf |
| 3.9 | B klickt die Kiste | Kaufmenü 1/16/64, Geld geht an A, Umsatzsteuer an die Gemeinde |
| 3.10 | B baut die Ladenkiste ab / setzt Trichter darunter | Nicht möglich |
| 3.11 | `/civ laden liste`, `/civ laden schliessen` | Übersicht, Laden wird geschlossen |
| 3.12 | Währungen: `/civ waehrung erstellen EDD € 1 Edel`, `kurs`, `item`, `liste`, `umrechnen` | Kurs zu Smaragd, Auszahlungs-Item |
| 3.13 | Zentralbank: `vorsitz`, `leitzins` | Nur Gründer/Vorsitz |
| 3.14 | Kredite: `/civ kredit anbieten B 10 5`, B `annehmen`/`ablehnen <Kennung>`; `/civ bank selbstbedienung an` | Angebot 7 Tage gültig, Zins nach Leitzins bei Annahme |
| 3.15 | `/civ kredit als <Firma> nehmen …` | Nur Firmenleitung |
| 3.16 | Gesetz `default_currency` | Dorfbewohner erwirtschaften in dieser Währung |

## 4. Grundstücke, Straßen, Adressen

| # | Schritte | Erwartung |
|---|---|---|
| 4.1 | Grundstücksstab craften (Stock + Smaragd diagonal); Linksklick = Ecke 1, Rechtsklick = Ecke 2 | Partikel zeigen die Auswahl |
| 4.2 | **Straße zuerst:** Ecken setzen, `/civ strasse anlegen Hauptstr`, `strasse liste`, `strasse aufheben` | Straße eingetragen |
| 4.3 | `/civ grundstueck erstellen wohnen` (auch gewerbe, industrie, landwirtschaft, oeffentlich, botschaft) | Grundstück entsteht nur ≤ 12 Blöcke von einer Straße (`street_required`) |
| 4.4 | `/civ grundstueck chunk wohnen` | Ganzer Chunk als Grundstück |
| 4.5 | `/civ grundstueck anbieten 12,5`; B: `kaufen` oder Shift-Klick im Menü | Geld fließt, B ist Inhaber; `zurueckziehen` nimmt das Angebot zurück |
| 4.6 | `/civ grundstueck vergeben B`, `aufloesen` | Verschenken/Auflösen nur Bürgermeister/Bauamt |
| 4.7 | `/civ grundstueck` | Info und Umriss als Partikel |
| 4.8 | `/civ grundstueck rechte` mit `mitglieder|fremde bauen|benutzen|behaelter ja|nein`, `gast B bauen ja`, `gastentfernen`, `regel pvp erlauben`, `zuruecksetzen` | Nur Inhaber; Rechte wirken auf dem Grundstück |
| 4.9 | Gesetz `plot_rights_override=nein` | Gemeinderecht gilt auch auf dem Grundstück |
| 4.10 | Grundsteuer setzen, `/civ admin tag` | Steuer pro Block, Freibetrag und Schonfrist wirken |
| 4.11 | `/civ adresse`, `/civ adressen` | Hausnummern ungerade/gerade je Straßenseite, Postleitzahl |
| 4.12 | Straßen-/Hausnummernschilder aufstellen, Amboss-Name, `|` für 3 Zeilen | Beschriftung auf dem Block |
| 4.13 | Firma kauft Grundstück: `/civ grundstueck kaufen "<Firma>"`, `uebertragen "<Firma>"`; im Menü Smaragdblock = Firmenwahl | Preis aus Firmenkasse, nur Leitung |
| 4.14 | Verträge siehe Kapitel 15 (Miete) | |

## 5. Bauen (Pläne, Bauaufträge, Hologramm)

| # | Schritte | Erwartung |
|---|---|---|
| 5.1 | `/civ bau` | Liste der 59 eingebauten Pläne |
| 5.2 | `/civ bau vorschau <Plan> [Drehung]` auf eigenem Grundstück; `vorschau aus` | Blaues Hologramm 45 s, nur für dich sichtbar; Wasser meldet Fehler |
| 5.3 | Firma mit Branche `bau` anlegen, Angestellte einstellen | |
| 5.4 | `/civ bau beauftragen <Plan> "<Firma>" [material firma|selbst] [Grad]` | Auftrag entsteht; Bauamt genehmigt/lehnt ab |
| 5.5 | `/civ bau material <Nr>` bei `selbst` | Bauherr liefert Baustoffe, zahlt nur 40 % |
| 5.6 | Baustelle beobachten | Arbeiter laufen hin; Fundament bis zum Boden (≤ 24 Blöcke), Gelände wird abgetragen, Tür auf Straßenhöhe und zur Straße gedreht, Bauabsperrung um die Baustelle, nichts Gebautes wird überschrieben |
| 5.7 | Baufeld blockieren (3 Tage) | Auftrag wird abgebrochen, Meldung an Bauherrn |
| 5.8 | Wassergebäude (`leuchtturm`, `schiff`) | Nur sie dürfen auf Wasser, ohne Fundament |
| 5.9 | Eigener Plan: `.litematic`/`.schem` auf Webseite „Bauen“ hochladen oder in `blueprints/` legen | Plan erscheint in der Liste |
| 5.10 | `/civ bau planung` | Zeigt, wer plant, Streifen/Chunks, Ablehnungsgründe |
| 5.11 | Bauabsperrung craften (2 Reihen Stock, oranger Farbstoff, Stock → 6) und von Hand setzen | Zaun in Warnfarben |
| 5.12 | Wegebau: `/civ strasse bauen schaufel|pflaster <Name>`, `/civ strasse wege` | Weg folgt dem Gelände, gepflastert zahlt die Kasse nach Fortschritt |
| 5.13 | Spieler weit weg vom Bauplatz | Chunks werden vom Server geladen, höchstens 24 neue je Tick, kein Ruckeln |

## 6. Dorfbewohner

| # | Test | Erwartung |
|---|---|---|
| 6.1 | Schleichen + Rechtsklick mit leerer Hand auf Villager | Menü: Name, Registernummer, Persönlichkeit (3 Achsen), Zufriedenheit, Auskunft (Gemeinde, Loyalität, Bildung, Partei, Anschrift, Arbeitgeber, Familie, Post), Knöpfe Reden/Beeinflussen/Post |
| 6.2 | `/civ dorfbewohner info`, `menue`, `register [Seite]` | Chat-Auskunft bzw. Menü bzw. Liste |
| 6.3 | Villager betritt Gemeindegebiet | Wird Einwohner; stirbt er, tritt er aus |
| 6.4 | `/civ admin tag` | Jeder Villager bringt Geld in die Kasse (¼ Smaragd); Zufriedene bis +50 %, Unzufriedene bis −50 % |
| 6.5 | `/civ stimmung`, Glocke im Gemeindehaus | Faktoren: Grundstimmung, Ordnung, Steuern, Mitsprache, Wohlstand, Presse |
| 6.6 | Zufriedene/unzufriedene Villager ansehen | Grüne Funken bzw. Gewitterwolken |
| 6.7 | Steuern stark erhöhen, mehrere Tage simulieren (`villager_emigration=true`) | Wer unter −30 fällt, wandert ab (≤ ¼ pro Tag), zieht in zufriedenere Gemeinde oder verschwindet |
| 6.8 | `villager_emigration=false` | Keine Abwanderung |
| 6.9 | Geschlecht: `/civ geschlecht weiblich` | Wird gespeichert; Ausweis zeigt es |
| 6.10 | Villager-Skins (Client) | Erwachsene haben individuellen Skin (Hautton, Haare, Frisur), Beruf/Biom darüber |
| 6.11 | Heirat/Kinder: mehrere Tage simulieren | Paare, nach ca. einer Woche Hochzeit, Kinder 10–30 %/Tag |
| 6.12 | Mehrfach Tage simulieren mit `villager_ambitions=true` | Ehrgeizige Villager (≈ jeder Sechste) gründen Firmen, Parteien, Zeitungen, Religionen, Bünde, Gemeinden, claimen Land (nur ≤ 35 % Wasser), kandidieren, übernehmen Firmen, vermieten Grundstücke |
| 6.13 | `villager_ambitions=false` | Nichts davon passiert |
| 6.14 | Namen neu gegründeter Dinge | Echte Namen („Lindenau“, „Mühlenweg“), nie Zahlen |
| 6.15 | Handschellen an Villager | Gefesselte stehen still |
| 6.16 | Plakat im Umkreis von 4 Blöcken | Villager lesen es, ≤ 3 je Plakat, einmal pro Tag |

## 7. Ämter und Wahlen

| # | Schritte | Erwartung |
|---|---|---|
| 7.1 | `/civ aemter` | Bürgermeister, Schatzmeister, Bauamt; ohne Wahl der dienstälteste Bürger kommissarisch |
| 7.2 | `/civ amt ernennen schatzmeister B`, `amt entlassen` | Nur Bürgermeister |
| 7.3 | A: `/civ wahl ausrufen buergermeister`; B: `kandidieren buergermeister steuern`; C: `wahl stimmen buergermeister B` | Wahl läuft; Stimme änderbar; Wahlgeheimnis |
| 7.4 | Wahlpositionen: Programme `ordnung`, `freiheit`, `steuern`, `ausbau`, `umwelt`, `soziales`, `wirtschaft`, `bildung`, `tradition`, `fortschritt`, `frieden`, `staerke`, `beteiligung`, `landwirtschaft`, `heimat`, `ohne` | Alle werden akzeptiert |
| 7.5 | `/civ admin tag` bis Ende (Standard 2 Tage) | Höchste Stimmenzahl gewinnt; bei Gleichstand Amtsinhaber, sonst wer zuerst kandidierte; ohne Kandidat bleibt alles |
| 7.6 | `term_days=2` | Ämter laufen ab und werden automatisch neu gewählt |
| 7.7 | `villager_vote_percent=100` | Villager wählen mit, wählen nach Persönlichkeit; kandidieren dürfen sie nur dort, wo sie wählen dürfen |
| 7.8 | Wahlverfahren im Recht-Menü durchschalten: `office_voting` (plurality/runoff), `parliament_voting` (sainte_lague, dhondt, hare_niemeyer, majority, districts, mixed), `ballot_type` (person/list), `threshold_percent`, `term_limit`, `compulsory_voting` + `voting_fine`, `election_debates` | Stichwahl bei fehlender Mehrheit; Sperrklausel; Wahlpflicht-Strafe; Amtszeitbegrenzung |
| 7.9 | Wahlurne craften/aufstellen, `ballot_box_required=ja` | Nur Urnenstimmen zählen (nicht Menü/Befehl/Web) |
| 7.10 | Briefkasten + `postal_vote_allowed=ja` | Briefwahl am Briefkasten |
| 7.11 | Wahlbezirke: `/civ wahlergebnis [Name|menue]` | Letzte 10 Ergebnisse je Bezirk |
| 7.12 | `/civ umfrage [Name]` | Villager-Umfrage, bei Parlament mit Sitzprognose |
| 7.13 | Wahlkreise: `/civ gemeinde wahlkreis anlegen Nord`, Stab-Ecken + `wahlkreis gebiet Nord`, `wahlkreis gemeinde Nord "<Gemeinde>"`, `liste`, `leeren`, `aufloesen` | Kreise erscheinen im Ergebnis und auf der Wahlkarte |
| 7.14 | Debatte: `/civ gemeinde debatte starten [Amt]`, `/civ debatte <Text>` | Beiträge wirken wie halbe Rede, am Ende Blitzumfrage; automatisch am letzten Wahltag (≥ 2 Kandidaten) |
| 7.15 | Live-Auszählung nach Wahlschluss | Zwischenstände bei 10/25/45/65/85/100 % als Chat und Bossleiste, dann Ergebnis; mit `live_count=false` sofort |
| 7.16 | Rede: `/civ rede <Text>` als Kandidat | Spieler in 32 Blöcken lesen mit, Villager reagieren 3 Tage, eine wirksame Rede/Tag |
| 7.17 | Wahlversprechen: `/civ versprechen geben Die Umsatzsteuer beträgt 5 %.`, `/civ versprechen [liste [Name]]` | Bilanz gehalten/gebrochen/teilweise/nicht messbar |
| 7.18 | Litfaßsäule aufstellen | Werbend für Kandidat bzw. Partei, Schild mit Namen; Rechtsklick zeigt Ziel; „Leere Säule“ verschwindet nach Rechtsklick |

## 8. Abstimmungen und Recht

| # | Schritte | Erwartung |
|---|---|---|
| 8.1 | Recht-Menü öffnen, Werte ändern | Ohne Abstimmungen ändert nur der Bürgermeister |
| 8.2 | `laws_by_vote=ja`, danach Regel ändern | Änderung wird Antrag; Mitglieder bekommen Meldung |
| 8.3 | `/civ abstimmungen`, `/civ abstimmen <Nr> ja|nein` oder im Menü Linksklick = ja, Rechtsklick = nein | > ½ Zustimmung = sofort gültig; ≥ ½ Ablehnung = gescheitert; sonst nach `vote_days`; Gleichstand = abgelehnt |
| 8.4 | Villager/Einwohner ohne Bürgerrecht | Stimmen nicht mit |
| 8.5 | `/civ recht`, `/civ recht setzen <Regel> <Wert>` | Anzeige/Änderung |

## 9. Verfassung und Gesetzbuch

| # | Schritte | Erwartung |
|---|---|---|
| 9.1 | Verfassungsblock craften (Goldbarren, Buch und Feder, Feder, Schwarzeichenbretter), aufstellen, Rechtsklick | Präambel, Artikel, Organe, Anträge, übergeordnete Bünde |
| 9.2 | `/civ verfassung praeambel <Text>` (≤ 600 Zeichen), `artikel <Regel> <Wert>` | Im Entwurf frei änderbar |
| 9.3 | `/civ verfassung ausrufen` bzw. Umschalt-Klick | Nicht umkehrbar; Präambel fix; Artikeländerung nur per Abstimmung mit Verfassungsmehrheit (66 %) |
| 9.4 | „Abschrift nehmen“ im Menü, Rechtsklick mit Abschrift | Öffnet diese Verfassung überall |
| 9.5 | Gesetzbuch craften (Buch, Goldklumpen, roter Farbstoff), Rechtsklick | Gesetzbuch der Gemeinde, in der du stehst; Zugriff auch im Gemeindehaus |
| 9.6 | `/civ gesetz pruefen Explosionen sind verboten.` | Zeigt erkannte Regelung |
| 9.7 | `/civ gesetz erlassen Sprengverbot \| Explosionen sind verboten.` | Regelung wird wirksam |
| 9.8 | Weitere Texte: „Fremde dürfen weder bauen noch Truhen öffnen.“, „Die Umsatzsteuer beträgt 10 %.“, „Das Gehalt beträgt 2 Smaragde.“ | Werden erkannt, Rest bleibt Text |
| 9.9 | Buch und Feder in der Hand: `/civ gesetz erlassen <Titel>` bzw. Menü „Paragraph aus Buch und Feder“ | Erste Zeile = Titel |
| 9.10 | **Gesetzesstruktur (#83):** Gesetz/Teil im Buch eingeben, §-Nummern je Gesetz, Teile/Paragraphen/Absätze mit eigenen Namen | Nummerierung je Gesetz, Menü und Web zeigen die Struktur |
| 9.11 | `/civ gesetz liste|zeigen <Nr>|aufheben <Nr>|menue` | Aufheben folgt denselben Regeln wie Erlassen (ggf. Antrag) |
| 9.12 | Web „Recht“: Überblick, Editor, Vorlagen speichern | Gesetze verfassen, prüfen, erlassen, aufheben |

## 10. Bünde (Länder, Nationen)

| # | Schritte | Erwartung |
|---|---|---|
| 10.1 | Zwei Gemeinden anlegen. A: `/civ bund gruenden land Nordland` | Eigene Gemeinde ist Glied, A hat Vorsitz |
| 10.2 | A: `/civ bund einladen <Gemeinde2>`; Bürgermeister 2: `/civ bund beitreten Nordland` | Einladung gilt bis Neustart |
| 10.3 | `/civ bund info`, `/civ ebenen`, Steckbrief „Gehört zu“ | Kette Gemeinde › Land › Nation |
| 10.4 | Mitglieder wechseln Gemeinde | Bürger/Villager gehören automatisch zum Bund |
| 10.5 | Nation über dem Land gründen, `federation_levels=kreis:Kreis:10,bund:Bund:40` | Eigene Ebenen mit Rang |
| 10.6 | `/civ land info|menue|aemter|wahl …|recht setzen …|verfassung|gesetze|umfrage`, `/civ nation …`, `/civ ebene <Ebene> …` | Wirkt auf die jeweilige Ebene; Reiter im Menü Gemeinde/Land/Nation |
| 10.7 | `federal_law_prevails=ja`, Bund regelt Explosionen | Gilt in allen Gliedern; Menü zeigt „Bundesrecht von …“ |
| 10.8 | `federal_levy_percent=5`, `/civ admin tag` | 5 % der Kassen gehen an den Bund |
| 10.9 | `/civ bund austreten`, `/civ bund aufloesen` | Letztes Glied kann nicht austreten; Kasse an Vorsitz; leerer Bund verschwindet |
| 10.10 | `/civ admin ebenen`, `/civ admin aufloesen <Name>` | Nur Admin |

## 11. Parteien

| # | Schritte | Erwartung |
|---|---|---|
| 11.1 | `/civ partei gruenden ordnung Ordnungspartei`, `liste`, `info`, `menue` | Eine Partei je Person |
| 11.2 | `/civ partei beitreten`, `austreten` | Letztes Mitglied weg = Partei weg |
| 11.3 | Vorsitz: `richtung 50 -20`, `wappen` (Item in der Hand), `gebiet setzen <Gemeinde>`, `gebiet leeren`, `aufloesen` | Nur Vorsitz |
| 11.4 | Kandidieren ohne eigenes Programm | Tritt mit Parteiprogramm an, Partei steht beim Kandidaten |
| 11.5 | Villager bei `villager_parties=true` simulieren | Treten passenden Parteien bei, gründen ab `villager_party_founders` (5) Gleichgesinnten eine, Vorsitz = Heimatverbundenster |

## 12. Parlament und Regierung (#79)

| # | Schritte | Erwartung |
|---|---|---|
| 12.1 | `parliament_seats=9`, `/civ admin tag` | Parlamentswahl wird automatisch ausgerufen; manuell `/civ wahl ausrufen parlament` |
| 12.2 | Kandidieren/wählen: `/civ wahl kandidieren parlament`, `wahl stimmen parlament <Name|Partei>` | Verhältniswahl nach Sainte-Laguë, Präsidium = stärkster Kandidat der stärksten Partei |
| 12.3 | `/civ parlament`, `zeigen`, `menue` | Sitze, Fraktionen, Präsidium, Tagesordnung |
| 12.4 | Rechtsänderung bei sitzendem Parlament | Wird Antrag, nur Abgeordnete stimmen ab, je Sitz eine Stimme; > ½ Sitze sofort |
| 12.5 | `session_days=3` | Dorfbewohner-Abgeordnete handeln nur an Sitzungstagen, Chat kündigt Tagesordnung an |
| 12.6 | Abgeordneter mit Partei tritt aus | Nächster der Partei rückt nach; Sitz eines Parteilosen bleibt leer |
| 12.7 | `parliament_seats=0` | Parlament löst sich am Tagesende auf |
| 12.8 | Regierung: `/civ regierung` | Oberhaupt, Ministerien, Haushalt |
| 12.9 | `/civ regierung oberhaupt …` (Titel, Staatsform, Gehalt, Modus, Befugnisse Veto/ernennen/Neuwahl), `regeln`, `vorbereiter` | Einstellungen gespeichert |
| 12.10 | `ministerium anlegen/aufloesen/zustaendigkeit/modus/lohn/bereich`, `ernennen`, `abberufen`, `einstellen`, `kuendigen` | Ministerien mit Minister und Angestellten |
| 12.11 | `haushalt entwurf`, `haushalt abstimmen`, `schliessen` | Haushalt gibt jedem Ministerium Etat; Löhne täglich aus der Kasse |
| 12.12 | `koalition bilden`; `koalition regeln <abstand> <minderheit>`; `koalition vorhaben <Regelung> <Wert>`; `koalition entlassen`, `koalition neuwahl` | Stärkste Partei sucht passende Partner (keine Links-Rechts-Bündnisse); sonst zweitstärkste, Minderheitsregierung oder Neuwahl; Ministerien nach Sitzstärke verteilt |
| 12.13 | Ministerium: `ministerium vorschlag`, `ministerium verordnung` | Vorschläge je Bereich (Finanzen, Inneres, Verteidigung, Wirtschaft, Kanzleramt, frei); Verordnung ohne Parlament |
| 12.14 | Veto des Oberhaupts, Forderungen von Dorfbewohnern in Koalitionsverhandlungen | Veto blockiert; Forderungen beeinflussen den Vertrag |
| 12.15 | Ministergehalt: `office_salary` | Bürgermeister, Amtsträger, Abgeordnete bekommen es täglich |

## 13. Justiz

Vorbereitung: A ernennt C zum Richter (`/civ gericht richter ernennen C`), optional Staatsanwalt.

| # | Schritte | Erwartung |
|---|---|---|
| 13.1 | `/civ gericht klage straf B \| Diebstahl`, `klage zivil B 5 \| Schulden`, `klage verfassung §3 \| Verstoß` | Gebühr (`court_fee`) vom Kläger |
| 13.2 | `/civ gericht faelle`, `fall <Nr>`, `akte <Nr>` | Menü/Akte mit Beteiligten, Beweisen, Protokoll |
| 13.3 | Richter: `eroeffnen`, `weiter`, `einstellen`, `urteil <Nr> schuldig 2 verbannen \| Begründung` | Phasen: eingereicht → Beweis → Plädoyers → Beratung → Urteil |
| 13.4 | `verteidiger <Nr> <Spieler|dorfbewohner>`, `zeuge <Nr> …` | Beteiligte hinzugefügt, Villager sagen sofort aus |
| 13.5 | Beweise: `beweis <Nr>` (Buch/Gegenstand in der Hand), `beweis <Nr> vertrag <N>`, `artikel <N>`, `text <Text>` | Verträge wiegen am schwersten |
| 13.6 | `aussage <Nr> <Text>`, `plaedoyer <Nr> <Text>` | Ton (be-/entlastend) wird erkannt |
| 13.7 | `jury_size=5`, Geschworene `stimme <Nr> schuldig|unschuldig` | Jury-Mehrheit entscheidet Schuld, Richter nur Strafe |
| 13.8 | Berufung: `berufung <Nr>` durch Unterlegene | Höheres Gericht, nie derselbe Richter |
| 13.9 | Urteil rechtskräftig nach 2 Tagen (`admin tag`) | Geldstrafe (≤ `max_fine`), Schadensersatz, Verbannung 30 Tage (`exile_allowed`), Amtsenthebung werden vollstreckt |
| 13.10 | Verfassungsklage erfolgreich | Regel/Paragraph wird aufgehoben |
| 13.11 | Richter versäumt Frist | Gericht schaltet am Tagesende selbst weiter |
| 13.12 | `court_staffing=head|government|automatic|election`, `/civ gericht kandidieren`, `zurueckziehen` | Ernennen bei automatic/election gesperrt, freie Stellen füllt die Gemeinde |
| 13.13 | Richterbank craften (Gold, Buch, Gold über 3 Bretter über Brett-leer-Brett), Rechtsklick | Öffnet Gericht der Gemeinde |
| 13.14 | Bund-Gericht: `/civ land gericht klage …` | Verfahren auf Bundesebene |
| 13.15 | Web „Justiz“: Fälle, Akte, Aussagen, Plädoyers, Beweise, Urteile, Geschworenenstimme | Entspricht dem Spielstand |

## 14. Polizei und Militär (#84)

| # | Schritte | Erwartung |
|---|---|---|
| 14.1 | `/civ polizei dienen` (B), `/civ polizei status` | B wird Kadett, A (Bürgermeister) Kommissar |
| 14.2 | `/civ polizei ernennen B polizist`, `entlassen`, `kuendigen` | Befehlskette Kadett → Polizist → Wachtmeister → Kommissar; höhere Ränge befehligen niedrigere |
| 14.3 | `/civ polizei zellen 3` (nur Bürgermeister) | Zellen werden gebaut (0–20) |
| 14.4 | C begeht Straftat; `/civ polizei anzeigen C diebstahl` (auch hausfriedensbruch, koerperverletzung, mord) | Anzeige in `status` |
| 14.5 | Handschellen craften (`NIN/N N` Eisennuggets, -barren), Rechtsklick auf Spieler, zweimal klicken | Fesseln/Lösen; Gefesselte können nichts abbauen, setzen, benutzen, schlagen |
| 14.6 | `/civ polizei festnehmen C` | Zelle frei = Untersuchungshaft, sonst nur gefesselt; Strafverfahren wird eröffnet; Vorgesetzte und Bürgermeister nicht festnehmbar |
| 14.7 | `police_arrest_without_report=ja/nein` | Festnahme ohne Anzeige nur bei ja |
| 14.8 | Richter: `urteil <Nr> schuldig haft 3 \| …` (≤ 60 Tage) | Haft nach Rechtskraft; sonst Regelhaft der Tat; Berufung übernimmt die Verknüpfung |
| 14.9 | `freilassen` ab Wachtmeister | Aus der Zelle entlassen |
| 14.10 | Villager: Polizisten (1 je 8 Einwohner, max. 12), Streife `police_patrol`, Kriminalitätswert, Anzeige durch Nachbarn, Zeugenaussage | Selten Straftaten, Streife nimmt Täter fest |
| 14.11 | `police_wage` | Sold je Rang und Tag |
| 14.12 | Militär: `/civ krieg dienen soldat|offizier|entlassen`, `befoerdern <Spieler> soldat|unteroffizier|offizier|hauptmann|general` | Nur Bürgermeister vergibt jeden Rang, sonst Vorgesetzte unter ihrem Rang; ein General je Garnison |
| 14.13 | Villager-Beförderung | Ab 10 Mann ein General, je 8 ein Hauptmann, je 6 ein Offizier, je 4 ein Unteroffizier |
| 14.14 | Krieg: `/civ krieg erklaeren "<Gemeinde>"`, `liste`, `gefecht <Nr>`, `frieden <Nr>`, `neutralitaet <Tage>` | Offiziere zählen dreifach, Verteidiger 10 % Heimvorteil; Reparationen über `war_reparations_per_strength_point` |
| 14.15 | `krieg beute <Nr>` und `beute <Nr> <Chunks> <Reparationen>` | Siegpunkte (1 je Soldat Vorsprung); Chunk kostet `conquest_points_per_chunk`, max `conquest_chunks_per_win`; nur Grenz-Chunks erobern |
| 14.16 | `soldier_wage`, `officer_wage` | Sold aus der Kasse, bei leerer Kasse Entlassung |
| 14.17 | `war_port_lock` (`all-outsiders`, `enemies`, `none`) | Port in Kriegsgebiete gesperrt oder frei |
| 14.18 | Web „Krieg“: Karte, Frontabschnitt, Feldzugplaner (Hauptmann/General), Live-Stände (10 s), Schlachtenbuch | Stimmt mit dem Spiel überein; Kriegsberichte in Zeitungen |

## 15. Verträge

| # | Schritte | Erwartung |
|---|---|---|
| 15.1 | Miete: A auf eigenem Grundstück `/civ vertrag miete B 1 5 10`; B `annehmen <Nr>` | Kaution beim Unterschreiben, Miete täglich; Mieter darf bauen wie Eigentümer |
| 15.2 | `kuendigen <Nr>` | Endet am nächsten Tag, Kaution zurück |
| 15.3 | Mieter kann nicht zahlen (Konto leeren, `admin tag`) | Vertragsbruch, Kaution verfällt |
| 15.4 | Vermieter verliert Grundstück | Mietvertrag gegenstandslos |
| 15.5 | Arbeit: `/civ vertrag arbeit B 2 10`, `arbeit dorfbewohner 0,5` | Lohn täglich; Villager-Anteil geht an Arbeitgeber |
| 15.6 | Frei: `/civ vertrag frei B 5 1 Titel \| Text` | Einmalig + täglich, Anbieter zahlt |
| 15.7 | Bündnis: `/civ vertrag buendnis <Gemeinde>` | Bürger der Verbündeten dürfen bauen wie Mitglieder |
| 15.8 | `/civ vertrag liste|zeigen|ablehnen|menue` | Menü auch im Gemeindehaus und Web |

## 16. Post, Freunde, Chat

| # | Schritte | Erwartung |
|---|---|---|
| 16.1 | Briefkasten craften (`III/IPI/ICI`), `/civ brief B Hallo \| Text` | Ankunft am nächsten Spieltag; Meldung bei Ankunft |
| 16.2 | Am Briefkasten mit Buch und Feder (erste Zeile = Betreff), Freund anklicken | Brief wird verschickt |
| 16.3 | `/civ post`, `post lesen <Nr>`, `post menue` | Postfach |
| 16.4 | `/civ freund B`, B nimmt per Knopf an; `annehmen`, `ablehnen`, `entfernen` | Freundschaft |
| 16.5 | `/civ freund vertrauen B`, dann B baut auf A's Grundstück | Vertrauter darf bauen, Behälter öffnen, benutzen; `misstrauen` nimmt zurück |
| 16.6 | `/civ freunde`: Klick = Nachricht, Rechtsklick = Vertrauen umschalten | Menü funktioniert |
| 16.7 | `/civ nachricht B Hallo` | Auch wenn B nur im Web ist |
| 16.8 | Chat im Spiel und auf der Webseite | Beidseitig, Web-Nachrichten mit „[Web]“ |

## 17. Firmen und Börse

| # | Schritte | Erwartung |
|---|---|---|
| 17.1 | `/civ firma gruenden 50 Holz AG` | Kapital ≥ 10, 1000 Aktien, Ausgabepreis = Kapital/1000 |
| 17.2 | `firma info|liste|aufnehmen|entlassen` | Mitgliederverwaltung |
| 17.3 | Firmenladen: `/civ laden eroeffnen 2 "Holz AG"` | Einnahmen in die Firmenkasse |
| 17.4 | `firma branche "Holz AG" forst` (auch schaeferei, acker, bergwerk, fischerei, viehzucht, ziegelei, obstbau, bau) | Abendproduktion beim `admin tag`; Ware im Lager oder automatisch an den Markt |
| 17.5 | `firma einstellen "Holz AG" B 2`, `einstellen … dorfbewohner 0,5`, `kuendigen` | Mehr Arbeitskräfte = mehr Ertrag (abnehmender Grenzertrag); Kündigung nach einem Tag |
| 17.6 | `firma investieren "Holz AG" 20` | Stufe 1 = 20, dann 60, 140, 300 …; +25 %/Stufe, max Stufe 8; Branchenwechsel halbiert |
| 17.7 | `firma verkauf automatisch|hand|jetzt`, `lager`, `abholen <Ware> [Anzahl]` | Verkaufsmodi |
| 17.8 | `firma menue` | Alle Knöpfe (Klick 10, Shift 100) |
| 17.9 | `dividende "Holz AG" 10`, `ausgeben "Holz AG" 100 0,1`, `aufloesen` | Je Aktie gleich viel; neue Aktien; Kasse anteilig |
| 17.10 | Börse: `/civ boerse`, `kaufen|verkaufen <Firma> <Anzahl> [Limit]`, `depot`, `buch <Firma>`, `streichen <Nr>`, `menue` | Orders sofort gegen Orderbuch, Rest wartet 7 Tage; ohne Limit ±10 % um den letzten Kurs |
| 17.11 | Börsenterminal craften (`GRG/IEI/III`) | Öffnet Börse |
| 17.12 | Villager investieren, übernehmen Firmen (> 50 %), führen Firmen nach Persönlichkeit | Dividenden alle 7 Tage, Ausbau, Einstellungen, Kündigung bei leerer Kasse |
| 17.13 | Konjunktur / Wirtschaft (Issue 05/06) | Preise, Löhne, Wirtschaftsdaten bewegen sich plausibel über mehrere Tage |

## 18. Presse

| # | Schritte | Erwartung |
|---|---|---|
| 18.1 | `/civ zeitung gruenden Bote`, `redakteur Bote B`, `entlassen`, `aufloesen` | Redaktion |
| 18.2 | `/civ zeitung schreiben Bote Titel \| Text` oder Buch und Feder + `veroeffentlichen Bote` | Max 5 Artikel je Zeitung und Tag; Gegenstand, Haltung, Thema werden erkannt |
| 18.3 | `/civ zeitung lesen`, `artikel <Nr>`, `liste`, `menue` | Ausgabe als Buch |
| 18.4 | Zeitungskiosk craften (`PPP/BGB/WWW`) | Neueste Artikel |
| 18.5 | Kritischen Artikel über die Gemeinde schreiben, Tage simulieren | Zufriedenheit-Faktor „Presse“ sinkt (max ±6); Wahlneigung verschiebt sich 3 Tage |
| 18.6 | `press_freedom=nein` | Kritische Artikel über die Gemeinde erscheinen dort nicht |
| 18.7 | Villager-Redakteure | Schreiben ca. alle 3 Tage Artikel über Kandidaten/Stimmung; Schlagzeile im Chat |

## 19. Religion

(Details im Wiki „Spieler-Religion“.)

| # | Schritte | Erwartung |
|---|---|---|
| 19.1 | Religion gründen/beitreten/austreten, `/civ religion …` | Mitgliederliste, Inbrunst, Oberhaupt |
| 19.2 | `religion segnen|taufe <Spieler|dorfbewohner>` | Segen stärkt Villager 5 Tage; Taufe nimmt auf |
| 19.3 | `religion schrift lesen|schreiben|anhaengen|benennen` (≤ 4000 Zeichen) | Heilige Schrift, Gotteshaus, Sprache umbenennbar |
| 19.4 | `religion amt festlegen "<Name>" segnen taufen gottesdienst schrift`, `vergeben`, `entziehen`, `abschaffen`, `liste` | Klerus-Ämter mit eigenen Rechten, auch für Villager |
| 19.5 | Klerus-Wahl, Gottesdienste, Riten | Ergebnisse in Chat/Web |
| 19.6 | Web „Religion“: Beitreten, Gründen, Kandidieren, Wählen, Schrift-Editor | Spiegelt den Spielstand |
| 19.7 | Villager gründen Religion (Gläubigkeit ≥ 35) | Namen wie „Hain der Quelle“ |

## 20. Hoheitszeichen (Flagge, Wappen, Plakat, Hymne, Logos)

| # | Schritte | Erwartung |
|---|---|---|
| 20.1 | Banner im Webstuhl gestalten, `/civ gemeinde flagge setzen`, `flagge holen`, `flagge entfernen` | Flagge der Ebene gespeichert |
| 20.2 | `/civ flagge editor`: Grundfarbe, bis 16 Muster-Schichten, Vorschau, speichern (nur Spitze), als Banner mitnehmen | Editor funktioniert |
| 20.3 | `/civ zeichen flagge|wappen|plakat leer|farbe <Farbe>|bild <Einheit>` | Block-Item mit Farbe/Bild; Abbau gibt dasselbe Item zurück |
| 20.4 | Flagge/Wappen im Gemeindegebiet aufstellen, `/civ dorfbewohner info` | Treue +1 bzw. +2, zusammen max +8 |
| 20.5 | Eigene Modelle/Texturen | Keine Vanilla-Platzhalter |
| 20.6 | Block vom Server entfernt (z. B. `/setblock`) | Register räumt nach ≤ 30 s auf |
| 20.7 | Hymne: `/civ gemeinde hymne setzen Titel \| harp \| C4 D4 E4*2 - F#4/2`, `hymne spielen`, `entfernen` | Melodie spielt; Rechtsklick auf Banner im Ebenenmenü |
| 20.8 | `anthem_on_enter=true`, Gebiet betreten | Hymne spielt |
| 20.9 | Hymne als Platte: `hymne platte`; Rechtsklick auf Platte/Plattenspieler | Spielt Hymne; Aufnahme (OGG/MP3 ≤ 4 MB) auf Web hochladen; `hymne spielen` nutzt Aufnahme, `hymne noten` Notenblock; `GET /api/anthem-pack.zip` liefert Ressourcenpaket |
| 20.10 | Logos: Web-Seite „Logos“ (Wappen, Plakat, Reklame hochladen, 128×128), `/civ logo <Einheit> wappen|plakat|reklame` | Wappen schwebt über dem Gemeindehaus; Bild als Karte |

## 21. Personalausweis

| # | Schritte | Erwartung |
|---|---|---|
| 21.1 | `/civ ausweis` | Item mit Name, Anschrift, Staatsbürgerschaft, Datum, Geschlecht; neuer ersetzt alten |
| 21.2 | `/civ ausweis dorfbewohner` | Ausweis für nächsten Villager |
| 21.3 | Umziehen/umbenennen, `/civ ausweis pruefen` | Ausweis ist „veraltet“ |

## 22. Webseite (Port 8765)

| # | Schritte | Erwartung |
|---|---|---|
| 22.1 | `http://<Server>:8765` öffnen | Hell/Dunkel, mobil tauglich, gruppierte Navigation (klappt auf dem Handy auf) |
| 22.2 | `/civ web` → Code im Browser eingeben | Anmeldung; Sitzung läuft nach 2 Tagen ohne Nutzung bzw. 7 Tagen ab; `/civ web abmelden` meldet überall ab |
| 22.3 | Seiten durchklicken: Start, Welt (Gemeinden, Bünde, Parteien, Firmen, Zeitungen mit Suche), Karte, Staat, Justiz, Wahlen, Markt, Börse, Presse, Post, Chat, Freunde, Mein Bereich, Recht, Religion, Logos, Bauen, Krieg | Daten stimmen mit dem Spiel überein |
| 22.4 | Karte: Claims je Dimension, Straßen, Ansicht „Wahlkarte“ mit Parteifilter, echte Landschaft (`world_map=true`, Radius 6) | Kacheln erscheinen nach einigen Minuten; Ordner `<Welt>/data/eddies-civ-mod/map/` |
| 22.5 | Staat: Live-Auszählung, Debatten, Flaggen, Hymnen abspielen | |
| 22.6 | Mein Bereich: Überweisen, Verträge, Wählen, Abstimmen | Wirkt im Spiel |
| 22.7 | Sicherheit: Rate-Limit (600 lesend / 120 schreibend je Minute), Fehlversuche (10 je Adresse), Herkunftsprüfung, CSP-Header | Zu viele Anfragen = Fehler 429 |
| 22.8 | `web_enabled=false` | Webseite startet nicht |
| 22.9 | `web_public_url`, `web_trust_proxy` hinter nginx | `/civ web` nennt die öffentliche Adresse |

## 23. Admin, Rechte, Daten

| # | Schritte | Erwartung |
|---|---|---|
| 23.1 | Rechte: `eddies-civ-mod:use`, `:web`, `:found`, `:admin` (LuckPerms oder Operator-Stufe) | Ohne Knoten kein Zugriff |
| 23.2 | `/civ admin tag` | Beendet den Tag: Steuern, Villager-Einkommen, Abstimmungen, Wahlen, Produktion, Verträge |
| 23.3 | `/civ admin sicherung` | Sofortige Sicherung in `backups/` |
| 23.4 | Start-Integrität: Datenbank absichtlich beschädigen | Server startet nicht, nennt den Backup-Ordner |
| 23.5 | Update-Test: Alt-Welt (≤ 1.0.8) laden | Migrationen laufen (u. a. V028–V030), vorher `civ-v<alt>-….sqlite`; je Spieltag `civ-daily-….sqlite`, von beiden Arten bleiben 7 |
| 23.6 | Wiederherstellen: Server stoppen, Backup als `civ.sqlite` zurückkopieren, starten | Zustand ist der alte |
| 23.7 | Neustart-Test | Gemeinden, Konten, Verträge, Verfahren, Parlament, Religionsdaten (`religion.tsv`), Baustellen (`construction.tsv`), Wege (`roads.tsv`) bleiben erhalten |

## 24. Spieler ohne Mod

Auf einem Server mit dem Mod soll mitspielen, wer ihn nicht hat (laut README).
1. Spieler ohne Mod verbinden. → Verbindung klappt oder sinnvolle Fehlermeldung; Menüs erscheinen als Truhenmenüs.
2. Hinweis: Die Installationsanleitung sagt, dass wegen eigener Blöcke alle Spieler den Mod brauchen. README und Wiki widersprechen sich hier. Beim Test festhalten, welche Aussage stimmt, und die Doku anpassen.

---

## Abnahme-Checkliste (Kurzform)

- [ ] `./gradlew test` und `./gradlew build` grün
- [ ] Server startet ohne Fehler, Migration und Backup laufen
- [ ] Gemeinde gründen, Gebiet, Schutz
- [ ] Geld, Markt, Läden, Bank, Währungen
- [ ] Straße → Grundstück → Verkauf/Miete → Bauauftrag
- [ ] Villager: Menü, Zufriedenheit, Abwanderung, Ehrgeiz
- [ ] Wahl, Abstimmung, Verfassung, Gesetzbuch
- [ ] Bund, Partei, Parlament, Regierung, Koalition
- [ ] Gericht komplett, Berufung, Haft
- [ ] Polizei, Handschellen, Zellen, Militär, Krieg
- [ ] Firma, Produktion, Börse, Presse
- [ ] Religion, Hoheitszeichen, Hymne, Ausweis
- [ ] Webseite, Anmeldung, Karte, Krieg, Recht
- [ ] Neustart und Datenerhalt

## Fehler melden

Pro Fehler: Kapitel und Testnummer, Schritte, erwartet vs. tatsächlich, `latest.log`, Version (1.0.9), Konfigurationseinträge, ggf. Screenshot. Bei Datenbankproblemen zusätzlich die letzte Sicherung aus `backups/` bereithalten.
