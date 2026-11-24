[Home](Home.md) › Religion

# Religion, Heilige Schrift und Klerus

Religionen haben kein Gebiet und keine Kasse; sie bestehen aus Mitgliedern (Spieler und Dorfbewohner), einer Heiligen Schrift, einem Gotteshaus und dem Klerus. Dorfbewohner mit starkem Glauben gründen auch von selbst welche.

## Grundbefehle
| Befehl | Wirkung |
|---|---|
| `/civ religion liste` / `info <Name>` | alle Religionen / Steckbrief |
| `/civ religion gruenden <Name> <mono\|poly> <Schrift> <Gotteshaus> <Sprache>` | eine Religion gründen |
| `/civ religion beitreten <Name>` / `austreten` | Mitglied werden / gehen |
| `/civ religion beten` / `gottesdienst` | beten; Gottesdienst (Oberhaupt oder Amt mit Recht `gottesdienst`) stärkt die Inbrunst |
| `/civ religion wahl ausrufen\|kandidieren\|waehlen <Spieler>` | Wahl um das Klerus-Oberhaupt |
| `/civ religion konfession <Name>` / `macht <0-100>` / `logo <Zeichen>` | Konfession, Machtgrad, Kurzlogo |

## Segnen und taufen
`/civ religion segnen <Spieler|dorfbewohner>` und `/civ religion taufe <Spieler|dorfbewohner>`: Mit `dorfbewohner` trifft es den **nächsten Dorfbewohner** in Reichweite (6 Blöcke). Ein **Segen** stärkt einen Dorfbewohner fünf Tage lang (+2 Zufriedenheit durch Glauben) und die Inbrunst der Religion; eine **Taufe** nimmt ihn in die Religion auf. Beides dürfen das Oberhaupt und jeder Amtsträger mit dem Recht `segnen` bzw. `taufen`; die Zeremonie erscheint in der Liste der letzten Riten und als Meldung.

## Die Heilige Schrift bearbeiten
| Befehl | Wirkung |
|---|---|
| `/civ religion schrift` / `schrift lesen [Religion]` | Wortlaut lesen (eigene oder fremde Religion) |
| `/civ religion schrift schreiben <Text>` | den Wortlaut ersetzen; `\n` im Text ist ein Zeilenumbruch |
| `/civ religion schrift anhaengen <Text>` | einen Absatz anhängen |
| `/civ religion schrift benennen schrift\|gotteshaus\|sprache <Name>` | Schrift, Gotteshaus oder Sprache umbenennen |

Höchstens 4000 Zeichen. Bearbeiten dürfen Gründer, Oberhaupt und Amtsträger mit dem Recht `schrift`. Auf der Webseite (Reiter **Religion**) gibt es dafür einen Editor.

## Klerus-Ämter selbst festlegen
Gründer und Oberhaupt erfinden die Ämter ihres Klerus frei, mit Namen und Rechten:

| Befehl | Wirkung |
|---|---|
| `/civ religion amt festlegen "<Name>" [segnen] [taufen] [gottesdienst] [schrift]` | ein Amt anlegen oder seine Rechte ändern (höchstens 12 je Religion) |
| `/civ religion amt vergeben "<Name>" <Spieler\|dorfbewohner>` | das Amt einem Mitglied geben |
| `/civ religion amt entziehen <Spieler\|dorfbewohner>` | alle Ämter eines Mitglieds entziehen |
| `/civ religion amt abschaffen "<Name>"` / `amt liste` | Amt löschen / alle Ämter mit Rechten und Trägern |

Die Rechte: `segnen`, `taufen`, `gottesdienst`, `schrift`. Beispiel: ein „Priester“ mit `segnen taufen gottesdienst` und ein „Schriftwart“ mit `schrift`. Ämter können auch Dorfbewohnern gegeben werden.

Gespeichert wird alles in `religion.tsv` im Datenordner.
