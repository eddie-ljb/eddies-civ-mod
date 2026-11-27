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

**Claim-Kosten:** Jeder zusätzlich beanspruchte Chunk kostet die Gemeindekasse den per Recht `claim_cost_per_chunk` festgelegten Betrag (Standard: 0, also deaktiviert). Reicht die Kasse nicht, wird der Claim abgelehnt. Der Bürgermeister sieht den fehlenden Betrag in der Fehlermeldung.

## Dorfbewohner als Einwohner
- Dorfbewohner, die das Gebiet einer Gemeinde betreten, werden dort Einwohner. In der Wildnis bleiben sie in ihrer bisherigen Gemeinde gemeldet. Stirbt ein Dorfbewohner, tritt er aus.
- Jeder Dorfbewohner erwirtschaftet täglich Geld für die Gemeindekasse; Standard ist ¼ Smaragd. Zufriedene leisten bis zu 50 % mehr, Unzufriedene bis zu 50 % weniger.
- **Persönlichkeit:** feste Ansichten auf drei Achsen – *Ordnung ↔ Freiheit*, *Sparsamkeit ↔ Großzügigkeit*, *Heimatverbundenheit*.
- **Zufriedenheit** (-100 bis 100) setzt sich zusammen aus Grundstimmung, *Ordnung* (wie streng Explosionen, Feuer und Rechte von Fremden geregelt sind), *Steuern*, *Mitsprache* (gewählter Bürgermeister, Abstimmungen, Wahlrecht) und *Wohlstand* (Siedlungsstufe).
- **Abwanderung:** Wer unter -30 Zufriedenheit fällt, denkt ans Wegziehen; je unzufriedener und rastloser, desto eher geht er am Tagesende. Gibt es eine Gemeinde, in der er deutlich zufriedener wäre, zieht er dorthin, sonst verschwindet er in die Wildnis. Höchstens ein Viertel der Dorfbewohner geht pro Tag. Abschaltbar mit `villager_emigration=false`.
- Klick auf die Glocke im Gemeindehaus zeigt die Stimmung im Detail, `/civ stimmung` fasst sie im Chat zusammen. Schleichen + Rechtsklick mit leerer Hand auf einen Dorfbewohner öffnet sein Menü mit Persönlichkeit, Zufriedenheit und Loyalität (und Knöpfen zum Reden, Beeinflussen und für Post). Zufriedene lassen grüne Funken aufsteigen, Unzufriedene kleine Gewitterwolken.
- **Loyalität** (-100 bis 100): anders als die tagesaktuelle Zufriedenheit sammelt sich Loyalität über die Zeit an – neue Einwohner haben nur ihre Heimatverbundenheit als Loyalität, mit wachsender Mitgliedsdauer (volles Gewicht nach 60 Tagen) schlägt die seitherige Zufriedenheit zunehmend durch. Dauerhaft zufriedene Dorfbewohner werden so loyaler, dauerhaft unzufriedene illoyaler. Loyalität gibt es auch zur eigenen Partei, zu deren Vorsitz (bzw. zum Bürgermeister/Vorsitz eines Bundes) und zum Arbeitgeber. Loyale Dorfbewohner folgen ihrer Partei beim Wählen stärker, illoyale schwächer; loyale wandern seltener ab (höchstens halb so wahrscheinlich wie unloyale, unabhängig von der reinen Tageszufriedenheit).

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

### Rechte auf dem eigenen Grundstück
Der Inhaber stellt ein, was auf seinem Grundstück erlaubt ist (Rechtsklick auf das Grundstück im Grundstücksmenü oder per Befehl):
- `/civ grundstueck rechte` zeigt die Einstellungen.
- `/civ grundstueck rechte mitglieder|fremde <bauen|benutzen|behaelter> <ja|nein>` erlaubt oder verbietet Mitgliedern der Gemeinde bzw. allen anderen das Bauen, das Benutzen von Türen, Knöpfen und Hebeln oder das Öffnen von Behältern. Standard: Mitglieder benutzen Türen, Fremde nichts.
- `/civ grundstueck rechte gast <Spieler> <Handlung> <ja|nein>` und `gastentfernen <Spieler>` geben einzelnen Spielern Rechte (bis zu 32 Gäste, auch Dorfbewohner der Gemeinde mit vollem Namen; im Menü per Klick umschalten).
- `/civ grundstueck rechte regel <pvp|monster|explosionen|feuer|tiere> <gesetz|erlauben|verbieten>` stellt Regeln abweichend vom Gesetz ein: Kämpfe, feindliche Monster, Explosionen, Feuerausbreitung und ob Fremde Tiere verletzen dürfen.
- `/civ grundstueck rechte zuruecksetzen` stellt den Standard wieder her.

Ob der Inhaber Gemeinde-, Länder- und Nationenrecht überschreiben darf, regelt das Gesetz `plot_rights_override` (Standard ja). Steht es auf nein, gilt das Recht auch auf Grundstücken: Was es Fremden erlaubt, ist überall erlaubt, und die Regeln des Grundstücks werden ignoriert. Wechselt das Grundstück den Inhaber, gilt wieder der Standard. Gemeindeland ist davon nicht betroffen; dafür entscheidet das Recht der Gemeinde.

### Dorfbewohner als Bauamt und Bauherren
Ist das Bauamt (Planer, sonst Bürgermeister) ein Dorfbewohner, schneidet er neue Grundstücke zu und schreibt sie zum Verkauf aus, plant Schule, Gotteshaus und öffentliche Plätze und lässt darauf kommunale Bauten von einer Baufirma errichten, bezahlt aus der Gemeindekasse. Dorfbewohner kaufen angebotenes Wohnland und bauen darauf; Firmenchefs und Zeitungsleiter lassen auf den Grundstücken ihrer Organisation bauen. Bleibt das Baufeld drei Tage lang blockiert, endet der Auftrag. Auf Grundstücken einer Gemeinde, Firma, Zeitung, Partei oder eines Bundes baut deren Leitung (Spieler oder Dorfbewohner) auf Kosten der Kasse. Über 50 eingebaute Bauvorlagen (`/civ bau plaene`) decken Wohnen, Gewerbe, Industrie, Landwirtschaft und kommunale Bauten ab.

Mehr zu Bauaufträgen, Absperrung, Ausrichtung und Wegebau: [Bauen und Straßen](Spieler-Bauen-und-Strassen.md).

## Straßen und Adressen
- Bürgermeister oder Bauamt markieren eine Straße mit dem Grundstücksstab und legen sie mit `/civ strasse anlegen <Name>` an; `aufheben <Name>` und `liste` gibt es auch.
- Jede Gemeinde hat eine feste Postleitzahl. Grundstücke bis 12 Blöcke neben einer Straße bekommen Hausnummern: ungerade auf der einen, gerade auf der anderen Seite, aufsteigend entlang der Straße.
- `/civ adresse [Spieler]` zeigt die Anschrift, `/civ adressen` listet die Straßen der Gemeinde. Dorfbewohner nennen ihre Anschrift beim Anklicken.

### Schilder und Hausnummern zum Aufstellen
Die fünf eigenen Blöcke zeigen ihren Text direkt vorne auf der Schildfläche. Straßenschilder und Gebietsschilder stehen auf einem Pfosten; Hausnummern sind flache Plaketten, die sich beim Platzieren an einer Wand ausrichten.

| Block/Item | Zeilen | Zweck |
|---|---|---|
| Straßenschild | 1 | z. B. „Hauptstraße“ |
| Hausnummer | 1 | z. B. „12“ |
| Ortsschild | mehrzeilig | Gemeinde-Begrüßungsschild |
| Länderschild | mehrzeilig | Landes-Begrüßungsschild |
| Nationenschild | mehrzeilig | Nations-Begrüßungsschild |

- Ohne Amboss-Namen übernimmt das Schild beim Platzieren die passenden Daten: Straßennamen auf Straßen oder adressierten Grundstücken, Hausnummern auf adressierten Grundstücken, auf Ortsschildern die Siedlungsstufe und vorhandene Angaben zu Land und Nation. Länder- und Nationenschilder zeigen die entsprechende Ebene. Ohne passende Daten bleibt das Schild leer.
- Für eigenen Text das Item vor dem Platzieren im Amboss benennen (z. B. `12` oder `Stadt: Nordhain|Land: Ost|Nation: Reich`). `|` trennt bis zu drei Zeilen auf Gebietsschildern. Der Amboss-Name hat Vorrang.
- Die Beschriftung ist eine Momentaufnahme beim Platzieren. Für geänderte Gebietsdaten ein unbenanntes Schild neu platzieren. Bereits gespeicherte Beschriftungen bleiben erhalten und werden mit der Standardausrichtung Süden gerendert.
- Rechtsklick zeigt den Text zusätzlich in der Actionbar. Farbstoff färbt die Schrift.
- Craftbar: Straßenschild aus Stöcken und Eichenbrettern; Hausnummer aus Eisennuggets; Ortsschild aus Eichenbrettern und einem Stock; Länder- und Nationenschild aus Dunkeleichenbrettern und einem Stock (Nationenschild zusätzlich mit einem Smaragd in der Mitte).

## Schutz
Im Gemeindegebiet gilt, solange das Recht nichts anderes erlaubt:
- Explosionen und Feuer zerstören keine Blöcke; Kolben bewegen/zerstören keine Blöcke über Gebietsgrenzen hinweg; Endermen nehmen keine Blöcke auf.
- Fremde dürfen nicht bauen, Türen benutzen oder Behälter öffnen.
- Fremde dürfen Rüstungsständer, Rahmen und Bilder nicht verändern und Tiere sowie Dorfbewohner nicht angreifen (Handel bleibt erlaubt).

Diese Regeln lassen sich im Menü **Recht** des Gemeindehauses einstellen oder als Freitext-Paragraph im Gesetzbuch erlassen – siehe [Recht, Verfassung und Bünde](Spieler-Recht-und-Verfassung.md).
