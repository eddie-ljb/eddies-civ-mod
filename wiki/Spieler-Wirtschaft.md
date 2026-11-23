[Home](Home.md) › Wirtschaft

# Wirtschaft: Bank, Markt, Firmen, Börse und Presse

## Bank (Block)
Rezept: Gold, Eisen, Smaragd, Steinziegel (`GIG / IEI / SSS`). Per Rechtsklick zahlt man Smaragde ein oder hebt sie ab. Das Konto rechnet in Hundertsteln: 1 Smaragd = 100 Einheiten, Beträge wie `0,25` sind möglich.

## Währungen, Zentralbank und Kredite

- **Eigene Währung:** `/civ waehrung erstellen <Code> <Symbol> <Kurs> <Name>` (Kurs = Wert *einer* Einheit in Smaragden, z. B. `2,5`). `/civ waehrung liste` zeigt alle, `/civ waehrung umrechnen <Betrag> <von> <nach>` rechnet über Smaragd um. Smaragd ist eingebaut und unveränderlich.
- **Zentralbank:** Zunächst entscheidet die Gründerin bzw. der Gründer, dann der ernannte Vorsitzende (`/civ waehrung vorsitz <Code> <Spieler>`) über Kurs (`kurs`), Leitzins (`leitzins <Code> <Prozent>`) und das Aussehen (`item <Code> <Item-ID>`, z. B. `minecraft:gold_ingot`).
- **Standardwährung:** Per Gesetz oder Verfassung (`/civ recht setzen default_currency <Code>`). Die Dorfbewohner des Gebiets wirtschaften in ihr; leer = Gründungswährung.
- **Bank:** Im Bank-Bildschirm wechselt der Knopf oben links die Auszahlungswährung. `/civ abheben <Anzahl> <Währung>` und `/civ einzahlen <Anzahl> <Währung>` tun dasselbe per Befehl. Bargeld einer eigenen Währung ist ein gekennzeichnetes Item; gewöhnliche Items gleicher Art nimmt die Bank nicht an.
- **Kredite:** `/civ bank gruenden` macht dich zur Bank. Kreditnehmer: `/civ kredit zins <Bank> <Währung>` (Zinsangebot), `/civ kredit nehmen <Bank> <Betrag> <Tage> [Währung]`, `/civ kredit zurueckzahlen <Kennung> <Betrag>`, `/civ kredit liste`. Jahreszins = Leitzins + Aufschlag nach **Kreditscore** (300–850, Start 600). Rückzahlung erhöht den Score, Zahlungsausfall senkt ihn. Der Zentralbank-Vorsitzende leiht Banken zum reinen Leitzins.
- **Angebote und Selbstbedienung:** Banken können Kredite anbieten (`/civ kredit anbieten <Kreditnehmer> <Betrag> <Tage> [Währung]`); der Kreditnehmer nimmt mit `/civ kredit annehmen <Kennung>` an oder lehnt mit `ablehnen` ab (sieben Tage gültig). Mit `/civ bank selbstbedienung an` darf man ohne Angebot direkt leihen (`/civ kredit nehmen …`); Standard ist aus.
- **Firmen und Gemeinden:** `als <Name>` vor dem Unterbefehl, z. B. `/civ bank als Nordbank gruenden`, `/civ kredit als Nordbank anbieten …`. Berechtigt sind Firmenleitung bzw. Bürgermeister/Schatzmeister.
- **Dorfbewohner** leihen sich bei knapper Kasse kleine Beträge bei Banken mit Selbstbedienung und zahlen zurück, sobald es geht.
- **Gesetze:** `bank_lending_allowed`, `bank_max_interest_percent`, `bank_min_credit_score`, `central_bank_max_base_rate_percent`.

## Markt (Block)
Rezept: weiße und rote Wolle, Bretter, Smaragd, Truhe (`WRW / PEP / PCP`).
- **Verkaufen:** Klick auf einen Stapel im Inventar verkauft den ganzen Stapel, Rechtsklick nur ein Stück.
- **Kaufen:** oben liegt das Lager; Klick kauft 1, Shift-Klick 16.

Preisbildung: Grundpreis je Item, je mehr Lagerbestand desto billiger, das Lager schrumpft täglich um 5 %, Ankauf/Verkauf liegen 10 % auseinander, das Preisniveau folgt der Geldmenge je Spieler (Inflation, standardmäßig 50–400 % begrenzt). Verzauberte, umbenannte oder beschädigte Items kauft der Markt nicht an.

**Für Admins:** die drei Stellschrauben dieser Inflation/Deflation lassen sich seit Issue #5 in der Server-Konfiguration anpassen, siehe [Admin-Installation-und-Konfiguration](Admin-Installation-und-Konfiguration.md) – `price_level_min`/`price_level_max` (Standard 0,5/4,0) und `price_level_damping` (Standard 0,5 = Quadratwurzel-Formel; höhere Werte lassen die Preise stärker auf die Geldmenge je Kopf reagieren).

## Firmen und Börse
- `/civ firma gruenden <Kapital> <Name>`: Kapital (mind. 10 Smaragde) geht vom Gründer in die Firmenkasse. 1.000 Aktien, anfangs alle beim Gründer; Ausgabepreis = Kapital / 1.000. Der Gründer leitet die Firma.
- Einnahmequellen: eigene Läden, Aufträge (freie Verträge), angestellte Dorfbewohner (`/civ firma einstellen <Firma> dorfbewohner <Lohn>`).
- `dividende <Firma> <Betrag>` schüttet gleichmäßig an alle Aktionäre aus. `ausgeben <Firma> <Anzahl> <Preis>` gibt neue Aktien aus. `aufloesen <Firma>` verteilt die Kasse anteilig.
- **Börse:** `/civ boerse` zeigt Kurse, `kaufen|verkaufen <Firma> <Anzahl> [Limit]` gibt eine Limit-Order auf (ohne Limit ±10 % vom letzten Kurs). Sofortige Ausführung gegen das Orderbuch, Rest wartet bis zu 7 Tage. `depot`, `buch <Firma>`, `streichen <Nr>`. Menü im Gemeindehaus oder am Block **Börsenterminal** (`GRG / IEI / III`).
- **Produktion:** `/civ firma branche <Firma> forst|schaeferei|acker|bergwerk|fischerei|viehzucht|ziegelei|obstbau|bau`. Jeden Abend erzeugt die Firma Waren.
  - **Arbeitskräfte:** Leitung/Mitglieder je ½, Angestellte (Vertrag) je 1, mit abnehmendem Grenzertrag.
  - **Investieren:** `/civ firma investieren <Firma> <Betrag>` – Stufe 1 kostet 20 Smaragde, jede weitere doppelt so viel wie die vorige, +25 % Ertrag je Stufe, höchstens Stufe 8. Branchenwechsel halbiert die Ausstattung.
  - **Verkauf:** `automatisch` (Standard, abends an den Servermarkt) oder `hand` (Lager); `/civ firma lager`, `abholen`, `verkauf jetzt`.
  - **Menü:** `/civ firma menue <Firma>` oder über die Börse.
- **Dorfbewohner investieren:** sparen ein Zehntel ihres Anteils am Gemeindeeinkommen (bis 50 Smaragde) und kaufen/verkaufen Aktien nach ihrer Persönlichkeit (Sparsamkeit, Ordnungsliebe, Freiheitsliebe, Ausgabefreude).

## Konjunktur
Jede **Wirtschaftszone** (der höchste Bund, dem eine Gemeinde angehört – Nation > Land > Gemeinde; eine Gemeinde ohne Bund ist ihre eigene Zone) durchläuft eigenständig einen Konjunkturzyklus von 24–48 Tagen mit vier Phasen: **Aufschwung** (×1,10), **Hochkonjunktur** (×1,25), **Abschwung** (×0,90) und **Rezession** (×0,70). Zonen beeinflussen sich leicht gegenseitig über einen globalen Durchschnitt (15 % Gewicht).

- `/civ konjunktur` zeigt Zone, aktuelle Phase und Effekt in Prozent der eigenen Gemeinde.
- Die Phase wirkt auf das **Dorfbewohner-Einkommen** der Gemeinde (ihre eigene Zone) und auf die **Firmenproduktion** (globaler Durchschnitt, da Firmen keiner Gemeinde zugeordnet sind).
- **Regierungsmaßnahme:** das Recht `conjuncture_stimulus_percent` (0–100, Standard 0) dämpft die Auswirkung der Konjunktur auf die eigene Zone, neutralisiert sie aber nie vollständig.
- Die Phase einer Zone ist aus ihrer Kennung fest vorherbestimmt (kein Zufall bei jedem Neustart) – derselbe Tag ergibt immer dieselbe Phase.

## Presse
- `/civ zeitung gruenden <Name>`; `redakteur <Zeitung> <Spieler|dorfbewohner>` nimmt in die Redaktion auf. Erscheint in den Gemeinden ihrer Redaktion.
- Schreiben: `/civ zeitung schreiben <Zeitung> <Titel> | <Text>`, oder mit Buch und Feder `veroeffentlichen <Zeitung>`. Höchstens 5 Artikel je Zeitung und Tag.
- Der Mod erkennt **Gegenstand** (häufigster bekannter Name), **Haltung** (kritisch bis lobend) und **Thema** (Ordnung, Freiheit, Steuern, Ausbau).
- **Wirkung:** Dorfbewohner der Erscheinungsgemeinden lesen mit 15–65 % Wahrscheinlichkeit; drei Tage lang verschiebt ein Artikel Wahlabsicht und ggf. Zufriedenheit (Faktor „Presse“, höchstens ±6). Passendes Thema zur Persönlichkeit wirkt bis 50 % stärker.
- Lesen: `/civ zeitung lesen [Zeitung]` (als Buch), `artikel <Nr>`. Block **Zeitungskiosk** (`PPP / BGB / WWW`) zeigt die neuesten Artikel.
- Gesetz `press_freedom` aus: kritische Artikel über die Gemeinde/ihre Amtsträger erscheinen dort nicht.
