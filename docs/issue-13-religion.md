# Issue #13 – Religion

Domäne: `civ-domain/.../religion` (Religion, Konfessionen, Klerus-Wahl, `ReligionRegistry`), bewusst keine Polity.
Server: `ReligionService` + `ReligionStore` (`religion.tsv`, atomar ersetzt wie `family.tsv`).

Befehle `/civ religion`: `liste`, `info <Name>`, `gruenden <Name> <mono|poly> <Schrift> <Gotteshaus> <Sprache>`,
`beitreten`, `austreten`, `konfession <Name>`, `macht <0-100>`, `beten`, `gottesdienst`, `taufe <Spieler>`,
`segnen <Spieler>`, `wahl ausrufen|kandidieren|waehlen <Spieler>`.

Dorfbewohner: `Personality.religiosity()` (>0 gläubig, <0 atheistisch). Gläubige (>= 20) schließen sich täglich mit
15 % der größten Religion an, Atheisten nie. Beim Auszählen einer Klerus-Wahl stimmen Dorfbewohner-Mitglieder zufällig
mit; ein vakantes Amt wird alle 7 Tage neu ausgeschrieben. Auskunft beim Anschauen zeigt Glaube und Gläubigkeit.

Wirkungen: Beten (1x/Tag/Spieler, +1) und Gottesdienste (+3) erhöhen die **Inbrunst** der Religion (max. 6, sinkt täglich
um 1). Gläubige Mitglieder erhalten Zufriedenheitspunkte `1 + Inbrunst/2 + Macht/50`; Atheisten spüren -1, wenn ein
Klerus mit Macht >= 70 und Mitgliedern existiert. Die Zufriedenheit fließt in Amtsbonus und Wahlverhalten ein.
Logo: `/civ religion logo <Symbol>`; GUI: `/civ religion menue`. Alle Meldungen de/en (Fehlertexte aus der Domäne bleiben deutsch).

Nicht umgesetzt: eigenes Aussehen (Blöcke/Texturen) von Schrift und Gotteshaus.
