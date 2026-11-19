# Karte

Es gibt zwei Karten: die **Web-Karte** (eingebettete Webseite, Reiter „Karte") und die **Ingame-Karte**
(`/civ karte`, oder im Gemeindehaus-Menü der Punkt „Karte").

## Farben

Jede Gemeinde gehört zu höchstens einem Bund je Ebene (z. B. Land, Nation). Auf beiden Karten färbt sich das Gebiet
einer Gemeinde standardmäßig nach dem **höchsten Bund**, dem sie angehört (Nation > Land > Gemeinde): Alle
Gemeinden derselben Nation erscheinen zusammenhängend in einer Farbe. Gehört eine Gemeinde keinem Bund an, zeigt
ihr Gebiet ihre eigene Farbe.

## Ebenen-Filter

Über den Filter **„Ebene"** lässt sich die Einfärbung auf eine bestimmte Ebene eingrenzen, statt immer die höchste
zu zeigen:

- **Web-Karte:** Auswahlfeld „Ebene" über der Karte. „Höchste Ebene" ist die Voreinstellung; wählt man z. B.
  „Land", erscheinen alle Gemeinden desselben Landes zusammenhängend in einer Farbe, auch wenn sie zusätzlich zu
  verschiedenen Nationen gehören.
- **Ingame-Karte:** Der Kompass-Knopf unten rechts (Kartenbeschriftung) wechselt bei jedem Klick zur nächsten
  Ebene, von der höchsten abwärts bis zur Gemeinde, und beginnt danach wieder von vorn.

Außenposten bleiben schraffiert dargestellt, eigenes und fremdes Gebiet bleiben zusätzlich an Namen bzw.
Textfarbe (grün/rot) erkennbar; nur die Flächenfarbe folgt dem Bund.

## Für Server-Betreiber

Die Ebenen selbst (Kennung, Rang, Anzeigename) kommen aus `federation_levels`, siehe README. Eigene Ebenen
erscheinen automatisch im Ebenen-Filter beider Karten.
