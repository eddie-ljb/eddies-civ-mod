[Home](Home.md) › Gemeinden und Grundstücke

# Gemeinden und Grundstücke

## Gemeinde gründen und beitreten
1. **Gemeindehaus** herstellen: Smaragd, Steinziegel, Glocke (`ESE / SBS / ESE`).
2. Im Amboss benennen. Der Name wird der Name der Gemeinde.
3. Aufstellen. Der Chunk wird Kerngebiet und das Gemeindehaus der Sitz. Der Block ist unzerstörbar.
4. Ein **Rechtsklick** auf das Gemeindehaus öffnet das Menü: Steckbrief, Mitglieder, Recht, Gemeindekasse, Karte, Chunk beanspruchen, Grundstücke, Abstimmungen, Beitreten oder Austreten, Auflösen (nur Bürgermeister, zweimal Shift-Klick).

Wer eine Gemeinde betritt oder verlässt, sieht ihren Namen als Titel. Auf Grundstücken erscheinen Inhaber und gegebenenfalls der Verkaufspreis über der Schnellzugriffsleiste.

**Gemeindehaus verlegen:** Der Bürgermeister setzt ein neues Gemeindehaus irgendwo im eigenen Gebiet. Das alte verschwindet und landet wieder im Inventar.

Die Siedlungsstufe richtet sich nach allen Einwohnern: ab 10 Marktgemeinde, ab 30 Stadt, ab 80 Metropole. Jede Stufe erlaubt 8 Chunks mehr Gebiet (`claim_base` + `claim_per_member`, siehe [Konfiguration](Admin-Installation-und-Konfiguration.md)). Für die Gebietsgröße zählen Dorfbewohner nur zu einem Viertel, Land lässt sich also nicht allein durch Zucht vergrößern.

## Dorfbewohner als Einwohner
- Dorfbewohner, die das Gebiet einer Gemeinde betreten, werden dort Einwohner. In der Wildnis bleiben sie in ihrer bisherigen Gemeinde gemeldet. Stirbt ein Dorfbewohner, tritt er aus.
- Jeder Dorfbewohner erwirtschaftet täglich Geld für die Gemeindekasse; Standard ist ¼ Smaragd. Zufriedene leisten bis zu 50 % mehr, Unzufriedene bis zu 50 % weniger.
- **Persönlichkeit:** feste Ansichten auf drei Achsen – *Ordnung ↔ Freiheit*, *Sparsamkeit ↔ Großzügigkeit*, *Heimatverbundenheit*.
- **Zufriedenheit** (-100 bis 100) setzt sich zusammen aus Grundstimmung, *Ordnung* (wie streng Explosionen, Feuer und Rechte von Fremden geregelt sind), *Steuern*, *Mitsprache* (gewählter Bürgermeister, Abstimmungen, Wahlrecht) und *Wohlstand* (Siedlungsstufe).
- **Abwanderung:** Wer unter -30 Zufriedenheit fällt, denkt ans Wegziehen; je unzufriedener und rastloser, desto eher geht er am Tagesende. Gibt es eine Gemeinde, in der er deutlich zufriedener wäre, zieht er dorthin, sonst verschwindet er in die Wildnis. Höchstens ein Viertel der Dorfbewohner geht pro Tag. Abschaltbar mit `villager_emigration=false`.
- Klick auf die Glocke im Gemeindehaus zeigt die Stimmung im Detail, `/civ stimmung` fasst sie im Chat zusammen. Schleichen + Rechtsklick mit leerer Hand auf einen Dorfbewohner zeigt seine Persönlichkeit und Zufriedenheit. Zufriedene lassen grüne Funken aufsteigen, Unzufriedene kleine Gewitterwolken.

## Läden
Spieler verkaufen eigene Waren an andere:
1. Item in die Hand nehmen, auf eine Kiste schauen, `/civ laden eroeffnen 1,5` tippen (nur wo man bauen darf).
2. Ware in die Kiste legen.
3. Andere öffnen per Rechtsklick den Laden statt der Kiste und kaufen 1, 16 oder 64 Stück.

Das Geld geht direkt auf das Konto des Verkäufers. Im Gemeindegebiet erhält die Gemeinde ihre **Umsatzsteuer** (Gesetz, Standard 0 %). Fremde können die Ladenkiste nicht abbauen, und unter fremde Ladenkisten lassen sich keine Trichter setzen. Schließen: `/civ laden schliessen`, Übersicht: `/civ laden liste`.

Im Kreativmodus liegen alle Blöcke des Mods, der Grundstücksstab und Test-Items im eigenen Reiter **Eddies Zivilisation**.

## Grundstücke
Grundstücke teilen das Gemeindeland auf. Nur ihre Inhaber dürfen dort bauen, und nur für sie fällt Grundsteuer an.
1. Bürgermeister oder Bauamt markieren eine Auswahl: `/civ grundstueck ecke1` / `ecke2`, oder bequemer mit dem **Grundstücksstab** (Stock + Smaragd diagonal: Linksklick Ecke 1, Rechtsklick Ecke 2, Partikel zeigen die Auswahl).
2. `/civ grundstueck erstellen <Art>` macht daraus ein Grundstück, `/civ grundstueck chunk <Art>` nimmt den ganzen Chunk. Arten: `wohnen`, `gewerbe`, `industrie`, `landwirtschaft`, `oeffentlich`, `botschaft`.
3. Danach: `/civ grundstueck anbieten 12,5` (Kauf per `/civ grundstueck kaufen` oder Shift-Klick im Menü) oder `/civ grundstueck vergeben <Spieler>` (verschenken).
4. Inhaber bieten ihr eigenes Grundstück genauso an. `/civ grundstueck` zeigt das Grundstück unter einem und seinen Umriss.

## Straßen und Adressen
- Bürgermeister oder Bauamt markieren eine Straße mit dem Grundstücksstab und legen sie mit `/civ strasse anlegen <Name>` an; `aufheben <Name>` und `liste` gibt es auch.
- Jede Gemeinde hat eine feste Postleitzahl. Grundstücke bis 12 Blöcke neben einer Straße bekommen Hausnummern: ungerade auf der einen, gerade auf der anderen Seite, aufsteigend entlang der Straße.
- `/civ adresse [Spieler]` zeigt die Anschrift, `/civ adressen` listet die Straßen der Gemeinde. Dorfbewohner nennen ihre Anschrift beim Anklicken.

### Schilder und Hausnummern zum Aufstellen
Zusätzlich zur automatischen Adressvergabe oben gibt es eigene, von Hand beschriftbare Blöcke, die der Deko und Beschilderung dienen:

| Block/Item | Zeilen | Zweck |
|---|---|---|
| Straßenschild | 1 | z. B. „Hauptstraße“ |
| Hausnummer | 1 | z. B. „12“ |
| Ortsschild | mehrzeilig | Gemeinde-Begrüßungsschild |
| Länderschild | mehrzeilig | Landes-Begrüßungsschild |
| Nationenschild | mehrzeilig | Nations-Begrüßungsschild |

- Hinstellen, dann im Amboss den gewünschten Text als Namen vergeben (bei den mehrzeiligen Schildern trennt ein `|` die Zeilen, bis zu vier Zeilen à 32 Zeichen) und erneut platzieren – der Amboss-Name wird beim Setzen übernommen.
- Rechtsklick zeigt den aktuellen Text in der Actionbar.
- Rechtsklick mit einem Farbstoff färbt das Schild ein.
- Craftbar: Straßenschild aus Stöcken und Eichenbrettern; Hausnummer aus Eisennuggets; Ortsschild aus Eichenbrettern und einem Stock; Länder- und Nationenschild aus Dunkeleichenbrettern und einem Stock (Nationenschild zusätzlich mit einem Smaragd in der Mitte).

## Schutz
Im Gemeindegebiet gilt, solange das Recht nichts anderes erlaubt:
- Explosionen und Feuer zerstören keine Blöcke; Kolben bewegen/zerstören keine Blöcke über Gebietsgrenzen hinweg; Endermen nehmen keine Blöcke auf.
- Fremde dürfen nicht bauen, Türen benutzen oder Behälter öffnen.
- Fremde dürfen Rüstungsständer, Rahmen und Bilder nicht verändern und Tiere sowie Dorfbewohner nicht angreifen (Handel bleibt erlaubt).

Diese Regeln lassen sich im Menü **Recht** des Gemeindehauses einstellen oder als Freitext-Paragraph im Gesetzbuch erlassen – siehe [Recht, Verfassung und Bünde](Spieler-Recht-und-Verfassung.md).
