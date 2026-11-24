[Home](Home.md) › Bauen und Straßen

# Bauen, Baustellen und Straßen

Hier steht alles zu Bauaufträgen, Baustellen, Straßen und dem Wegebau, für Spieler und für Dorfbewohner.

## Erst die Straße, dann das Haus
Grundstücke und Häuser gibt es nur an Straßen (Gesetz `street_required`, Standard ja):
1. Bürgermeister oder Bauamt legen die Straße an (`/civ strasse anlegen <Name>`) und bauen den Weg (Wegebau unten) oder bauen ihn von Hand und tragen ihn danach ein.
2. Erst wenn die Straße eingetragen und, falls ein Wegebau läuft, fertig ist, lässt sich in höchstens 12 Blöcken Abstand ein Grundstück zuschneiden und ausschreiben.
3. Ein Bauauftrag braucht ein Grundstück an einer solchen Straße. Sonst meldet das Spiel, dass erst eine Straße gebaut werden muss.

Dorfbewohner als Bauamt gehen genauso vor: erst die Straße samt Weg, dann die Grundstücke. Das Gesetz lässt sich pro Gemeinde abschalten.

## Bauaufträge
Der Inhaber eines Grundstücks beauftragt eine **Baufirma** (Branche `bau`). Die Bauarbeiter, angestellte Dorfbewohner, setzen den Plan sichtbar Stück für Stück.

| Befehl | Wirkung |
|---|---|
| `/civ bau plaene` | alle Bauvorlagen (57 eingebaute in den Stilen `holz`, `stein`, `ziegel`, dazu eigene Pläne) |
| `/civ bau beauftragen <Plan> "<Firma>" [material firma\|selbst] [Drehung]` | Bau auf dem Grundstück unter dir beauftragen |
| `/civ bau vorschau <Plan> [Drehung]` / `vorschau aus` | Hologramm des Bauwerks auf dem Grundstück unter dir (45 Sekunden), ohne zu beauftragen; ein neuer Auftrag zeigt es ebenfalls |
| `/civ bau liste` / `material <Nr>` / `abbrechen <Nr>` | Aufträge, Material liefern, abbrechen |
| `/civ bau genehmigen <Nr>` / `ablehnen <Nr>` | Baugenehmigung durch das Bauamt (Gesetz `build_permit_required`) |
| `/civ bau selbstbau <Plan> "<Firma>" <Spieler>` | ein Spieler baut selbst im Auftrag der Firma |

### Ausrichtung zur Straße
Gibst du **keine Drehung** an, dreht der Mod das Gebäude von selbst: Die Haustür zeigt zur nächsten Straße der Gemeinde. Gibt es noch keine Straße, schaut das Haus zum Gemeindehaus. Passt der Plan so nicht aufs Grundstück, probiert der Mod die andere Achse. Mit einer Gradzahl (`0`, `90`, `180`, `270`) bestimmst du die Drehung selbst. Dorfbewohner, Firmen und Gemeinden lassen immer automatisch ausrichten.

### Türen auf Straßenhöhe
Die unterste Ebene des Plans liegt so, dass die **Haustür auf Straßenhöhe** ist: Maßstab ist der Boden unmittelbar vor der Tür. Steht das Haus am Hang, trägt die Bauleitung vor dem ersten Stein **Erde, Stein und Bewuchs im Baufeld ab**. Unter dem Boden wird bis zu 24 Blöcke tief ein Fundament gegossen, wo der Untergrund fehlt. Gebautes wird nie überschrieben: Steht schon etwas auf dem Baufeld, ruht der Auftrag, und nach drei Tagen endet er.

### Bauabsperrung
Jede laufende Baustelle der Baufirma wird mit **Bauabsperrungen** eingezäunt (ein Block Abstand zum Bau, höchstens bis zur Grundstücksgrenze). Die Absperrung zählt zu den Zäunen, deshalb laufen Dorfbewohner nicht hinein; die Bauarbeiter gehören dazu und kommen mit Hilfe der Bauleitung auf den Platz. Nach Fertigstellung oder Abbruch baut der Mod die Absperrung von selbst wieder ab. Das Item gibt es im Kreativ-Reiter und per Rezept (zwei Reihen aus Stock, orangem Farbstoff und Stock → 6 Stück); so lassen sich auch eigene Baustellen absperren. Baut ein Spieler selbst im Auftrag, wird nicht abgesperrt.

## Straßen
- Bürgermeister oder Bauamt markieren eine Fläche mit dem Grundstücksstab und legen sie mit `/civ strasse anlegen <Name>` an; `aufheben <Name>` und `liste` gibt es auch.
- Jede Gemeinde hat eine feste Postleitzahl. Grundstücke bis 12 Blöcke neben einer Straße bekommen Hausnummern: ungerade auf der einen, gerade auf der anderen Seite.

### Wegebau
Eine Straße ist zunächst nur eine Fläche im Register. Mit dem **Wegebau** entsteht der Weg in der Welt:

| Befehl | Wirkung |
|---|---|
| `/civ strasse bauen schaufel <Name>` | der Weg wird mit der Schaufel zum Trampelpfad geplättet: kostenlos, 48 Felder am Tag |
| `/civ strasse bauen pflaster <Name>` | der Weg wird gepflastert (Bruchstein und Steinziegel im Muster): 20 Hundertstel je Feld aus der Gemeindekasse, 160 Felder am Tag; gezahlt wird nach Fortschritt an die Baufirma der Gemeinde |
| `/civ strasse wege` | Stand aller Wegebauten der Gemeinde |

Der Weg folgt dem Gelände, räumt Gras und Blumen weg und lässt Häuser, Wasser und Lava unberührt. Die Befehle stehen nur Bürgermeister und Bauamt offen.

### Straßen durch Dorfbewohner
Ist das Bauamt (Planer, sonst Bürgermeister, auch kommissarisch) ein Dorfbewohner, legt er selbst Straßen an, sobald es genug Grundstücke gibt (eine Straße je vier Grundstücke, höchstens acht): Er sucht einen zwei Blöcke breiten Streifen auf der Grenze zwischen zwei Chunks der Gemeinde, an dem weder Grundstücke noch andere Straßen liegen, tauft ihn mit einem echten Namen (zum Beispiel „Mühlenweg“, „Lindenallee“, „Am Brunnen“, nie mit einer Nummer) und beauftragt den Weg: gepflastert, wenn die Kasse das Doppelte der Kosten trägt, sonst mit der Schaufel. Die Gemeinde erfährt davon als Meldung. Abschaltbar mit `villager_ambitions=false`.

## Namen von Dorfbewohner-Gründungen
Gemeinden, Straßen, Firmen, Zeitungen, Parteien, Religionen und ihre Schriften, die Dorfbewohner gründen, bekommen **echte deutsche Namen statt Kennungen**: Gemeinden heißen etwa „Lindenau“ oder „Eichenhain“, Firmen „Lindenau Handelshaus“, Zeitungen „Dorfbote Lindenau“, Parteien „Freie Ordnungspartei Lindenau“, Religionen „Hain der Quelle“ mit der Schrift „Das Buch des Lichts“. Ist ein Name vergeben, wählt der Mod den nächsten Vorschlag, notfalls mit Vorsilbe („Neu“, „Alt“, „Ober“ …), nie mit einer Zahl.
