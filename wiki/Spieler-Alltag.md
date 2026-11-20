[Home](Home.md) › Alltag

# Alltag: Verträge, Post, Freunde, Hoheitszeichen

## Verträge
`/civ vertrag` (oder Gemeindehaus → Verträge, oder die Webseite) verwaltet alle Verträge. Ein Angebot gilt, sobald die Gegenseite unterschreibt; für eine Gemeinde unterschreibt der Bürgermeister.

| Art | Anbieten | Wirkung |
|---|---|---|
| Miete | `/civ vertrag miete <Spieler> <Preis/Tag> [Kaution] [Tage]` | Kaution beim Unterschreiben, Miete täglich; Mieter nutzt das Grundstück wie der Eigentümer |
| Arbeit | `/civ vertrag arbeit <Spieler> <Lohn/Tag> [Tage]` / `arbeit dorfbewohner <Lohn>` | Lohn täglich |
| Frei | `/civ vertrag frei <Spieler> <einmalig> <täglich> <Titel> \| <Text>` | eigene Vereinbarung |
| Bündnis | `/civ vertrag buendnis <Gemeinde oder Bund>` (Bürgermeister) | Bürger der Verbündeten dürfen im Gebiet bauen wie Mitglieder |

`liste`, `zeigen <Nr>`, `annehmen <Nr>`, `ablehnen <Nr>`, `kuendigen <Nr>` (endet am nächsten Tag). Nicht gezahlte Raten lösen Vertragsbruch aus und die Kaution verfällt.

Gesetz `office_salary`: Bürgermeister, Amtsträger und Abgeordnete bekommen täglich ein Gehalt aus der Gemeindekasse.

## Post und Briefkasten (Block)
Rezept: Eisen, Papier, Truhe (`III / IPI / ICI`).
- Briefe kommen am nächsten Spieltag an. Schreiben: `/civ brief <Spieler> <Betreff> | <Text>`, am Briefkasten mit Buch und Feder, oder auf der Webseite.
- `/civ post` zeigt das Postfach, `/civ post lesen <Nr>` einen Brief.
- **Briefwahl:** erlaubt das Gesetz `postal_vote_allowed`, wählt man am Briefkasten; zählt wie an der Urne.

## Freunde und Chat
- `/civ freund <Spieler>` fragt an (Knöpfe [Annehmen]/[Ablehnen], alternativ `/civ freund annehmen|ablehnen <Spieler>`). `/civ freund entfernen`, `/civ freunde` (Menü).
- **Vertrauen:** `/civ freund vertrauen|misstrauen <Spieler>` – wem man vertraut, darf auf eigenen/gemieteten Grundstücken bauen, Behälter öffnen, Dinge benutzen.
- `/civ nachricht <Freund> <Text>` schickt eine Direktnachricht.
- Der allgemeine Chat erscheint auch auf der Webseite; von dort geschriebenes erscheint im Spiel mit „[Web]“.

## Flaggen und Hymnen
- **Flagge:** Banner am Webstuhl gestalten, in die Hand nehmen, `/civ <Ebene> flagge setzen`. `flagge holen` gibt ein Banner mit der Flagge, `flagge entfernen`.
- **Hymne:** `/civ <Ebene> hymne setzen <Titel> | [Instrument |] <Noten>`. Noten F#3–F#5, `*2` verdoppelt, `/2` halbiert die Länge, `-` ist eine Pause. Instrumente: `harp`, `bell`, `flute`, `chime`, `guitar`, `xylophone` u. a. `hymne spielen`, `hymne entfernen`. Mit `anthem_on_enter` hört man sie beim Betreten des Gebiets.
- **Eigenes Wappen, Wahlplakat oder Leuchtreklame hochladen:** Wer eine Gemeinde, ein Land, eine Nation oder einen Bund führt, kann zusätzlich zum Banner-Wappen ein selbst gemaltes Bild hochladen. Auf der Webseite in der Ansicht der jeweiligen Einheit: Reiter **Eigenes Bild** öffnen, Art wählen (Wappen, Wahlplakat oder Leuchtreklame), eine PNG-Datei (höchstens 128×128 Pixel) auswählen und hochladen. Ein neu hochgeladenes Wappen ersetzt das alte; von Wahlplakaten und Leuchtreklamen dürfen bis zu zehn gleichzeitig bestehen. Das Bild erscheint danach in der Webansicht der Einheit; eine Ingame-Darstellung auf einem eigenen Block ist als Folgeschritt vorgesehen (siehe `docs/issue-12-flaggen-banner.md`).

## Englische Befehle und Handbuch
Jeder Befehl gibt es auch auf Englisch, z. B. `/civ court case 3`, `/civ company invest …` (siehe [Befehlsübersicht](Befehlsuebersicht.md)). Neue Spieler bekommen beim ersten Betreten das **Civ-Handbuch** in ihrer Spielsprache; Nachschlag mit `/civ handbuch`.
