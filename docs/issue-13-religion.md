# Issue #13 – Religion

Domäne: `civ-domain/.../religion` (Religion, Konfessionen, Klerus-Wahl, `ReligionRegistry`), bewusst keine Polity.
Server: `ReligionService` + `ReligionStore` (`religion.tsv`, atomar ersetzt wie `family.tsv`).

Befehle `/civ religion`: `liste`, `info <Name>`, `gruenden <Name> <mono|poly> <Schrift> <Gotteshaus> <Sprache>`,
`beitreten`, `austreten`, `konfession <Name>`, `macht <0-100>`, `beten`, `gottesdienst`, `taufe <Spieler>`,
`segnen <Spieler>`, `wahl ausrufen|kandidieren|waehlen <Spieler>`.

Dorfbewohner: `Personality.religiosity()` (>0 gläubig, <0 atheistisch). Gläubige (>= 20) schließen sich täglich mit
15 % der größten Religion an, Atheisten nie. Beim Auszählen einer Klerus-Wahl stimmen Dorfbewohner-Mitglieder zufällig
mit; ein vakantes Amt wird alle 7 Tage neu ausgeschrieben. Auskunft beim Anschauen zeigt Glaube und Gläubigkeit.

Nicht umgesetzt: Logos/Aussehen von Schrift und Gotteshaus (nur Namen), Gebets-/Gottesdienst-Wirkung auf
Zufriedenheit oder Wahlverhalten (Machtgrad ist erfasst, aber noch ohne Wirkung), GUI-Screen, Konfession mit
Gotteshaus-Block, Meldungen/Befehlstexte nur auf Deutsch.
