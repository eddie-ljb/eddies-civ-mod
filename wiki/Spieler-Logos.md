[Home](Home.md) › Logos

# Eigene Logos im Spiel

Wer eine Gemeinde, ein Land, eine Nation, einen Bund, eine Partei oder eine Firma **führt** (Bürgermeister, Vorsitz, Firmenchef), lädt auf der Webseite eigene Bilder hoch.

## Hochladen
1. Im Spiel `/civ web` tippen und den Code auf der Webseite eingeben.
2. Reiter **Gesellschaft → Logos** öffnen. Dort stehen alle Einheiten, die du führst.
3. Einheit und Art wählen: **Wappen** (ersetzt das alte), **Wahlplakat** oder **Leuchtreklame** (je höchstens zehn).
4. Eine Bilddatei wählen (PNG, JPG, GIF oder WebP). Der Browser verkleinert sie auf höchstens 128 × 128 Punkte und zeigt eine Vorschau.
5. **Hochladen**. Unter dem Formular stehen die bisherigen Bilder, jedes lässt sich entfernen.

Nur die Spitze der Einheit darf hochladen; der Server prüft das bei jedem Upload.

## Darstellung im Spiel
- **Über dem Gemeindehaus** schwebt das **Wappen** der Gemeinde als Bild, das sich den Betrachtern zudreht. Es erscheint und wechselt von selbst (alle zehn Sekunden geprüft) und verschwindet, wenn das Wappen entfernt wird.
- **In der Hand:** `/civ logo <Einheit> [wappen|plakat|reklame]` gibt jedem Spieler das Bild als **Karte**. So lässt es sich in Rahmen hängen oder auf Mauern zeigen.

Die Bilder erscheinen als Karten, deshalb brauchen Spieler dafür keinen Client-Mod. Der Mod bildet jedes Bild auf die Kartenfarben ab; klare Motive mit wenigen Farben wirken am besten, Verläufe und feine Linien weniger. Transparente Stellen bleiben durchsichtig.

Die Bilddateien liegen im Bilderordner des Servers (`images`, benannt nach dem Hash), die Zuordnung Bild → Karte in `maps.tsv` darin.
