[Home](Home.md) › Befehlsübersicht

# Befehlsübersicht

Jeder deutsche Befehl unter `/civ` hat einen englischen Zwilling mit gleicher Funktion, z. B. `/civ gericht fall 3` ↔ `/civ court case 3`, `/civ firma investieren "Holz AG" 20` ↔ `/civ company invest "Holz AG" 20`. Argumentwerte wie Ämter (`buergermeister`), Branchen oder `schuldig` bleiben deutsch. Die Übersetzung ist mechanisch (ein Wörterbuch deutsch→englisch pro Befehlswort), die Logik ist identisch – siehe [Architektur](Entwickler-Architektur.md).

## Eingabehilfe
Alle Befehle schlagen beim Tippen (Tabulator) passende Werte vor, mit einer kurzen Erklärung daneben: Nummern eigener Bauaufträge, Fälle, Verträge und Briefe; Straßen, Firmen, Zeitungen, Parteien, Religionen und Gemeinden; Währungen und Symbole; Spielernamen; Beträge, Tage und Prozente; die Drehung in Grad; Machtgrade und die Achsen der Parteirichtung; Gegenstände; die Rechte der Klerus-Ämter.

## Allgemein
| Befehl | Wirkung |
|---|---|
| `/civ hilfe` | Überblick |
| `/civ info` | Chunk, Gemeinde, Kontostand, Tag |
| `/civ menue` | Menü der eigenen Gemeinde |
| `/civ karte` | Chunk-Karte; Linksklick Kerngebiet, Rechtsklick Außenposten |
| `/civ grenzen` | Gebietsgrenzen 10 Sekunden als Partikel |
| `/civ beitreten <Name>` / `/civ austreten` | Gemeinde beitreten / verlassen |
| `/civ liste` | alle Gemeinden |
| `/civ gemeinde [Name]` | Steckbrief einer Gemeinde |
| `/civ beanspruchen [aussenposten]` / `/civ freigeben` | Chunk beanspruchen / aufgeben |
| `/civ recht` / `recht setzen <Regel> <Wert>` | Regelungen anzeigen / ändern |
| `/civ handbuch` | Civ-Handbuch erneut erhalten |

## Finanzen
| Befehl | Wirkung |
|---|---|
| `/civ konto` / `einzahlen [Anzahl]` / `abheben <Anzahl>` | Kontostand, einzahlen, abheben |
| `/civ ueberweisen <Spieler\|Gemeinde> <Betrag>` | Geld überweisen |
| `/civ kasse einzahlen <Betrag>` / `kasse entnehmen <Betrag>` | Gemeindekasse (Entnahme nur Schatzmeister) |
| `/civ preis` / `verkaufen [Anzahl]` / `kaufen <Anzahl> <Item>` | Marktpreis, verkaufen, kaufen |

## Grundstücke und Läden
| Befehl | Wirkung |
|---|---|
| `/civ grundstueck [info]` / `ecke1` / `ecke2` | Grundstück unter einem, Ecken setzen |
| `/civ grundstueck erstellen\|chunk <Art>` | Auswahl/Chunk zum Grundstück machen |
| `/civ grundstueck anbieten\|zurueckziehen\|kaufen\|vergeben\|aufloesen` | Grundstücksmarkt |
| `/civ laden eroeffnen <Preis>` / `schliessen` / `liste` | Laden an der Kiste im Blick |

## Politik
| Befehl | Wirkung |
|---|---|
| `/civ abstimmungen` / `abstimmen <Nr> ja\|nein` | offene Abstimmungen, abstimmen |
| `/civ aemter` / `stimmung` | Ämter + laufende Wahlen, Stimmung der Dorfbewohner |
| `/civ wahl ausrufen\|kandidieren\|stimmen <Amt> …` | Wahlen |
| `/civ amt ernennen\|entlassen <Amt>` | Schatzmeister/Bauamt (Bürgermeister) |
| `/civ parlament [zeigen <Name>\|menue]` | Parlament anzeigen |
| `/civ partei [liste\|menue\|info [Name]]` / `gruenden\|beitreten\|austreten\|richtung\|wappen\|aufloesen` | Parteien |
| `/civ rede <Text>` / `versprechen geben <Text>` / `versprechen [liste [Name]]` | Wahlkampf |
| `/civ umfrage [Name]` / `/civ wahlergebnis [Name\|menue]` | Umfrage, letztes Wahlergebnis |

## Bünde und Ebenen
| Befehl | Wirkung |
|---|---|
| `/civ bund gruenden <Ebene> <Name>` / `einladen <Name>` / `beitreten <Bund>` | Bund gründen, einladen, beitreten |
| `/civ bund austreten` / `aufloesen` / `info [Name]` | Bund verlassen, auflösen, Info |
| `/civ ebenen` | eigene Gemeinde/Land/Nation mit Spitze |
| `/civ land\|nation <Befehl>` / `/civ ebene <Ebene> <Befehl>` | `info`, `menue`, `aemter`, `wahl …`, `amt …`, `parlament`, `recht [setzen]`, `abstimmungen`, `abstimmen`, `verfassung`, `gesetze`, `umfrage`, `wahlergebnis` für diese Ebene |
| `/civ <Ebene> hymne [spielen\|noten\|platte\|setzen\|entfernen]` | Hymne abspielen (mit Aufnahme, falls hochgeladen), nur als Notenblock-Melodie, als Schallplatte holen, festlegen, entfernen; die Aufnahme (OGG/MP3) lädt die Spitze auf der Webseite unter Staat hoch |
| `/civ <Ebene> wahlkreis\|debatte\|flagge\|hymne\|gericht …` | Wahlkreise, Debatten, Hoheitszeichen, Gericht einer Ebene |
| `/civ heim [Polity] [1-3]` | Zum Heimat-Hafen einer Polity porten (Standard: eigene Gemeinde, Hafen 1 = Gemeindehaus) |
| `/civ <Ebene> heim liste\|setzen 2\|3\|entfernen 2\|3\|zugang <keiner\|buerger\|verbuendete\|alle>\|preis <Betrag>` | Heimat-Häfen einer Ebene verwalten (Spitze) |

## Recht und Verfassung
| Befehl | Wirkung |
|---|---|
| `/civ verfassung` / `verfassung zeigen <Name>` / `verfassung menue` | Verfassung anzeigen |
| `/civ verfassung praeambel <Text>` / `ausrufen [Name]` / `artikel <Regel> <Wert>` | Präambel, Ausrufen, Artikel |
| `/civ gesetz [liste [Name]]` / `gesetz menue` / `gesetz zeigen <Nr>` / `pruefen <Text>` | Gesetzbuch |
| `/civ gesetz erlassen <Titel> \| <Text>` / `aufheben <Nr>` | Paragraph erlassen/aufheben |

## Justiz
| Befehl | Wirkung |
|---|---|
| `/civ gericht [faelle\|fall\|akte\|klage\|beweis\|aussage\|plaedoyer\|stimme\|urteil\|berufung …]` | siehe [Justiz](Spieler-Justiz.md) |

## Wirtschaft
| Befehl | Wirkung |
|---|---|
| `/civ firma gruenden\|info\|liste\|aufnehmen\|entlassen\|einstellen\|dividende\|ausgeben\|aufloesen` | Firmen |
| `/civ boerse [kaufen\|verkaufen\|depot\|buch\|streichen\|menue]` | Börse |
| `/civ zeitung gruenden\|redakteur\|entlassen\|schreiben\|veroeffentlichen\|lesen\|artikel\|liste\|menue\|aufloesen` | Presse |

## Alltag
| Befehl | Wirkung |
|---|---|
| `/civ strasse anlegen\|aufheben <Name>` / `strasse liste` | Straßen (Bürgermeister, Bauamt) |
| `/civ strasse bauen schaufel\|pflaster <Name>` / `strasse wege` | Weg auf einer Straße bauen lassen / Stand der Wegebauten |
| `/civ krieg beute <Nr> [<Chunks> <Punkte für Reparationen>]` | Siegpunkte eines gewonnenen Kriegs ansehen und ausgeben |
| `/civ grundstueck kaufen ["<Firma>"]` | angebotenes Grundstück unter dir kaufen, mit Firma aus der Firmenkasse für die Firma (nur Firmenleitung) |
| `/civ grundstueck uebertragen "<Firma>"` | eigenes Grundstück unter dir ohne Preis an die eigene Firma übergeben |
| `/civ kultur [setzen <Kultur> <Rasse>\|menue]` | Kultur und Rasse der eigenen Gemeinde ansehen und (Bürgermeister) ändern |
| `/civ dorfbewohner info\|menue` | Auskunft über den nächsten Dorfbewohner im Chat oder als Menü |
| `/civ bau vorschau <Plan> [Drehung]\|aus` | Hologramm eines Bauplans auf dem Grundstück unter dir |
| `/civ bau plaene\|liste\|beauftragen\|material\|genehmigen\|ablehnen\|abbrechen …` | Bauaufträge (ohne Drehung: Tür zur Straße), siehe [Bauen](Spieler-Bauen-und-Strassen.md) |
| `/civ religion schrift [lesen\|schreiben\|anhaengen\|benennen] …` | Heilige Schrift lesen und bearbeiten |
| `/civ religion segnen\|taufe <Spieler\|dorfbewohner>` | segnen und taufen, auch Dorfbewohner |
| `/civ religion amt festlegen\|vergeben\|entziehen\|abschaffen\|liste …` | Klerus-Ämter selbst definieren, siehe [Religion](Spieler-Religion.md) |
| `/civ logo <Einheit> [wappen\|plakat\|reklame]` | hochgeladenes Logo als Karte, siehe [Logos](Spieler-Logos.md) |
| `/civ adresse [Spieler]` / `/civ adressen` | Anschrift, Straßen und Hausnummern |
| `/civ vertrag …` / `vertrag menue` | Verträge |
| `/civ post [lesen <Nr>\|menue]` / `/civ brief <Spieler> <Betreff> \| <Text>` | Post |
| `/civ freund [entfernen] <Spieler>` / `/civ freunde [menue]` / `/civ nachricht <Freund> <Text>` | Freunde |
| `/civ web` / `/civ web abmelden` | Webseiten-Anmeldung |

## Nur für Admins
| Befehl | Wirkung |
|---|---|
| `/civ gruenden <Name>` | Gemeinde ohne Gemeindehaus gründen |
| `/civ admin umgehen` | Gebietsschutz für sich selbst an/aus |
| `/civ admin aufloesen <Name>` | Gemeinde/Bund auflösen |
| `/civ admin ebenen` | alle Ebenen mit Rang und Anzahl |
| `/civ admin tag` | laufenden Tag sofort beenden (Steuern, Einnahmen, fällige Wahlen/Abstimmungen) – praktisch zum Testen |
| `/civ admin sicherung` | sofortige Datenbanksicherung |

Benötigt den Rechte-Knoten `eddies-civ-mod:admin`, siehe [Installation und Konfiguration](Admin-Installation-und-Konfiguration.md#rechte).

## Regierung (Ministerien, Haushalt, Oberhaupt)

| Befehl | Wirkung |
|---|---|
| `/civ regierung` | Oberhaupt, Regeln, Ministerien und Haushalt anzeigen |
| `/civ regierung oberhaupt titel\|form\|modus\|gehalt\|befugnisse ...` | Oberhaupt konfigurieren (nur Bürgermeister) |
| `/civ regierung regeln <höchstzahl> <tage> <prozent>` | Zahl der Ministerien, Haushaltsrhythmus, nötige Mehrheit |
| `/civ regierung vorbereiter "<Ministerium>"` | Ministerium, das den Haushalt vorbereitet |
| `/civ regierung ministerium anlegen "<Name>" <wahl\|ernennung> [Zuständigkeit]` | Ministerium anlegen (auflösen: `aufloesen`) |
| `/civ regierung ministerium ernennen\|abberufen\|einstellen\|kuendigen "<Name>" [Spieler]` | Minister und Angestellte |
| `/civ regierung ministerium lohn "<Name>" <Minister> <Angestellte>` | Tageslöhne |
| `/civ regierung haushalt entwurf "<Name>" <Betrag>` | Etat im Entwurf setzen |
| `/civ regierung haushalt abstimmen <true\|false>` / `schliessen` | Abstimmung und Beschluss |
| `/civ regierung koalition bilden` / `entlassen` / `neuwahl` | Regierung bilden, entlassen, Neuwahlen anordnen (Oberhaupt) |
| `/civ regierung koalition regeln <abstand> <minderheit>` | wie weit Koalitionspartner auseinanderliegen dürfen, Minderheitsregierung erlaubt |
| `/civ regierung koalition vorhaben <Regelung> <Wert>` | Vorhaben im Koalitionsvertrag |
| `/civ regierung ministerium bereich "<Name>" <finanzen\|inneres\|verteidigung\|wirtschaft\|kanzleramt\|frei>` | Handlungsbereich |
| `/civ regierung ministerium vorschlag "<Name>" <Regelung> <Wert>` | Gesetzesvorschlag bzw. Verordnung des Ministers |
| `/civ regierung ministerium verordnung "<Name>" <true\|false>` | Verordnungsrecht erlauben |
| `/civ regierung foerderung anlegen "<Name>" "<Ministerium>" <Betrag> <alle\|Name> <Tage> [<voll bis> <Auslauf>]` | Förderung; auch `liste`, `beenden`, `abstimmen` |
| `/civ regierung wahl ausrufen\|kandidieren\|waehlen "<Ministerium>" [Spieler]` | Volkswahl der Minister |
| `/civ regierung gesetze` | beschlossene Gesetze, die auf die Unterschrift warten |
| `/civ regierung gesetze unterzeichnen <Nr>` / `veto <Nr>` | Oberhaupt: sofort in Kraft setzen oder verwerfen |
| `/civ regierung gesetze vetofrist <Tage>` | Wartezeit (0 = kein Veto) |

Englisch: `/civ government ...`
