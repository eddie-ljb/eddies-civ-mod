[Home](Home.md) › Justiz

# Justiz

Jede Gemeinde und jeder Bund hat ein Gericht; Richter ist, wen die Spitze ernennt (`/civ <Ebene> gericht richter ernennen <Spieler|dorfbewohner>`), sonst die Spitze selbst. Staatsanwälte ebenso (`staatsanwalt ernennen`).

## Klagen
- `/civ gericht klage straf <Beklagter> | <Vorwurf>`
- `/civ gericht klage zivil <Beklagter> <Smaragde> | <Grund>`
- `/civ gericht klage verfassung <§n oder Regel> | <Begründung>`
- Bei Bünden: `/civ <Ebene> gericht klage …`

Die Gerichtsgebühr (`court_fee`) zahlt der Kläger; gewinnt er, erstattet sie der Beklagte.

## Ablauf
Eingereicht → Beweisaufnahme → Plädoyers → Beratung → Urteil → nach zwei Tagen Berufungsfrist rechtskräftig und vollstreckt. Versäumt der Richter eine Frist, schaltet das Gericht am Tagesende selbst weiter und entscheidet nach Aktenlage.

## Beteiligte
Strafprozesse bekommen eine Staatsanwaltschaft und Geschworene (`jury_size`, aus Spielern und Dorfbewohnern; Dorfbewohner urteilen nach Beweislage und Persönlichkeit). Mit Jury entscheidet ihre Mehrheit über schuldig, der Richter nur über die Strafe. Der Angeklagte bestellt einen Verteidiger (`/civ gericht verteidiger <Nr> <Spieler|dorfbewohner>`), Parteien laden Zeugen (`zeuge <Nr> …`); Dorfbewohner sagen sofort aus.

## Beweise, Aussagen, Plädoyers
- `/civ gericht beweis <Nr>` legt vor, was man in der Hand hat; `beweis <Nr> vertrag <Vertragsnr>`, `beweis <Nr> artikel <Artikelnr>`, `beweis <Nr> text <Text>`. Verträge wiegen am schwersten.
- `/civ gericht aussage <Nr> <Text>`, `plaedoyer <Nr> <Text>` – der Ton (be-/entlastend) wird erkannt. Aussagen/Beweise laufen vom Einreichen bis Ende der Plädoyers, auch von Zeugen; Plädoyers ab der Eröffnung.

## Richter und Urteil
`eroeffnen <Nr>`, `weiter <Nr>`, `einstellen <Nr> [Grund]`, `urteil <Nr> schuldig|freispruch [Smaragde] [verbannen] [absetzen] | Begründung`. Strafen: Geldstrafe bis `max_fine`, Schadensersatz bis zur Klagesumme, Verbannung für 30 Tage (`exile_allowed`), Amtsenthebung. Eine erfolgreiche Verfassungsklage hebt die Regel bzw. den Paragraphen auf.

## Geschworene und Berufung
- `/civ gericht stimme <Nr> schuldig|unschuldig` während Plädoyers und Beratung; bei frühem Urteil stimmen Dorfbewohner-Geschworene vorher ab.
- `/civ gericht berufung <Nr>` durch die unterlegene Seite, solange das Urteil nicht rechtskräftig ist; das Gericht der nächsthöheren Ebene verhandelt neu (Beweise wandern mit). Ohne höhere Ebene verhandelt dasselbe Gericht neu, aber nie mit demselben Richter.

## Menüs und Block
- `/civ gericht` (eigenes Gericht), `/civ gericht fall <Nr>` (Akte mit Beteiligten, Beweisen, Protokoll und verfügbaren Aktionen), `/civ gericht faelle`, `/civ gericht akte <Nr>` (in den Chat). Im Rathaus oben (Glocke) und im Ebenenmenü.
- **Richterbank (Block):** Rezept Gold, Buch, Gold über drei Bretter über Brett-leer-Brett. Rechtsklick öffnet das Gericht der Gemeinde, in der sie steht.
