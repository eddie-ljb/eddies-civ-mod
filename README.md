# Eddies Civ Mod

Fabric-Mod für Minecraft 26.3: Gemeinden, Gebiete, Recht, Geld und ein Servermarkt mit dynamischen Preisen.
Alle Menüs laufen auf dem Server als Truhenmenüs. Spieler ohne den Mod können deshalb auf einem Server mit dem Mod mitspielen.

## Für Spieler

### Gemeinde gründen und beitreten
1. **Gemeindehaus** herstellen: Smaragd, Steinziegel, Glocke (`ESE / SBS / ESE`).
2. Im Amboss benennen. Der Name wird der Name der Gemeinde.
3. Aufstellen. Der Chunk wird Kerngebiet und das Gemeindehaus der Sitz. Der Block ist unzerstörbar.
4. Ein **Rechtsklick** auf das Gemeindehaus öffnet das Menü:
   - Steckbrief
   - Mitglieder
   - Recht
   - Gemeindekasse
   - Karte
   - Chunk beanspruchen
   - Grundstücke
   - Abstimmungen
   - Beitreten oder Austreten
   - Auflösen (nur Bürgermeister, zweimal Shift-Klick)

Wer eine Gemeinde betritt oder verlässt, sieht ihren Namen als Titel. Auf Grundstücken erscheinen Inhaber und gegebenenfalls der Verkaufspreis über der Schnellzugriffsleiste.

**Gemeindehaus verlegen:** Der Bürgermeister setzt ein neues Gemeindehaus irgendwo im eigenen Gebiet. Das alte verschwindet und landet wieder im Inventar.

### Dorfbewohner
- Dorfbewohner erhalten dauerhaft einen Vor- und Nachnamen. Schleichen + Rechtsklick mit leerer Hand zeigt auch die eindeutige Registernummer. `/civ dorfbewohner register [Seite]` listet Spieler und Dorfbewohner der eigenen Gemeinde.
- Dorfbewohner, die das Gebiet einer Gemeinde betreten, werden dort Einwohner. In der Wildnis bleiben sie in ihrer bisherigen Gemeinde gemeldet. Stirbt ein Dorfbewohner, tritt er aus.
- Jeder Dorfbewohner erwirtschaftet täglich Geld für die Gemeindekasse; Standard ist ¼ Smaragd. Zufriedene leisten bis zu 50 % mehr, Unzufriedene bis zu 50 % weniger.
- **Persönlichkeit:** Jeder Dorfbewohner hat feste Ansichten auf drei Achsen: *Ordnung ↔ Freiheit*, *Sparsamkeit ↔ Großzügigkeit* und *Heimatverbundenheit*.
- **Zufriedenheit** (-100 bis 100) setzt sich zusammen aus Grundstimmung, *Ordnung* (wie streng Explosionen, Feuer und Rechte von Fremden geregelt sind; Ordnungsliebende mögen es streng, Freigeister locker), *Steuern* (stören jeden, Sparsame am meisten), *Mitsprache* (gewählter Bürgermeister, Abstimmungen, Wahlrecht für Dorfbewohner) und *Wohlstand* (Siedlungsstufe).
- **Abwanderung:** Wer unter -30 Zufriedenheit fällt, denkt ans Wegziehen; je unzufriedener und rastloser, desto eher geht er am Tagesende. Gibt es eine Gemeinde, in der er deutlich zufriedener wäre, zieht er dorthin und erscheint neben deren Gemeindehaus, sonst verschwindet er in die Wildnis. Höchstens ein Viertel der Dorfbewohner geht pro Tag. Abschaltbar mit `villager_emigration=false` in der Konfiguration.
- Im Gemeindehaus zeigt ein Klick auf die Glocke die Stimmung im Detail, `/civ stimmung` fasst sie im Chat zusammen. Schleichen + Rechtsklick mit leerer Hand auf einen Dorfbewohner zeigt seine Persönlichkeit und Zufriedenheit. Zufriedene Dorfbewohner lassen ab und zu grüne Funken aufsteigen, unzufriedene kleine Gewitterwolken.
- Die Siedlungsstufe richtet sich nach allen Einwohnern: ab 10 Marktgemeinde, ab 30 Stadt, ab 80 Metropole. Jede Stufe erlaubt 8 Chunks mehr Gebiet.
- Für die Gebietsgröße zählen Dorfbewohner nur zu einem Viertel. So lässt sich Land nicht allein durch Zucht vergrößern.

### Läden
Spieler verkaufen eigene Waren an andere:
1. Item in die Hand nehmen, auf eine Kiste schauen und `/civ laden eroeffnen 1,5` tippen. Das geht nur, wo du bauen darfst.
2. Die Ware in die Kiste legen.
3. Andere öffnen per Rechtsklick den Laden statt der Kiste und kaufen 1, 16 oder 64 Stück.

Das Geld geht direkt auf dein Konto. Im Gemeindegebiet erhält die Gemeinde ihre **Umsatzsteuer** (Gesetz, Standard 0 %). Fremde können die Ladenkiste nicht abbauen. Schließen geht mit `/civ laden schliessen`, eine Übersicht gibt `/civ laden liste`. Unter fremde Ladenkisten lassen sich keine Trichter setzen, auch nicht in der Wildnis. Am sichersten steht ein Laden trotzdem im Gemeindegebiet oder auf dem eigenen Grundstück.

Im Kreativmodus liegen alle Blöcke des Mods, der Grundstücksstab und nützliche Test-Items im eigenen Reiter **Eddies Zivilisation**.

### Grundstücke
Grundstücke teilen das Gemeindeland auf. Nur ihre Inhaber dürfen dort bauen, und nur für sie fällt Grundsteuer an.
1. Der Bürgermeister oder das Bauamt stellt sich an eine Ecke und tippt `/civ grundstueck ecke1`, an der gegenüberliegenden Ecke `ecke2`. Bequemer geht es mit dem **Grundstücksstab** (Stock + Smaragd diagonal): Linksklick auf einen Block setzt Ecke 1, Rechtsklick Ecke 2. Partikel zeigen die Auswahl.
2. `/civ grundstueck erstellen wohnen` macht daraus ein Grundstück der Gemeinde. `/civ grundstueck chunk wohnen` nimmt stattdessen den ganzen Chunk.
   Mögliche Arten: `wohnen`, `gewerbe`, `industrie`, `landwirtschaft`, `oeffentlich`, `botschaft`.
3. Danach gibt es zwei Möglichkeiten:
   - `/civ grundstueck anbieten 12,5` bietet es zum Verkauf an. Jeder kann es mit `/civ grundstueck kaufen` oder per Shift-Klick im Grundstücksmenü erwerben.
   - `/civ grundstueck vergeben <Spieler>` verschenkt es.
4. Inhaber bieten ihr eigenes Grundstück genauso an. `/civ grundstueck` zeigt das Grundstück unter dir und seinen Umriss.

### Abstimmungen
Gilt das Gesetz **Rechtsänderungen per Abstimmung**, wird jede Änderung im Recht-Menü oder per Befehl zum Antrag.
- Die Mitglieder werden benachrichtigt. Bürger stimmen im Gemeindehaus unter **Abstimmungen** ab (Linksklick dafür, Rechtsklick dagegen) oder mit `/civ abstimmen <Nr> ja|nein`.
- Hat mehr als die Hälfte der Bürger zugestimmt, gilt die Änderung sofort. Hat mindestens die Hälfte abgelehnt, ist sie gescheitert.
- Sonst wird nach **Dauer einer Abstimmung** (Standard: 2 Tage) ausgezählt. Dann entscheidet die Mehrheit der abgegebenen Stimmen; bei Gleichstand gilt der Antrag als abgelehnt.
- Dorfbewohner und Einwohner ohne Bürgerrecht stimmen nicht mit ab.
- Ohne Abstimmungen ändert nur der Bürgermeister das Recht.

### Ämter und Wahlen
Jede Gemeinde hat drei Ämter: **Bürgermeister**, **Schatzmeister** und **Bauamt**.

| Amt | Darf |
|---|---|
| Bürgermeister | Recht ändern (ohne Abstimmungen), Kasse verwalten, Grundstücke verwalten, Sitz verlegen, Gemeinde auflösen, Schatzmeister und Bauamt ernennen oder entlassen |
| Schatzmeister | Geld aus der Gemeindekasse nehmen |
| Bauamt | Grundstücke der Gemeinde anlegen, anbieten, vergeben und auflösen |

- Solange niemand gewählt wurde, ist der dienstälteste Bürger kommissarisch Bürgermeister, anfangs der Gründer.
- **Wahl ausrufen:** im Gemeindehaus unter **Ämter und Wahlen** per Umschalt-Klick auf das Amt oder mit `/civ wahl ausrufen <Amt>`. Der Bürgermeister darf das jederzeit, andere Bürger nur, wenn das Amt frei oder die Amtszeit abgelaufen ist.
- **Kandidieren und wählen:** Klick aufs Amt bzw. auf einen Kandidaten, oder `/civ wahl kandidieren <Amt> [Programm]` und `/civ wahl stimmen <Amt> <Spieler>`. Programme: `ordnung`, `freiheit`, `steuern` (niedrige Steuern), `ausbau`, `umwelt`, `soziales`, `wirtschaft`, `bildung`, `tradition`, `fortschritt`, `frieden`, `staerke`, `beteiligung`, `landwirtschaft`, `heimat` oder `ohne`. Dorfbewohner wählen das Programm, das zu ihrer Persönlichkeit passt (z. B. Naturfreunde `umwelt`, Gemeinschaftssinnige `soziales`, Traditionsbewusste `tradition`). Die Stimme lässt sich bis zum Ende ändern. Wer wen gewählt hat, bleibt geheim.
- **Auszählung** am Ende von Tag *Ausruf + Wahldauer* (Standard 2 Tage). Es gewinnt die höchste Stimmenzahl; bei Gleichstand der Amtsinhaber, sonst wer zuerst kandidiert hat. Hat niemand kandidiert, bleibt alles, wie es ist.
- **Amtszeit:** Mit `term_days` größer 0 laufen Ämter ab und werden automatisch neu gewählt, das Bürgermeisteramt auch, wenn es noch nie gewählt wurde. Bei 0 (Standard) sind Ämter unbefristet.
- **Dorfbewohner wählen mit**, wenn `villager_vote_percent` größer 0 ist; ihre Stimme zählt mit diesem Gewicht (100 = wie ein Bürger). Kandidieren können sie nicht. Sie wählen nach ihrer Persönlichkeit das passende Programm; wer unter Steuern leidet, neigt zu „Niedrige Steuern“. Zufriedene halten zum Amtsinhaber, Unzufriedene wählen den Wechsel. Wem kein Kandidat zusagt, bleibt zu Hause.
- Wer austritt, verliert seine Ämter, seine Kandidatur und seine Stimme.

### Bünde und Ebenen
Gemeinden schließen sich zu **Bünden** zusammen: Länder, Nationen und eigene Ebenen wie Kreis oder Bund (siehe `federation_levels`). Eingebaut sind Gemeinde (Rang 0), Land (20) und Nation (30); ein Bund steht immer höher als jedes seiner Glieder, also z. B. Gemeinde › Kreis › Land › Nation.

- **Gründen:** Wer eine Gemeinde führt, gründet mit `/civ bund gruenden <Ebene> <Name>` einen Bund darüber; die eigene Gemeinde ist erstes Glied, der Gründer hat den Vorsitz. Wer schon einen Bund führt, gründet so die nächste Ebene darüber.
- **Aufnehmen:** Der Vorsitz lädt mit `/civ bund einladen <Gemeinde oder Bund>` ein, wer die eingeladene Gemeinde führt, nimmt mit `/civ bund beitreten <Bund>` an. Einladungen gelten bis zum Neustart.
- **Mitglieder:** Alle Bürger und Dorfbewohner der Glieder gehören automatisch auch zum Bund und allen Bünden darüber; wer einer Gemeinde beitritt oder sie verlässt, tut das auch dort. Einzelne Spieler treten nur Gemeinden bei.
- **Austreten und auflösen:** `/civ bund austreten` (die oberste Ebene, die du führst, verlässt ihren Bund; das letzte Glied kann nicht austreten), `/civ bund aufloesen` (Glieder werden frei, die Kasse geht an den Vorsitz). Löst sich das letzte Glied eines Bundes auf, verschwindet der Bund mit.
- **Anzeige:** `/civ bund info [Name]`, im Gemeindehaus-Steckbrief „Gehört zu: …“ und auf der Webseite unter **Bünde**.
- Wer mehrere Ebenen zugleich führt, handelt mit `/civ bund …` immer für die oberste.
- **Befehle je Ebene:** `/civ gemeinde …`, `/civ land …`, `/civ nation …` (eigene Ebenen aus `federation_levels` unter ihrer Kennung, sonst `/civ ebene <Ebene> …`) wirken genau auf die Gemeinde, das Land oder die Nation, zu der die eigene Gemeinde gehört: `info`, `menue`, `aemter`, `wahl ausrufen|kandidieren|stimmen <Amt> …`, `amt ernennen|entlassen`, `parlament`, `recht [setzen <Regel> <Wert>]`, `abstimmungen`, `abstimmen <Nr> ja|nein`, `verfassung [ausrufen]`, `gesetze`, `umfrage`, `wahlergebnis`. Beispiel Nationalparlament: `/civ nation recht setzen parliament_seats 9`, dann `/civ nation wahl ausrufen parlament`. `/civ ebenen` zeigt die eigene Kette.
- **Menü je Ebene:** `/civ land menue` bzw. `/civ nation menue` oder im Gemeindehaus oben rechts (Leuchtfeuer). Oben wechseln Reiter zwischen Gemeinde, Land und Nation; darunter Ämter, Parlament, Wahlen und Umfragen, Abstimmungen, Recht, Verfassung und Gesetzbuch genau dieser Ebene.
- Bünde haben eigene Organe: **Vorsitz** (statt Bürgermeister) und **Schatzmeister**, gewählt von allen Bürgern der Glieder. Wahlen und Abstimmungen laufen über das Verfassungsmenü des Bundes, Gesetze über das Gesetzbuch des Bundes.

### Verfassung (Block)
Jede Gemeinde und jeder Bund hat eine Verfassung. Anfangs ist sie ein **Entwurf**: Die Spitze (Bürgermeister bzw. Vorsitz) legt Präambel und Verfassungsartikel frei fest. Mit dem **Ausrufen** tritt sie in Kraft; danach steht die Präambel fest, und die Artikel ändern sich nur noch durch Abstimmung mit der Verfassungsmehrheit (Standard 66 % = zwei Drittel), auch wenn sonst der Bürgermeister verordnet.

- **Verfassungsartikel:** `laws_by_vote`, `vote_days`, `term_days`, `election_days`, `villager_vote_percent`, `amendment_majority_percent` (50–100), `federal_law_prevails`.
- **Block „Verfassung“** (Rezept: Goldbarren, Buch und Feder, Feder, Schwarzeichenbretter; auch im Kreativ-Reiter): Rechtsklick zeigt die Verfassung der Gemeinde, in deren Gebiet er steht – Präambel, Artikel, Organe, offene Änderungsanträge, übergeordneter Bund und Glieder, jeweils anklickbar. Die Spitze ruft dort per Umschalt-Klick aus. Auch das Gemeindehaus hat unten in der Mitte einen Knopf **Verfassung**.
- **Abschrift:** Im Menü „Abschrift nehmen“ gibt ein Item; Rechtsklick damit öffnet diese Verfassung überall, z. B. die des Landes.
- **Für welche Ebene?** `praeambel`, `artikel` und `ausrufen` ohne Namen gelten für die oberste Ebene, die du führst, sonst für deine Gemeinde. Über die Artikel deiner Gemeinde stimmst du auch mit `/civ recht setzen` ab.
- **Abstimmung:** Ein Änderungsantrag ist angenommen, sobald die Verfassungsmehrheit aller Bürger zugestimmt hat; abgelehnt, sobald sie nicht mehr erreichbar ist. Am Fristende zählt die Quote der abgegebenen Stimmen.

### Gesetzbuch (Item)
Neben den Regelungen gibt es **Paragraphen**: Titel plus frei formulierter Text. Was das Spiel im Text erkennt, setzt es als Regelung durch; der Rest gilt als Text und wird später Sache der Gerichte.

- **Erkannt werden** (Deutsch und Englisch, auch ohne Umlaute): Explosionen/TNT, Feuer, was Fremde dürfen (bauen, Türen/Knöpfe/Betten, Truhen/Öfen), Grundsteuer (Zahl = Hundertstel, „0,05 Smaragde“ = 5), steuerfreie Blöcke und Tage, Steuern für Botschaften, Umsatzsteuer in %, Zahl der Außenposten, Amtszeit, Wahl- und Abstimmungsdauer, Wahlrecht der Dorfbewohner, Abstimmungspflicht, Verfassungsmehrheit („zwei Drittel“, „drei Viertel“, „einstimmig“) und Vorrang des Bundesrechts. Beispiele: „Explosionen sind verboten.“, „Fremde dürfen weder bauen noch Truhen öffnen.“, „Die Umsatzsteuer beträgt 10 %.“
- **Erlassen:** `/civ gesetz erlassen <Titel> | <Text>`, oder Text in ein Buch mit Feder schreiben, in die Hand nehmen und `/civ gesetz erlassen <Titel>` – bzw. im Gesetzbuch-Menü „Paragraph aus Buch und Feder“ (erste Zeile = Titel). `/civ gesetz pruefen <Text>` zeigt vorher, was erkannt würde.
- **Wer?** Ohne Abstimmungspflicht erlässt die Spitze sofort; mit Abstimmungspflicht oder wenn ein Verfassungsartikel einer ausgerufenen Verfassung berührt ist, wird ein Antrag daraus (mit Verfassungsmehrheit). Aufheben (`/civ gesetz aufheben <Nr>` oder Umschalt-Klick im Menü) folgt denselben Regeln.
- **Gesetzbuch-Item** (Rezept: Buch, Goldklumpen, roter Farbstoff): Rechtsklick öffnet das Gesetzbuch der Gemeinde, in der du stehst. Außerdem im Gemeindehaus (unten links neben „Verfassung“) und im Verfassungsmenü. Von dort geht es zum Gesetzbuch des übergeordneten Bundes.
- **Bundesrecht bricht Gemeinderecht:** Regelt ein Bund etwas ausdrücklich und gilt dort `federal_law_prevails` (Standard ja), gilt sein Wert in allen Gliedern – beim Gebietsschutz, bei Explosionen, Feuer, PvP, Monstern und Tieren, bei Grund- und Umsatzsteuer, beim Außenposten-Limit und bei der Zufriedenheit der Dorfbewohner. Verfahrensregeln (Verfassungsartikel) bleiben Sache jeder Ebene. Im Rechtsmenü und bei `/civ recht` steht dann „Bundesrecht von …“.

### Parteien
Parteien sind Zusammenschlüsse von Personen (Spieler und Dorfbewohner) über alle Gemeinden hinweg. Jede Person gehört höchstens einer Partei an.

- **Richtung:** zwei Achsen von -100 bis 100: Ordnung (− Freiheit, + Ordnung) und Wirtschaft (− niedrige Steuern, + Ausbau). Die stärkere Achse ergibt das Wahlprogramm der Partei.
- **Befehle:** `/civ partei gruenden <ordnung|freiheit|steuern|ausbau|mitte> <Name>`, `beitreten <Name>`, `austreten`, `info [Name]`, `liste`, `menue`; nur der Vorsitz: `richtung <Ordnung> <Wirtschaft>`, `wappen` (Item in der Hand wird Logo), `aufloesen`. Geht das letzte Mitglied, löst sich die Partei auf.
- **Menü:** im Gemeindehaus unten rechts „Parteien“: Klick tritt bei, Umschalt-Klick auf die eigene tritt aus.
- **Wahlen:** Wer ohne eigenes Programm kandidiert, tritt mit dem Programm seiner Partei an; bei Kandidaten steht die Partei dabei (Menü und Webseite). Dorfbewohner wählen bevorzugt Kandidaten ihrer Partei, sonst Parteien, die ihrer Persönlichkeit nahe sind.
- **Dorfbewohner:** treten täglich mit etwas Glück einer Partei bei, die zu ihrer Persönlichkeit passt (Ordnungsliebe; Sparsamkeit = niedrige Steuern), und verlassen sie, wenn sie sich zu weit entfernt. Finden sich in einer Gemeinde genug parteilose Gleichgesinnte (`villager_party_founders`, Standard 5) ohne passende Partei, gründen sie eine, z. B. „Ordnungspartei Nordhain“; der Heimatverbundenste übernimmt den Vorsitz. Abschaltbar mit `villager_parties=false`.

### Parlament
Gemeinden und Bünde können ein Parlament haben. Die Regelung `parliament_seats` (Sitze im Parlament, 0–99; Verfassungsartikel) legt fest, wie viele Sitze es hat; 0 heißt kein Parlament.

- **Wahl:** Sieht das Recht Sitze vor und sitzt noch kein Parlament (oder ist die Wahlperiode `term_days` um), wird am Tagesende automatisch eine Parlamentswahl ausgerufen; per Hand mit `/civ wahl ausrufen parlament`. Kandidieren: `/civ wahl kandidieren parlament [Programm]`, wählen: `/civ wahl stimmen parlament <Name>`, oder alles im Menü **Parlament** (Gemeindehaus unten Mitte, Verfassungsmenü).
- **Verhältniswahl:** Jede Stimme zählt für den Kandidaten und seine Partei. Die Sitze gehen nach Sainte-Laguë an die Parteien, innerhalb einer Partei an die Kandidaten mit den meisten Stimmen; Parteilose treten einzeln an. Das Präsidium bekommt der stärkste Kandidat der stärksten Partei.
- **Gesetzgebung:** Sobald ein Parlament sitzt, gibt es keine Verordnungen mehr. Jede Rechtsänderung wird ein Antrag (stellen darf ihn jeder Bürger), abstimmen dürfen nur die Abgeordneten, je Sitz eine Stimme. Mehr als die Hälfte der Sitze entscheidet sofort, sonst die abgegebenen Stimmen nach `vote_days`; Verfassungsartikel brauchen weiter die Verfassungsmehrheit.
- **Dorfbewohner:** Haben Dorfbewohner Wahlrecht, kandidieren Parteimitglieder unter ihnen für ihre Partei (je Partei höchstens so viele, wie es Sitze gibt). Gewählte Dorfbewohner stimmen am Tagesende nach Persönlichkeit und Parteilinie ab und stellen an etwa jedem dritten Tag selbst Anträge, z. B. Schutzregeln lockern oder Steuern senken. Ein Parlament hebt ihre Zufriedenheit (Mitsprache), mehr noch, wenn Dorfbewohner darin sitzen.
- **Auflösen:** Setzt das Parlament `parliament_seats` auf 0, löst es sich am Tagesende auf.
- **Nachrücken:** Verlässt ein Abgeordneter mit Partei die Gemeinde, rückt der nächste nicht gewählte Kandidat derselben Partei nach; der Sitz eines Parteilosen bleibt frei.
- **Sitzungen:** Das Parlament tagt alle `session_days` Tage (Standard: täglich). Nur an Sitzungstagen stimmen Dorfbewohner-Abgeordnete ab, stellen Anträge und werden fällige Anträge ausgezählt; der Chat kündigt die Sitzung mit der Tagesordnung an.

### Wahlurne, Wahlbezirke und Wahlkarte
- **Wahlurne** (Block): Rechtsklick zeigt alle laufenden Wahlen der Gemeinde am Ort und ihrer Bünde mit den Kandidaten; Klick wählt. Mit `ballot_box_required` zählen nur Stimmen an der Urne, nicht per Menü, Befehl oder Webseite.
- **Wahlbezirke:** Jede Stimme zählt in der Gemeinde des Wählers. Bei Wahlen eines Bundes meldet der Chat, wer in welchem Bezirk vorn lag. Die letzten 10 Ergebnisse jeder Gemeinde und jedes Bundes bleiben gespeichert: `/civ wahlergebnis [Name | menue]`.
- **Umfragen:** `/civ umfrage [Name]` fragt alle Dorfbewohner, wen sie bei laufenden Wahlen wählen würden (auch ohne Wahlrecht; Spielerstimmen bleiben geheim). Beim Parlament steht eine Sitzprognose nach Sainte-Laguë dabei.
- **Menü „Wahlen und Umfragen“** (Gemeindehaus unten links, Parlamentsmenü unten rechts): Umfragen und Ergebnisse je Bezirk.
- **Webseite:** Reiter „Wahlen“ mit Umfragen und Ergebnissen, filterbar nach Ebene und Partei. Auf der Karte färbt die Ansicht „Wahlkarte“ jede Gemeinde nach dem Führenden ihres Bezirks; der Parteifilter hebt die Bezirke einer Partei hervor.

### Wahlverfahren
Jede Ebene stellt im Recht ein, wie gewählt wird (Menü **Recht**: Klick schaltet durch die Möglichkeiten):

| Regel | Möglichkeiten |
|---|---|
| `office_voting` (Ämter) | `plurality`: relative Mehrheit, „First past the post“ · `runoff`: absolute Mehrheit, sonst Stichwahl der beiden Besten (neue Wahl, gleiche Dauer) |
| `parliament_voting` (Parlament) | `sainte_lague`, `dhondt`, `hare_niemeyer` (Verhältniswahl) · `majority` (Mehrheitswahl ohne Ausgleich) · `districts` (Mehrheitswahl je Wahlkreis) · `mixed` (Wahlkreissieger sicher, Rest als Ausgleich nach Sainte-Laguë) |
| `ballot_type` | `person`: Personenwahl · `list`: Listenwahl; man wählt eine Partei (`/civ wahl stimmen parlament <Partei>`), ihre Sitze gehen der Reihe der Kandidatur nach an ihre Kandidaten |
| `threshold_percent` | Sperrklausel 0–20 % für Verhältniswahlen |
| `term_limit` | wie oft jemand dasselbe Amt in Folge haben darf (0 = beliebig) |
| `compulsory_voting`, `voting_fine` | Wahlpflicht für Spieler; wer nicht wählt, zahlt die Strafe (Hundertstel Smaragd) an die Kasse |
| `election_debates` | ob am letzten Wahltag automatisch eine Debatte läuft |

### Wahlkreise
Unabhängig von Gemeinden kann jede Ebene eigene Wahlkreise schneiden: `/civ <Ebene> wahlkreis anlegen <Name>`, dann mit dem Grundstücksstab zwei Ecken markieren und `/civ <Ebene> wahlkreis gebiet <Name>` (beliebig viele Rechtecke) oder ganze Gemeinden mit `/civ <Ebene> wahlkreis gemeinde <Name> <Gemeinde>` (Namen mit Leerzeichen in Anführungszeichen) zuordnen. `wahlkreis liste`, `leeren <Name>`, `aufloesen <Name>`. Spieler zählen im Wahlkreis ihres Wohngrundstücks, Dorfbewohner dort, wo sie zuletzt gesehen wurden; wer in keinem Wahlkreis liegt, zählt in seiner Gemeinde. Bei `districts` und `mixed` gibt jeder Wahlkreis seinen Sieger, Ergebnisse und die Wahlkarte zeigen die Kreise.

### Wahldebatten und Live-Auszählung
- **Debatte:** am letzten Wahltag startet für jede Wahl mit mindestens zwei Kandidaten eine Debatte (`election_debates`), oder sofort mit `/civ <Ebene> debatte starten [Amt]`. Kandidaten sprechen mit `/civ debatte <Text>`; Dorfbewohner-Kandidaten antworten nach ihrem Programm. Jeder Beitrag wirkt wie eine halbe Rede auf alle Dorfbewohner der Ebene; am Ende gibt es eine Blitzumfrage.
- **Live-Auszählung:** Nach Wahlschluss wird nicht sofort verkündet: Zwischenstände bei 10, 25, 45, 65, 85 und 100 % mit Hochrechnung und Sitzprognose, als Chat-Meldung und Bossleiste, dann das amtliche Ergebnis. Abschaltbar in der Konfiguration (`live_count`, `live_count_projections`).

### Justiz
Jede Gemeinde und jeder Bund hat ein Gericht; Richter ist, wen die Spitze ernennt (`/civ <Ebene> gericht richter ernennen <Spieler|dorfbewohner>`), sonst die Spitze selbst. Staatsanwälte ebenso mit `staatsanwalt ernennen`.

- **Klagen:** `/civ gericht klage straf <Beklagter> | <Vorwurf>`, `… zivil <Beklagter> <Smaragde> | <Grund>`, `… verfassung <§n oder Regel> | <Begründung>` beim Gemeindegericht, bei Bünden mit `/civ <Ebene> gericht klage …`. Die Gerichtsgebühr (`court_fee`) zahlt der Kläger; gewinnt er, erstattet sie der Beklagte.
- **Ablauf:** eingereicht → Beweisaufnahme → Plädoyers → Beratung → Urteil → nach zwei Tagen Berufungsfrist rechtskräftig und vollstreckt. Versäumt der Richter eine Frist, schaltet das Gericht am Tagesende selbst weiter und entscheidet nach Aktenlage.
- **Beteiligte:** Strafprozesse bekommen eine Staatsanwaltschaft und Geschworene (`jury_size`, aus Spielern und Dorfbewohnern der Gemeinde; Dorfbewohner urteilen nach Beweislage und Persönlichkeit). Mit Jury entscheidet ihre Mehrheit über schuldig, der Richter nur über die Strafe. Der Angeklagte bestellt einen Verteidiger (`/civ gericht verteidiger <Nr> <Spieler|dorfbewohner>`), Parteien laden Zeugen (`zeuge <Nr> …`); Dorfbewohner sagen sofort aus.
- **Beweise:** `/civ gericht beweis <Nr>` legt vor, was du in der Hand hast (Buch mit Feder, signiertes Buch, beliebiger Gegenstand), `beweis <Nr> vertrag <Vertragsnr>`, `beweis <Nr> artikel <Artikelnr>`, `beweis <Nr> text <Text>`. Verträge wiegen am schwersten.
- **Aussagen und Plädoyers:** `/civ gericht aussage <Nr> <Text>`, `plaedoyer <Nr> <Text>`; der Ton (be- oder entlastend) wird erkannt. Aussagen und Beweise gehen vom Einreichen bis zum Ende der Plädoyers, auch von Zeugen; Plädoyers ab der Eröffnung.
- **Richter:** `eroeffnen <Nr>`, `weiter <Nr>`, `einstellen <Nr> [Grund]`, `urteil <Nr> schuldig|freispruch [Smaragde] [verbannen] [absetzen] | Begründung`. Strafen: Geldstrafe bis `max_fine`, Schadensersatz bis zur Klagesumme, Verbannung für 30 Tage (`exile_allowed`; man fliegt aus der Gemeinde und darf nicht wieder beitreten), Amtsenthebung. Eine erfolgreiche Verfassungsklage hebt die Regel bzw. den Paragraphen auf.
- **Geschworene:** `/civ gericht stimme <Nr> schuldig|unschuldig` während Plädoyers und Beratung. Spricht der Richter früh ein Urteil, stimmen die Dorfbewohner-Geschworenen vorher ab.
- **Berufung:** `/civ gericht berufung <Nr>` durch die unterlegene Seite (auch Verteidigung bzw. Staatsanwaltschaft), solange das Urteil nicht rechtskräftig ist; das Gericht der nächsthöheren Ebene verhandelt neu, die Beweise wandern mit. Ohne höhere Ebene verhandelt dasselbe Gericht neu, aber nie mit dem Richter der Vorinstanz.
- **Menüs:** `/civ gericht` (Gericht der eigenen Gemeinde), `/civ gericht fall <Nr>` (Akte mit allen Beteiligten, Beweisen, Protokoll und den Knöpfen, die dir offenstehen), `/civ gericht faelle`, `/civ gericht akte <Nr>` (in den Chat). Im Rathaus oben (Glocke) und im Ebenenmenü.
- **Richterbank (Block):** Rezept Gold, Buch, Gold über drei Bretter über Brett-leer-Brett. Rechtsklick öffnet das Gericht der Gemeinde, in der sie steht.

### Flaggen und Hymnen
- **Flagge:** Banner gestalten (Webstuhl), in die Hand nehmen und `/civ <Ebene> flagge setzen`. `flagge holen` gibt ein Banner mit der Flagge, `flagge entfernen`. Im Ebenenmenü (Banner): Klick holt die Flagge. Die Webseite zeichnet die Flaggen nach.
- **Hymne:** `/civ <Ebene> hymne setzen <Titel> | [Instrument |] <Noten>`. Noten von F#3 bis F#5 (`C4 D4 E4 F#4 …`), `*2` verdoppelt, `/2` halbiert die Länge, `-` ist eine Pause. Instrumente: `harp`, `bell`, `flute`, `chime`, `guitar`, `xylophone` und alle anderen Notenblock-Instrumente. `hymne spielen`, `hymne entfernen`; Rechtsklick aufs Banner im Ebenenmenü spielt sie. Mit `anthem_on_enter` hört man sie beim Betreten des Gebiets.

### Wahlkampf
- **Reden:** `/civ rede <Text>`: Spieler in 32 Blöcken lesen mit. Kandidierst du gerade, reagieren Dorfbewohner in Hörweite drei Tage lang: Das Thema (Ordnung, Freiheit, Steuern, Ausbau; aus Stichworten erkannt) zieht passende Persönlichkeiten an und stößt andere ab. Eine wirksame Rede pro Tag.
- **Wahlversprechen:** `/civ versprechen geben <Text>` als Kandidat; das Gesetzbuch erkennt Messbares wie „Die Umsatzsteuer beträgt 5 %.“ `/civ versprechen [liste [Name]]` zeigt alle Versprechen mit Bilanz (gehalten, gebrochen, teilweise, nicht messbar). Wer wieder antritt, wird von Dorfbewohnern an seiner Bilanz gemessen. Kandidaten-Tooltips in Ämter-, Parlaments- und Urnenmenü zeigen die Versprechen.
- **Wahlplakate (Litfaßsäule):** Aufstellen im Gemeindegebiet wirbt für dich, wenn du kandidierst, sonst für deine Partei: Jedes Plakat zieht die Dorfbewohner der Gemeinde etwas zum Beworbenen (bis zu drei zählen). Rechtsklick zeigt, wofür es wirbt; über der Säule schwebt ein Schild mit dem Namen. Wer noch nicht kandidiert und keine Partei hat, dessen Plakat wartet und wird aktiv, sobald er kandidiert.
- `/civ parlament [zeigen <Name> | menue]` zeigt Sitzverteilung, Fraktionen, Präsidium, Wahlperiode und Tagesordnung.

### Gesetzes-Baukasten
Alle Regelungen stehen im Rechtsmenü (Gemeindehaus → Recht) als Schalter oder Zahl, gelten nach Bundesrecht-Vorrang und lassen sich auch als Freitext-Paragraph erlassen. Neu als Spielregeln im Gebiet:

| Regel | Wirkung |
|---|---|
| `pvp_allowed` (Standard ja) | Spieler können einander im Gebiet nicht verletzen, wenn nein („PvP ist verboten.“) |
| `hostile_mobs_allowed` (Standard ja) | Feindliche Monster verschwinden im Gebiet alle 5 Sekunden, wenn nein; benannte bleiben („Keine Monster in der Stadt.“) |
| `outsiders_may_hurt_animals` (Standard nein) | Fremde können Tiere nicht verletzen („Tiere sind geschützt.“) |
| `federal_levy_percent` (0–10, nur im Bund) | Bundesumlage: jedes Glied zahlt täglich diesen Anteil seiner Kasse an den Bund („Die Bundesumlage beträgt 5 %.“) |

### Schutz
Im Gemeindegebiet gilt Folgendes, solange das Recht nichts anderes erlaubt:
- Explosionen und Feuer zerstören keine Blöcke.
- Kolben bewegen oder zerstören keine Blöcke über Gebietsgrenzen hinweg.
- Endermen nehmen keine Blöcke auf.
- Fremde dürfen nicht bauen, Türen benutzen oder Behälter öffnen.
- Fremde dürfen Rüstungsständer, Rahmen und Bilder nicht verändern und Tiere sowie Dorfbewohner nicht angreifen. Handeln mit Dorfbewohnern bleibt erlaubt.

### Straßen und Adressen
- Bürgermeister oder Bauamt markieren eine Straße mit dem Grundstücksstab (zwei Ecken) und legen sie mit `/civ strasse anlegen <Name>` an; `aufheben <Name>` und `liste` gibt es auch.
- Jede Gemeinde hat eine feste Postleitzahl. Grundstücke bis 12 Blöcke neben einer Straße bekommen Hausnummern: ungerade auf der einen, gerade auf der anderen Seite, aufsteigend entlang der Straße.
- `/civ adresse [Spieler]` zeigt die Anschrift (Wohngrundstück, sonst gemietetes, sonst eigenes Grundstück); `/civ adressen` listet die Straßen der Gemeinde. Dorfbewohner nennen ihre Anschrift beim Anklicken.

### Verträge
`/civ vertrag` (oder Gemeindehaus → Verträge, oder die Webseite) verwaltet alle Verträge. Ein Angebot gilt, sobald die Gegenseite unterschreibt; für eine Gemeinde unterschreibt der Bürgermeister.

| Art | Anbieten | Wirkung |
|---|---|---|
| Miete | `/civ vertrag miete <Spieler> <Preis/Tag> [Kaution] [Tage]` auf dem eigenen oder (als Bürgermeister/Bauamt) einem Gemeindegrundstück | Kaution beim Unterschreiben, Miete täglich. Der Mieter darf das Grundstück nutzen wie der Eigentümer. Kaution zurück bei Ablauf oder Kündigung |
| Arbeit | `/civ vertrag arbeit <Spieler> <Lohn/Tag> [Tage]` oder `arbeit dorfbewohner <Lohn>` (nächster Dorfbewohner) | Lohn täglich. Dorfbewohner arbeiten ab 0,25 Smaragden am Tag; ihr Anteil am Gemeindeeinkommen geht dann an den Arbeitgeber |
| Frei | `/civ vertrag frei <Spieler> <einmalig> <täglich> <Titel> \| <Text>` | eigene Vereinbarung, der Anbieter zahlt |
| Bündnis | `/civ vertrag buendnis <Gemeinde oder Bund>` (Bürgermeister) | Bürger der Verbündeten dürfen im Gebiet bauen wie Mitglieder |

`liste`, `zeigen <Nr>`, `annehmen <Nr>`, `ablehnen <Nr>`, `kuendigen <Nr>` (endet am nächsten Tag). Kann eine Rate nicht gezahlt werden, endet der Vertrag als Vertragsbruch und die Kaution verfällt. Verliert der Vermieter das Grundstück, wird der Mietvertrag gegenstandslos.

Gesetz `office_salary` („Das Gehalt beträgt 2 Smaragde.“): Bürgermeister, Amtsträger und Abgeordnete bekommen es täglich aus der Gemeindekasse.

### Post und Briefkasten (Block)
Rezept: Eisen, Papier, Truhe (`III / IPI / ICI`).

- Briefe kommen am nächsten Spieltag an. Schreiben: `/civ brief <Spieler> <Betreff> | <Text>`, am Briefkasten mit einem Buch mit Feder in der Hand (erste Zeile = Betreff) und Klick auf einen Freund, oder auf der Webseite.
- `/civ post` zeigt das Postfach, `/civ post lesen <Nr>` einen Brief. Wer online ist, bekommt bei Ankunft eine Nachricht.
- **Briefwahl:** Erlaubt das Gesetz `postal_vote_allowed` („Briefwahl ist erlaubt.“), wählt man am Briefkasten; die Stimme zählt wie an der Urne.

### Freunde und Chat
- `/civ freund <Spieler>` fragt an; die Anfrage kommt mit Knöpfen [Annehmen] [Ablehnen], alternativ `/civ freund annehmen|ablehnen <Spieler>`. `/civ freund entfernen <Spieler>`, `/civ freunde` (Menü: Klick schreibt eine Nachricht, Rechtsklick schaltet Vertrauen, unten rechts neue Freunde aus allen Online-Spielern).
- **Vertrauen:** `/civ freund vertrauen|misstrauen <Spieler>`. Wem du vertraust, der darf auf deinen Grundstücken (eigene und gemietete) bauen, Behälter öffnen und Dinge benutzen.
- `/civ nachricht <Freund> <Text>` schickt eine Direktnachricht, auch an Freunde, die gerade auf der Webseite sind.
- Der allgemeine Chat im Spiel erscheint auf der Webseite; was dort geschrieben wird, erscheint im Spiel mit „[Web]“.

### Firmen und Börse
- `/civ firma gruenden <Kapital> <Name>`: Das Kapital (mindestens 10 Smaragde) geht vom Gründer in die Firmenkasse. Die Firma hat 1.000 Aktien, die alle dem Gründer gehören; Ausgabepreis = Kapital / 1.000. Der Gründer hat die Leitung und handelt für die Firma.
- Firmen verdienen über eigene Läden (`/civ laden eroeffnen <Preis> <Firma>`, Einnahmen in die Firmenkasse), Aufträge (freie Verträge mit der Firma) und angestellte Dorfbewohner (`/civ firma einstellen <Firma> dorfbewohner <Lohn>`: ihr Anteil am Gemeindeeinkommen geht an die Firma).
- `dividende <Firma> <Betrag>` schüttet aus der Kasse an alle Aktionäre aus, je Aktie gleich viel. `ausgeben <Firma> <Anzahl> <Preis>` gibt neue Aktien aus und bietet sie an; der Erlös geht in die Kasse. `aufloesen <Firma>` verteilt die Kasse anteilig an die Aktionäre.
- **Börse:** `/civ boerse` zeigt alle Kurse, `kaufen|verkaufen <Firma> <Anzahl> [Limit]` gibt eine Limit-Order auf (ohne Limit bis 10 % über bzw. unter dem letzten Kurs). Orders werden sofort gegen das Orderbuch ausgeführt, beste Preise zuerst, zum Preis der wartenden Order; der Rest wartet bis zu 7 Tage. `depot`, `buch <Firma>`, `streichen <Nr>`. Menü: Gemeindehaus → Börse oder Block **Börsenterminal** (Rezept `GRG / IEI / III`).
- **Produktion:** Jede Firma wählt eine Branche: `/civ firma branche <Firma> forst|schaeferei|acker|bergwerk|fischerei|viehzucht|ziegelei|obstbau`. Jeden Abend erzeugt sie Waren (Holz, Wolle, Weizen, Bruchstein/Kohle/Eisenerz, Fisch, Rindfleisch/Leder/Eier, Ziegel/Glas, Äpfel/Honig).
  - **Arbeitskräfte:** die Leitung und Mitglieder je ½, Angestellte mit Arbeitsvertrag (Spieler oder Dorfbewohner) je 1. Mehr Leute bringen mehr, mit abnehmendem Grenzertrag. Einstellen: `/civ firma einstellen <Firma> <Spieler|dorfbewohner> <Lohn je Tag>` (Dorfbewohner ab 0,25 Smaragden), kündigen: `/civ firma kuendigen <Firma> <Spieler|dorfbewohner>` (endet nach einem Tag).
  - **Investieren:** `/civ firma investieren <Firma> <Betrag>` aus der Firmenkasse in die Ausstattung. Stufe 1 kostet 20 Smaragde, jede weitere doppelt so viel wie die vorige (20, 60, 140, 300 … insgesamt); jede Stufe bringt +25 % Ertrag, höchstens Stufe 8. Ein Branchenwechsel halbiert die Ausstattung.
  - **Verkauf:** `/civ firma verkauf <Firma> automatisch` (Standard) verkauft die Tageserzeugung abends an den Servermarkt, der Erlös geht in die Firmenkasse; `hand` behält sie im Lager. `/civ firma lager <Firma>`, `/civ firma abholen <Firma> <Ware> [Anzahl]` (ins Inventar), `/civ firma verkauf <Firma> jetzt`.
  - **Menü:** `/civ firma menue <Firma>` oder über die Börse: Branche wählen, investieren (Klick 10, Shift 100 Smaragde), Verkauf umschalten, Lager abholen, Belegschaft (Shift-Klick kündigt), einstellen. Die Leitung bekommt jeden Abend eine Meldung mit Stückzahl und Erlös; die Webseite zeigt den Betrieb unter Börse.
- **Dorfbewohner investieren:** Sie sparen ein Zehntel ihres Anteils am Gemeindeeinkommen (bis 50 Smaragde). Sparsame kaufen Aktien mit gutem Verhältnis aus Buchwert, Dividende und Kurs; Ordnungsliebende meiden schwankende Kurse, Freiheitsliebende mögen steigende; Ausgabefreudige verkaufen nach und nach.

### Presse
- `/civ zeitung gruenden <Name>`; `redakteur <Zeitung> <Spieler|dorfbewohner>` nimmt in die Redaktion auf. Die Zeitung erscheint in den Gemeinden ihrer Redaktion.
- Schreiben: `/civ zeitung schreiben <Zeitung> <Titel> | <Text>`, mit Buch und Feder in der Hand `veroeffentlichen <Zeitung>` (erste Zeile = Titel) oder auf der Webseite. Höchstens 5 Artikel je Zeitung und Tag.
- Der Mod erkennt **Gegenstand** (häufigster bekannter Name: Spieler, Partei, Gemeinde, Firma), **Haltung** (scharf kritisch bis lobend, aus lobenden und kritischen Wörtern) und **Thema** (Framing: Ordnung, Freiheit, Steuern, Ausbau).
- **Wirkung:** Dorfbewohner der Erscheinungsgemeinden lesen einen Artikel mit 15–65 % Wahrscheinlichkeit (je nach Zugehörigkeitsgefühl). Drei Tage lang verschiebt er ihre Wahl (Kandidat ±0,08 × Haltung, Partei ±0,05, zusammen höchstens ±0,3) und, bei Berichten über die Gemeinde oder ihre Führung, ihre Zufriedenheit (neuer Faktor „Presse“, höchstens ±6). Passt das Thema zur Persönlichkeit, wirkt er bis 50 % stärker.
- **Dorfbewohner-Redakteure** schreiben etwa jeden dritten Tag aus Vorlagen: über Kandidaten laufender Wahlen (Haltung nach Programm und eigener Persönlichkeit) oder die Stimmung in der Gemeinde.
- Lesen: `/civ zeitung lesen [Zeitung]` gibt die aktuelle Ausgabe als Buch, `artikel <Nr>` zeigt einen Artikel. Block **Zeitungskiosk** (Rezept `PPP / BGB / WWW`) zeigt die neuesten Artikel. Neue Artikel erscheinen als Schlagzeile im Chat der Gemeinde.

### Bank (Block)
Rezept: Gold, Eisen, Smaragd, Steinziegel (`GIG / IEI / SSS`).

Per Rechtsklick zahlst du Smaragde ein oder hebst sie ab. Das Konto rechnet in Hundertsteln: 1 Smaragd sind 100 Einheiten, deshalb sind auch Beträge wie `0,25` möglich.

### Markt (Block)
Rezept: weiße und rote Wolle, Bretter, Smaragd, Truhe (`WRW / PEP / PCP`).

- **Verkaufen:** Klick auf einen Stapel im eigenen Inventar verkauft den ganzen Stapel. Ein Rechtsklick verkauft nur ein Stück.
- **Kaufen:** Oben liegt das Lager. Ein Klick kauft 1, ein Shift-Klick 16.

So entstehen die Preise:
- Jedes Item hat einen Grundpreis.
- Je mehr davon auf Lager ist, desto billiger wird es.
- Das Lager schrumpft jeden Tag um 5 %.
- Ankauf und Verkauf liegen 10 % auseinander.
- Das Preisniveau folgt der Geldmenge je Spieler (Inflation) und ist auf 50 % bis 400 % begrenzt.
- Verzauberte, umbenannte oder beschädigte Items kauft der Markt nicht an.

### Webseite
Der Server stellt eine Webseite bereit, standardmäßig auf Port 8765, hell und dunkel, auch auf dem Handy:
- **Start:** Live-Karte aller Gemeinden, Kennzahlen, wer gerade online ist, Einstieg und wichtige Befehle
- **Welt:** Register aller Gemeinden (mit generiertem Wappen, Postleitzahl, Straßen und Adressen), Bünde, Parteien, Firmen und Zeitungen mit Suche
- **Karte:** alle beanspruchten Chunks je Dimension mit Straßen, Wahlkarte nach Bezirken; mit `world_map` darunter die echte Landschaft: Der Mod zeichnet geladene Chunks um die Spieler (Umkreis `world_map_radius_chunks`) alle paar Minuten neu in Kacheln (ähnlich Dynmap)
- **Staat:** Live-Auszählung mit Hochrechnung, laufende Debatten, Flaggen, Hymnen zum Abspielen und Wahlkreise
- **Justiz:** alle Verfahren mit Akte und Protokoll, Klage einreichen, Aussagen, Plädoyers, Beweise, Urteile und Abstimmen als Geschworener
- **Wahlen** mit Umfragen und Ergebnissen, **Markt** mit Preisen und Läden der Spieler
- **Börse:** Kursliste mit Verlauf, Chart mit Umsatz, Kennzahlen, Aktionäre, Orderbuch, Handeln und Depot
- **Presse:** Artikel mit Haltung und Thema, Zeitungen mit Redaktion und Verbreitung, Artikel schreiben mit Live-Erkennung
- **Post:** Postfach, gesendete Briefe, Briefe schreiben und beantworten
- **Chat:** allgemeiner Chat (mit dem Spiel verbunden) und Direktnachrichten an Freunde
- **Freunde:** Anfragen senden, annehmen, ablehnen; wer online ist
- **Mein Bereich:** Kontostand, Anschrift, Überweisen, Verträge unterschreiben, anbieten und kündigen, Ämter, Wahlen und Abstimmungen

Anmelden: Mit `/civ web` holst du dir im Spiel einen Code und gibst ihn auf der Webseite ein. Sitzungen verfallen nach zwei Tagen ohne Nutzung oder nach sieben Tagen; `/civ web abmelden` meldet dich überall ab.

### Englische Befehle und Handbuch
- Jeder Befehl geht auch auf Englisch: `/civ court case 3`, `/civ company invest "Holz AG" 20`, `/civ election vote buergermeister Alex`, `/civ town court sue …`. Werte hinter den Befehlen (Ämter wie `buergermeister`, Branchen, `schuldig` usw.) bleiben deutsch.
- Neue Spieler bekommen beim ersten Betreten das **Civ-Handbuch** (in ihrer Spielsprache). Nachschlag: `/civ handbuch`.

### Befehle
| Befehl | Wirkung |
|---|---|
| `/civ hilfe` | Überblick |
| `/civ info` | Chunk, Gemeinde, Kontostand, Tag |
| `/civ menue` | Menü der eigenen Gemeinde |
| `/civ karte` | Chunk-Karte; Linksklick beansprucht Kerngebiet, Rechtsklick einen Außenposten |
| `/civ grenzen` | Gebietsgrenzen 10 Sekunden lang als Partikel |
| `/civ beitreten <Name>` | einer Gemeinde beitreten |
| `/civ austreten` | aus der eigenen Gemeinde austreten |
| `/civ liste` | alle Gemeinden |
| `/civ gemeinde [Name]` | Steckbrief einer Gemeinde |
| `/civ beanspruchen [aussenposten]` | den aktuellen Chunk beanspruchen |
| `/civ freigeben` | den aktuellen Chunk aufgeben |
| `/civ recht` | Regelungen der eigenen Gemeinde anzeigen |
| `/civ recht setzen <Regel> <Wert>` | eine Regelung ändern |
| `/civ konto` | Kontostand |
| `/civ einzahlen [Anzahl]` | Smaragde einzahlen |
| `/civ abheben <Anzahl>` | Smaragde abheben |
| `/civ ueberweisen <Spieler\|Gemeinde> <Betrag>` | Geld überweisen, z. B. `2,5` |
| `/civ kasse einzahlen <Betrag>` | in die Gemeindekasse einzahlen |
| `/civ kasse entnehmen <Betrag>` | aus der Gemeindekasse entnehmen (nur Bürgermeister) |
| `/civ preis` | Preise des Items in der Hand |
| `/civ verkaufen [Anzahl]` | Item in der Hand verkaufen |
| `/civ kaufen <Anzahl> <Item>` | vom Markt kaufen |
| `/civ grundstueck [info]` | Grundstück unter dir, Umriss als Partikel |
| `/civ grundstueck ecke1` / `ecke2` | Ecken einer Auswahl setzen |
| `/civ grundstueck erstellen <Art>` | Auswahl zum Grundstück machen (Bürgermeister, Bauamt) |
| `/civ grundstueck chunk <Art>` | ganzen Chunk zum Grundstück machen (Bürgermeister, Bauamt) |
| `/civ grundstueck anbieten <Preis>` | Grundstück zum Verkauf anbieten |
| `/civ grundstueck zurueckziehen` | Verkaufsangebot zurückziehen |
| `/civ grundstueck kaufen` | angebotenes Grundstück kaufen |
| `/civ grundstueck vergeben <Spieler>` | Gemeindeland verschenken (Bürgermeister, Bauamt) |
| `/civ grundstueck aufloesen` | Grundstück der Gemeinde auflösen (Bürgermeister, Bauamt) |
| `/civ laden eroeffnen <Preis>` | Kiste im Blick wird Laden für das Item in der Hand |
| `/civ laden schliessen` | Laden an der Kiste im Blick schließen |
| `/civ laden liste` | eigene Läden |
| `/civ web` | Anmeldecode für die Webseite |
| `/civ abstimmungen` | offene Abstimmungen |
| `/civ abstimmen <Nr> ja\|nein` | abstimmen |
| `/civ aemter` | Ämter, Inhaber und laufende Wahlen |
| `/civ stimmung` | Stimmung der Dorfbewohner |
| `/civ wahl ausrufen <Amt>` | Wahl ausrufen (`buergermeister`, `schatzmeister`, `bauamt`) |
| `/civ wahl kandidieren <Amt> [Programm]` | kandidieren, optional mit Programm |
| `/civ wahl stimmen <Amt> <Spieler>` | wählen |
| `/civ amt ernennen <Amt> <Spieler>` | Schatzmeister oder Bauamt ernennen (Bürgermeister) |
| `/civ amt entlassen <Amt>` | Amt frei machen (Bürgermeister) |
| `/civ bund gruenden <Ebene> <Name>` | Bund über der eigenen Gemeinde (oder dem eigenen Bund) gründen |
| `/civ bund einladen <Name>` / `beitreten <Bund>` | Gemeinde einladen / Einladung annehmen |
| `/civ bund austreten` / `aufloesen` | Bund verlassen / auflösen |
| `/civ bund info [Name]` | Bünde über der eigenen Gemeinde oder ein bestimmter Bund |
| `/civ ebenen` | Eigene Gemeinde, Land und Nation mit Spitze |
| `/civ land\|nation <Befehl>` | `info`, `menue`, `aemter`, `wahl …`, `amt …`, `parlament`, `recht [setzen]`, `abstimmungen`, `abstimmen`, `verfassung`, `gesetze`, `umfrage`, `wahlergebnis` für diese Ebene |
| `/civ ebene <Ebene> <Befehl>` | Dasselbe für jede Ebene, auch eigene |
| `/civ verfassung` / `verfassung zeigen <Name>` | Verfassung der eigenen Gemeinde / einer Gemeinde oder eines Bundes |
| `/civ verfassung menue` | Verfassungsmenü der eigenen Gemeinde |
| `/civ verfassung praeambel <Text>` | Präambel des Entwurfs (Spitze, höchstens 600 Zeichen) |
| `/civ verfassung ausrufen [Name]` | Verfassung ausrufen (Spitze, nicht umkehrbar) |
| `/civ verfassung artikel <Regel> <Wert>` | Verfassungsartikel setzen (Entwurf) bzw. Änderung beantragen (ausgerufen) |
| `/civ gesetz [liste [Name]]` / `gesetz menue` | Paragraphen der eigenen Gemeinde bzw. einer Gemeinde oder eines Bundes / Gesetzbuch-Menü |
| `/civ gesetz zeigen <Nr>` / `pruefen <Text>` | einen Paragraphen lesen / Erkennung testen |
| `/civ gesetz erlassen <Titel> \| <Text>` | Paragraph erlassen oder beantragen (ohne `\|`: Text aus Buch und Feder in der Hand) |
| `/civ gesetz aufheben <Nr>` | Paragraph aufheben oder Aufhebung beantragen |
| `/civ partei [liste \| menue \| info [Name]]` | Parteien anzeigen |
| `/civ partei gruenden <Richtung> <Name>` / `beitreten <Name>` / `austreten` | Partei gründen, beitreten, verlassen |
| `/civ partei richtung <Ordnung> <Wirtschaft>` / `wappen` / `aufloesen` | Richtung, Logo (Item in der Hand), Auflösung (Vorsitz) |
| `/civ parlament [zeigen <Name> \| menue]` | Parlament anzeigen; Wahl über `/civ wahl … parlament` |
| `/civ rede <Text>` | Wahlkampfrede an Spieler und Dorfbewohner in Hörweite |
| `/civ versprechen geben <Text>` / `versprechen [liste [Name]]` | Wahlversprechen geben bzw. mit Bilanz anzeigen |
| `/civ umfrage [Name]` / `/civ wahlergebnis [Name \| menue]` | Umfrage unter Dorfbewohnern, letztes Wahlergebnis je Bezirk |
| `/civ strasse anlegen\|aufheben <Name>` / `strasse liste` | Straßen der Gemeinde (Bürgermeister, Bauamt) |
| `/civ adresse [Spieler]` / `/civ adressen` | Anschrift, Straßen und Hausnummern |
| `/civ vertrag …` / `vertrag menue` | Verträge, siehe oben |
| `/civ post [lesen <Nr> \| menue]` / `/civ brief <Spieler> <Betreff> \| <Text>` | Post |
| `/civ freund [entfernen] <Spieler>` / `/civ freunde [menue]` | Freunde |
| `/civ nachricht <Freund> <Text>` | Direktnachricht |
| `/civ gericht [faelle\|fall\|akte\|klage\|beweis\|aussage\|plaedoyer\|stimme\|urteil\|berufung …]` | Justiz, siehe oben |
| `/civ <Ebene> wahlkreis\|debatte\|flagge\|hymne\|gericht …` | Wahlkreise, Debatten, Hoheitszeichen und Gericht einer Ebene |
| `/civ firma gruenden\|info\|liste\|aufnehmen\|entlassen\|einstellen\|dividende\|ausgeben\|aufloesen` | Firmen, siehe oben |
| `/civ boerse [kaufen\|verkaufen\|depot\|buch\|streichen\|menue]` | Börse |
| `/civ zeitung gruenden\|redakteur\|entlassen\|schreiben\|veroeffentlichen\|lesen\|artikel\|liste\|menue\|aufloesen` | Presse |

Nur für Admins:
- `/civ gruenden <Name>` gründet eine Gemeinde ohne Gemeindehaus.
- `/civ admin umgehen` schaltet den Gebietsschutz für dich selbst ab und wieder an.
- `/civ admin aufloesen <Name>` löst eine Gemeinde oder einen Bund auf.
- `/civ admin ebenen` zeigt alle Ebenen mit Rang und Anzahl.
- `/civ admin tag` beendet den laufenden Tag sofort: Grundsteuer, Einnahmen der Dorfbewohner, fällige Abstimmungen und Wahlen werden abgerechnet. Praktisch zum Testen.

### Recht
Der Bürgermeister (oder mit Abstimmungen die Bürger) stellt diese Regelungen im Menü **Recht** des Gemeindehauses ein:

| Regelung | Bedeutung |
|---|---|
| Grundsteuer pro Block und Tag | in Hundertstel Smaragd |
| Steuerfreie Blöcke je Inhaber | Freibetrag auf die Gesamtfläche eines Inhabers |
| Steuerfreie Tage nach Erwerb | Schonfrist nach dem Kauf eines Grundstücks |
| Botschaften zahlen Grundsteuer | ja/nein |
| Höchstzahl an Außenposten | |
| Fremde dürfen bauen | ja/nein |
| Fremde dürfen benutzen | ja/nein |
| Fremde dürfen Behälter öffnen | ja/nein |
| Explosionen zerstören Blöcke | ja/nein |
| Feuer verbrennt Blöcke | ja/nein |
| Rechtsänderungen per Abstimmung | ja/nein |
| Dauer einer Abstimmung in Tagen | Standard 2 |
| Umsatzsteuer auf Ladenverkäufe in Prozent | 0 bis 50 |
| Amtszeit gewählter Ämter in Tagen (`term_days`) | 0 = unbefristet (Standard) |
| Dauer einer Wahl in Tagen (`election_days`) | Standard 2 |
| Stimmgewicht von Dorfbewohnern in Prozent (`villager_vote_percent`) | 0 = kein Wahlrecht (Standard) |
| Sitze im Parlament (`parliament_seats`) | 0 = kein Parlament (Standard), bis 99 |
| Sitzungen alle … Tage (`session_days`) | 1 bis 7, Standard 1 |
| Gewählt wird nur an der Wahlurne (`ballot_box_required`) | ja/nein, Standard nein |
| Wahlverfahren, Sperrklausel, Amtszeitbegrenzung, Wahlpflicht, Debatten | siehe **Wahlverfahren** |
| Pressefreiheit (`press_freedom`) | aus: Artikel, die die Gemeinde oder ihre Amtsträger kritisieren, erscheinen dort nicht |
| Geschworene je Strafprozess (`jury_size`) | 0–12, Standard 5 |
| Höchste Geldstrafe (`max_fine`) | Hundertstel Smaragd, Standard 5000 |
| Verbannung erlaubt (`exile_allowed`) | ja/nein, Standard ja |
| Gerichtsgebühr (`court_fee`) | Hundertstel Smaragd, Standard 100 |

## Für Server-Admins
`config/eddies-civ-mod.properties` wird beim ersten Start angelegt:

| Eintrag | Bedeutung |
|---|---|
| `ticks_per_day` | Länge eines Zivilisationstags; 24000 = ein Minecraft-Tag |
| `claim_base` | Chunks, die jede Gemeinde beanspruchen darf |
| `claim_per_member` | zusätzliche Chunks je Mitglied |
| `price_reference_emeralds_per_player` | Geldmenge je Spieler, bei der der Markt Grundpreise zahlt |
| `villager_income_hundredths_per_day` | Einnahmen je Dorfbewohner und Tag; 100 = 1 Smaragd |
| `villager_emigration` | ob sehr unzufriedene Dorfbewohner wegziehen (Standard `true`) |
| `villager_parties`, `villager_party_founders` | ob Dorfbewohner Parteien beitreten und gründen (Standard `true`) und ab wie vielen Gleichgesinnten (Standard 5) |
| `federation_levels` | eigene Ebenen als `id:Name:Rang`, durch Kommas getrennt, z. B. `kreis:Kreis:10,bund:Bund:40`; eingebaut sind `gemeinde` 0, `land` 20, `nation` 30 |
| `market_town_inhabitants`, `city_inhabitants`, `metropolis_inhabitants` | Einwohner für die Siedlungsstufen |
| `web_enabled`, `web_bind`, `web_port` | Webseite ein/aus, Adresse und Port (Standard `true`, `0.0.0.0`, `8765`) |
| `web_public_url` | Adresse, die `/civ web` den Spielern nennt, z. B. `https://civ.example.org` |
| `web_trust_proxy` | nur hinter einem Reverse-Proxy `true`: dann zählen `X-Forwarded-For`/`-Proto` (Standard `false`) |
| `live_count`, `live_count_seconds`, `live_count_projections` | Live-Auszählung nach Wahlschluss an/aus, Dauer in Sekunden, Hochrechnungen an/aus (Standard `true`, 60, `true`) |
| `debate_minutes` | Länge einer Wahldebatte (Standard 5) |
| `anthem_on_enter` | Hymne beim Betreten eines Gebiets (Standard `false`) |
| `world_map`, `world_map_radius_chunks` | gerenderte Weltkarte für die Webseite an/aus und Umkreis um Spieler (Standard `true`, 6); Kacheln unter `<Welt>/data/eddies-civ-mod/map/` |

Fehlen Einträge (nach einem Update), ergänzt der Mod sie beim Start mit Standardwerten.

Der Zustand liegt je Welt in `<Welt>/data/eddies-civ-mod/civ.sqlite` und wird im Hintergrund zusammen mit der Welt gespeichert.

### Installation
Ein JAR für Server und Client: Der Mod bringt eigene Blöcke mit, deshalb brauchen ihn alle Spieler. Dazu Fabric Loader ab 0.19.5 und Fabric API ab 0.164.0, Java 25.

### Rechte
Über die Permission-API von Fabric, also auch mit LuckPerms. Ohne Rechte-Mod gilt die Operator-Stufe.

| Knoten | Wofür | ohne Rechte-Mod |
|---|---|---|
| `eddies-civ-mod:use` | alle `/civ`-Befehle | alle Spieler |
| `eddies-civ-mod:web` | `/civ web` | alle Spieler |
| `eddies-civ-mod:found` | `/civ gruenden` ohne Gemeindehaus | Spielleiter (Stufe 2) |
| `eddies-civ-mod:admin` | `/civ admin …` | Spielleiter (Stufe 2) |

### Sicherungen
- Beim Start prüft der Mod die Datenbank (`PRAGMA quick_check`). Ist sie beschädigt, startet der Server nicht und nennt den Ordner mit den Sicherungen.
- Vor jeder Schemaänderung (nach einem Update) entsteht `backups/civ-v<alte Version>-….sqlite`, einmal je Spieltag `civ-daily-….sqlite`; von jeder Art bleiben die neuesten sieben. `/civ admin sicherung` schreibt sofort eine.
- Wiederherstellen: Server stoppen, die gewünschte Sicherung aus `<Welt>/data/eddies-civ-mod/backups/` als `civ.sqlite` in den Ordner darüber kopieren, Server starten.

### Webseite absichern
- Die Seite setzt Sicherheits-Header (CSP, kein Einbetten in fremde Seiten), prüft bei Änderungen die Herkunft, begrenzt Anfragen je Adresse (600 lesend, 120 schreibend je Minute) und Fehlversuche bei der Anmeldung (10 je Adresse, 200 insgesamt je Minute).
- Für HTTPS einen Reverse-Proxy davorsetzen und `web_bind=127.0.0.1`, `web_trust_proxy=true`, `web_public_url=https://…` eintragen. Beispiel für nginx:

```
location / {
    proxy_pass http://127.0.0.1:8765;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

## Aufbau
| Modul | Inhalt |
|---|---|
| `civ-domain` | Fachlogik ohne Abhängigkeiten |
| `civ-application` | Use Cases und Persistenzschnittstellen |
| `civ-persistence` | SQLite |
| `civ-server` | Server-Ablauf, Befehle und Menülogik, ohne Minecraft |
| `civ-fabric` | dünner Adapter zu Minecraft und Fabric |
| `civ-sim` | deterministische Simulation |
