# Eddies Civ Mod

Fabric-Mod für Minecraft 26.3: Gemeinden, Gebiete, Recht, Geld und ein Servermarkt mit dynamischen Preisen.
Alle Menüs laufen auf dem Server als Truhenmenüs. Spieler ohne den Mod können deshalb auf einem Server mit dem Mod mitspielen.

## Für Spieler

### Gemeinde gründen und beitreten
1. **Gemeindehaus** herstellen: Smaragd, Steinziegel, Glocke (`ESE / SBS / ESE`).
2. Im Amboss benennen. Der Name wird der Name der Gemeinde.
3. Aufstellen. Der Chunk wird Kerngebiet und das Gemeindehaus der Sitz. Der Block ist unzerstörbar.
4. Ein **Rechtsklick** auf das Gemeindehaus öffnet das Menü:
   - Steckbrief
   - Mitglieder
   - Recht
   - Gemeindekasse
   - Karte
   - Chunk beanspruchen
   - Grundstücke
   - Abstimmungen
   - Beitreten oder Austreten
   - Auflösen (nur Bürgermeister, zweimal Shift-Klick)

Wer eine Gemeinde betritt oder verlässt, sieht ihren Namen als Titel. Auf Grundstücken erscheinen Inhaber und gegebenenfalls der Verkaufspreis über der Schnellzugriffsleiste.

**Gemeindehaus verlegen:** Der Bürgermeister setzt ein neues Gemeindehaus irgendwo im eigenen Gebiet. Das alte verschwindet und landet wieder im Inventar.

### Dorfbewohner
- Dorfbewohner, die das Gebiet einer Gemeinde betreten, werden dort Einwohner. In der Wildnis bleiben sie in ihrer bisherigen Gemeinde gemeldet. Stirbt ein Dorfbewohner, tritt er aus.
- Jeder Dorfbewohner erwirtschaftet täglich Geld für die Gemeindekasse; Standard ist ¼ Smaragd. Zufriedene leisten bis zu 50 % mehr, Unzufriedene bis zu 50 % weniger.
- **Persönlichkeit:** Jeder Dorfbewohner hat feste Ansichten auf drei Achsen: *Ordnung ↔ Freiheit*, *Sparsamkeit ↔ Großzügigkeit* und *Heimatverbundenheit*.
- **Zufriedenheit** (-100 bis 100) setzt sich zusammen aus Grundstimmung, *Ordnung* (wie streng Explosionen, Feuer und Rechte von Fremden geregelt sind; Ordnungsliebende mögen es streng, Freigeister locker), *Steuern* (stören jeden, Sparsame am meisten), *Mitsprache* (gewählter Bürgermeister, Abstimmungen, Wahlrecht für Dorfbewohner) und *Wohlstand* (Siedlungsstufe).
- **Abwanderung:** Wer unter -30 Zufriedenheit fällt, denkt ans Wegziehen; je unzufriedener und rastloser, desto eher geht er am Tagesende. Gibt es eine Gemeinde, in der er deutlich zufriedener wäre, zieht er dorthin und erscheint neben deren Gemeindehaus, sonst verschwindet er in die Wildnis. Höchstens ein Viertel der Dorfbewohner geht pro Tag. Abschaltbar mit `villager_emigration=false` in der Konfiguration.
- Im Gemeindehaus zeigt ein Klick auf die Glocke die Stimmung im Detail, `/civ stimmung` fasst sie im Chat zusammen. Schleichen + Rechtsklick mit leerer Hand auf einen Dorfbewohner zeigt seine Persönlichkeit und Zufriedenheit. Zufriedene Dorfbewohner lassen ab und zu grüne Funken aufsteigen, unzufriedene kleine Gewitterwolken.
- Die Siedlungsstufe richtet sich nach allen Einwohnern: ab 10 Marktgemeinde, ab 30 Stadt, ab 80 Metropole. Jede Stufe erlaubt 8 Chunks mehr Gebiet.
- Für die Gebietsgröße zählen Dorfbewohner nur zu einem Viertel. So lässt sich Land nicht allein durch Zucht vergrößern.

### Läden
Spieler verkaufen eigene Waren an andere:
1. Item in die Hand nehmen, auf eine Kiste schauen und `/civ laden eroeffnen 1,5` tippen. Das geht nur, wo du bauen darfst.
2. Die Ware in die Kiste legen.
3. Andere öffnen per Rechtsklick den Laden statt der Kiste und kaufen 1, 16 oder 64 Stück.

Das Geld geht direkt auf dein Konto. Im Gemeindegebiet erhält die Gemeinde ihre **Umsatzsteuer** (Gesetz, Standard 0 %). Fremde können die Ladenkiste nicht abbauen. Schließen geht mit `/civ laden schliessen`, eine Übersicht gibt `/civ laden liste`. Unter fremde Ladenkisten lassen sich keine Trichter setzen, auch nicht in der Wildnis. Am sichersten steht ein Laden trotzdem im Gemeindegebiet oder auf dem eigenen Grundstück.

Im Kreativmodus liegen alle Blöcke des Mods, der Grundstücksstab und nützliche Test-Items im eigenen Reiter **Eddies Zivilisation**.

### Grundstücke
Grundstücke teilen das Gemeindeland auf. Nur ihre Inhaber dürfen dort bauen, und nur für sie fällt Grundsteuer an.
1. Der Bürgermeister oder das Bauamt stellt sich an eine Ecke und tippt `/civ grundstueck ecke1`, an der gegenüberliegenden Ecke `ecke2`. Bequemer geht es mit dem **Grundstücksstab** (Stock + Smaragd diagonal): Linksklick auf einen Block setzt Ecke 1, Rechtsklick Ecke 2. Partikel zeigen die Auswahl.
2. `/civ grundstueck erstellen wohnen` macht daraus ein Grundstück der Gemeinde. `/civ grundstueck chunk wohnen` nimmt stattdessen den ganzen Chunk.
   Mögliche Arten: `wohnen`, `gewerbe`, `industrie`, `landwirtschaft`, `oeffentlich`, `botschaft`.
3. Danach gibt es zwei Möglichkeiten:
   - `/civ grundstueck anbieten 12,5` bietet es zum Verkauf an. Jeder kann es mit `/civ grundstueck kaufen` oder per Shift-Klick im Grundstücksmenü erwerben.
   - `/civ grundstueck vergeben <Spieler>` verschenkt es.
4. Inhaber bieten ihr eigenes Grundstück genauso an. `/civ grundstueck` zeigt das Grundstück unter dir und seinen Umriss.

### Abstimmungen
Gilt das Gesetz **Rechtsänderungen per Abstimmung**, wird jede Änderung im Recht-Menü oder per Befehl zum Antrag.
- Die Mitglieder werden benachrichtigt. Bürger stimmen im Gemeindehaus unter **Abstimmungen** ab (Linksklick dafür, Rechtsklick dagegen) oder mit `/civ abstimmen <Nr> ja|nein`.
- Hat mehr als die Hälfte der Bürger zugestimmt, gilt die Änderung sofort. Hat mindestens die Hälfte abgelehnt, ist sie gescheitert.
- Sonst wird nach **Dauer einer Abstimmung** (Standard: 2 Tage) ausgezählt. Dann entscheidet die Mehrheit der abgegebenen Stimmen; bei Gleichstand gilt der Antrag als abgelehnt.
- Dorfbewohner und Einwohner ohne Bürgerrecht stimmen nicht mit ab.
- Ohne Abstimmungen ändert nur der Bürgermeister das Recht.

### Ämter und Wahlen
Jede Gemeinde hat drei Ämter: **Bürgermeister**, **Schatzmeister** und **Bauamt**.

| Amt | Darf |
|---|---|
| Bürgermeister | Recht ändern (ohne Abstimmungen), Kasse verwalten, Grundstücke verwalten, Sitz verlegen, Gemeinde auflösen, Schatzmeister und Bauamt ernennen oder entlassen |
| Schatzmeister | Geld aus der Gemeindekasse nehmen |
| Bauamt | Grundstücke der Gemeinde anlegen, anbieten, vergeben und auflösen |

- Solange niemand gewählt wurde, ist der dienstälteste Bürger kommissarisch Bürgermeister, anfangs der Gründer.
- **Wahl ausrufen:** im Gemeindehaus unter **Ämter und Wahlen** per Umschalt-Klick auf das Amt oder mit `/civ wahl ausrufen <Amt>`. Der Bürgermeister darf das jederzeit, andere Bürger nur, wenn das Amt frei oder die Amtszeit abgelaufen ist.
- **Kandidieren und wählen:** Klick aufs Amt bzw. auf einen Kandidaten, oder `/civ wahl kandidieren <Amt> [Programm]` und `/civ wahl stimmen <Amt> <Spieler>`. Programme: `ordnung` (Sicherheit und Ordnung), `freiheit`, `steuern` (niedrige Steuern), `ausbau` (Ausbau der Gemeinde) oder `ohne`. Die Stimme lässt sich bis zum Ende ändern. Wer wen gewählt hat, bleibt geheim.
- **Auszählung** am Ende von Tag *Ausruf + Wahldauer* (Standard 2 Tage). Es gewinnt die höchste Stimmenzahl; bei Gleichstand der Amtsinhaber, sonst wer zuerst kandidiert hat. Hat niemand kandidiert, bleibt alles, wie es ist.
- **Amtszeit:** Mit `term_days` größer 0 laufen Ämter ab und werden automatisch neu gewählt, das Bürgermeisteramt auch, wenn es noch nie gewählt wurde. Bei 0 (Standard) sind Ämter unbefristet.
- **Dorfbewohner wählen mit**, wenn `villager_vote_percent` größer 0 ist; ihre Stimme zählt mit diesem Gewicht (100 = wie ein Bürger). Kandidieren können sie nicht. Sie wählen nach ihrer Persönlichkeit das passende Programm; wer unter Steuern leidet, neigt zu „Niedrige Steuern“. Zufriedene halten zum Amtsinhaber, Unzufriedene wählen den Wechsel. Wem kein Kandidat zusagt, bleibt zu Hause.
- Wer austritt, verliert seine Ämter, seine Kandidatur und seine Stimme.

### Schutz
Im Gemeindegebiet gilt Folgendes, solange das Recht nichts anderes erlaubt:
- Explosionen und Feuer zerstören keine Blöcke.
- Kolben bewegen oder zerstören keine Blöcke über Gebietsgrenzen hinweg.
- Endermen nehmen keine Blöcke auf.
- Fremde dürfen nicht bauen, Türen benutzen oder Behälter öffnen.
- Fremde dürfen Rüstungsständer, Rahmen und Bilder nicht verändern und Tiere sowie Dorfbewohner nicht angreifen. Handeln mit Dorfbewohnern bleibt erlaubt.

### Bank (Block)
Rezept: Gold, Eisen, Smaragd, Steinziegel (`GIG / IEI / SSS`).

Per Rechtsklick zahlst du Smaragde ein oder hebst sie ab. Das Konto rechnet in Hundertsteln: 1 Smaragd sind 100 Einheiten, deshalb sind auch Beträge wie `0,25` möglich.

### Markt (Block)
Rezept: weiße und rote Wolle, Bretter, Smaragd, Truhe (`WRW / PEP / PCP`).

- **Verkaufen:** Klick auf einen Stapel im eigenen Inventar verkauft den ganzen Stapel. Ein Rechtsklick verkauft nur ein Stück.
- **Kaufen:** Oben liegt das Lager. Ein Klick kauft 1, ein Shift-Klick 16.

So entstehen die Preise:
- Jedes Item hat einen Grundpreis.
- Je mehr davon auf Lager ist, desto billiger wird es.
- Das Lager schrumpft jeden Tag um 5 %.
- Ankauf und Verkauf liegen 10 % auseinander.
- Das Preisniveau folgt der Geldmenge je Spieler (Inflation) und ist auf 50 % bis 400 % begrenzt.
- Verzauberte, umbenannte oder beschädigte Items kauft der Markt nicht an.

### Webseite
Der Server stellt eine Webseite bereit, standardmäßig auf Port 8765:
- **Übersicht** mit allen Gemeinden, Einwohnern, Kassen und Grundstücken
- **Karte** aller beanspruchten Chunks je Dimension, zum Verschieben und Zoomen
- **Markt** mit aktuellen Preisen und Lagerbeständen sowie allen Läden der Spieler
- **Mein Bereich:** Mit `/civ web` holst du dir im Spiel einen Code. Mit ihm meldest du dich an und siehst deinen Kontostand, überweist Geld, stimmst ab, kandidierst und wählst.

### Befehle
| Befehl | Wirkung |
|---|---|
| `/civ hilfe` | Überblick |
| `/civ info` | Chunk, Gemeinde, Kontostand, Tag |
| `/civ menue` | Menü der eigenen Gemeinde |
| `/civ karte` | Chunk-Karte; Linksklick beansprucht Kerngebiet, Rechtsklick einen Außenposten |
| `/civ grenzen` | Gebietsgrenzen 10 Sekunden lang als Partikel |
| `/civ beitreten <Name>` | einer Gemeinde beitreten |
| `/civ austreten` | aus der eigenen Gemeinde austreten |
| `/civ liste` | alle Gemeinden |
| `/civ gemeinde [Name]` | Steckbrief einer Gemeinde |
| `/civ beanspruchen [aussenposten]` | den aktuellen Chunk beanspruchen |
| `/civ freigeben` | den aktuellen Chunk aufgeben |
| `/civ recht` | Regelungen der eigenen Gemeinde anzeigen |
| `/civ recht setzen <Regel> <Wert>` | eine Regelung ändern |
| `/civ konto` | Kontostand |
| `/civ einzahlen [Anzahl]` | Smaragde einzahlen |
| `/civ abheben <Anzahl>` | Smaragde abheben |
| `/civ ueberweisen <Spieler\|Gemeinde> <Betrag>` | Geld überweisen, z. B. `2,5` |
| `/civ kasse einzahlen <Betrag>` | in die Gemeindekasse einzahlen |
| `/civ kasse entnehmen <Betrag>` | aus der Gemeindekasse entnehmen (nur Bürgermeister) |
| `/civ preis` | Preise des Items in der Hand |
| `/civ verkaufen [Anzahl]` | Item in der Hand verkaufen |
| `/civ kaufen <Anzahl> <Item>` | vom Markt kaufen |
| `/civ grundstueck [info]` | Grundstück unter dir, Umriss als Partikel |
| `/civ grundstueck ecke1` / `ecke2` | Ecken einer Auswahl setzen |
| `/civ grundstueck erstellen <Art>` | Auswahl zum Grundstück machen (Bürgermeister, Bauamt) |
| `/civ grundstueck chunk <Art>` | ganzen Chunk zum Grundstück machen (Bürgermeister, Bauamt) |
| `/civ grundstueck anbieten <Preis>` | Grundstück zum Verkauf anbieten |
| `/civ grundstueck zurueckziehen` | Verkaufsangebot zurückziehen |
| `/civ grundstueck kaufen` | angebotenes Grundstück kaufen |
| `/civ grundstueck vergeben <Spieler>` | Gemeindeland verschenken (Bürgermeister, Bauamt) |
| `/civ grundstueck aufloesen` | Grundstück der Gemeinde auflösen (Bürgermeister, Bauamt) |
| `/civ laden eroeffnen <Preis>` | Kiste im Blick wird Laden für das Item in der Hand |
| `/civ laden schliessen` | Laden an der Kiste im Blick schließen |
| `/civ laden liste` | eigene Läden |
| `/civ web` | Anmeldecode für die Webseite |
| `/civ abstimmungen` | offene Abstimmungen |
| `/civ abstimmen <Nr> ja\|nein` | abstimmen |
| `/civ aemter` | Ämter, Inhaber und laufende Wahlen |
| `/civ stimmung` | Stimmung der Dorfbewohner |
| `/civ wahl ausrufen <Amt>` | Wahl ausrufen (`buergermeister`, `schatzmeister`, `bauamt`) |
| `/civ wahl kandidieren <Amt> [Programm]` | kandidieren, optional mit Programm |
| `/civ wahl stimmen <Amt> <Spieler>` | wählen |
| `/civ amt ernennen <Amt> <Spieler>` | Schatzmeister oder Bauamt ernennen (Bürgermeister) |
| `/civ amt entlassen <Amt>` | Amt frei machen (Bürgermeister) |

Nur für Admins:
- `/civ gruenden <Name>` gründet eine Gemeinde ohne Gemeindehaus.
- `/civ admin umgehen` schaltet den Gebietsschutz für dich selbst ab und wieder an.
- `/civ admin aufloesen <Name>` löst eine Gemeinde auf.
- `/civ admin tag` beendet den laufenden Tag sofort: Grundsteuer, Einnahmen der Dorfbewohner, fällige Abstimmungen und Wahlen werden abgerechnet. Praktisch zum Testen.

### Recht
Der Bürgermeister (oder mit Abstimmungen die Bürger) stellt diese Regelungen im Menü **Recht** des Gemeindehauses ein:

| Regelung | Bedeutung |
|---|---|
| Grundsteuer pro Block und Tag | in Hundertstel Smaragd |
| Steuerfreie Blöcke je Inhaber | Freibetrag auf die Gesamtfläche eines Inhabers |
| Steuerfreie Tage nach Erwerb | Schonfrist nach dem Kauf eines Grundstücks |
| Botschaften zahlen Grundsteuer | ja/nein |
| Höchstzahl an Außenposten | |
| Fremde dürfen bauen | ja/nein |
| Fremde dürfen benutzen | ja/nein |
| Fremde dürfen Behälter öffnen | ja/nein |
| Explosionen zerstören Blöcke | ja/nein |
| Feuer verbrennt Blöcke | ja/nein |
| Rechtsänderungen per Abstimmung | ja/nein |
| Dauer einer Abstimmung in Tagen | Standard 2 |
| Umsatzsteuer auf Ladenverkäufe in Prozent | 0 bis 50 |
| Amtszeit gewählter Ämter in Tagen (`term_days`) | 0 = unbefristet (Standard) |
| Dauer einer Wahl in Tagen (`election_days`) | Standard 2 |
| Stimmgewicht von Dorfbewohnern in Prozent (`villager_vote_percent`) | 0 = kein Wahlrecht (Standard) |

## Für Server-Admins
`config/eddies-civ-mod.properties` wird beim ersten Start angelegt:

| Eintrag | Bedeutung |
|---|---|
| `ticks_per_day` | Länge eines Zivilisationstags; 24000 = ein Minecraft-Tag |
| `claim_base` | Chunks, die jede Gemeinde beanspruchen darf |
| `claim_per_member` | zusätzliche Chunks je Mitglied |
| `price_reference_emeralds_per_player` | Geldmenge je Spieler, bei der der Markt Grundpreise zahlt |
| `villager_income_hundredths_per_day` | Einnahmen je Dorfbewohner und Tag; 100 = 1 Smaragd |
| `villager_emigration` | ob sehr unzufriedene Dorfbewohner wegziehen (Standard `true`) |
| `market_town_inhabitants`, `city_inhabitants`, `metropolis_inhabitants` | Einwohner für die Siedlungsstufen |
| `web_enabled`, `web_bind`, `web_port` | Webseite ein/aus, Adresse und Port (Standard `true`, `0.0.0.0`, `8765`) |
| `web_public_url` | Adresse, die `/civ web` den Spielern nennt, z. B. `https://civ.example.org` |

Der Zustand liegt je Welt in `<Welt>/data/eddies-civ-mod/civ.sqlite` und wird im Hintergrund zusammen mit der Welt gespeichert.

## Aufbau
| Modul | Inhalt |
|---|---|
| `civ-domain` | Fachlogik ohne Abhängigkeiten |
| `civ-application` | Use Cases und Persistenzschnittstellen |
| `civ-persistence` | SQLite |
| `civ-server` | Server-Ablauf, Befehle und Menülogik, ohne Minecraft |
| `civ-fabric` | dünner Adapter zu Minecraft und Fabric |
| `civ-sim` | deterministische Simulation |
