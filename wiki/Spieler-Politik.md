[Home](Home.md) › Politik

# Politik: Ämter, Wahlen und Parteien

## Abstimmungen
Gilt das Gesetz **Rechtsänderungen per Abstimmung**, wird jede Änderung im Recht-Menü oder per Befehl zum Antrag.
- Mitglieder werden benachrichtigt. Bürger stimmen im Gemeindehaus unter **Abstimmungen** ab (Linksklick dafür, Rechtsklick dagegen) oder mit `/civ abstimmen <Nr> ja|nein`.
- Mehr als die Hälfte der Bürger zugestimmt → die Änderung gilt sofort. Mindestens die Hälfte abgelehnt → gescheitert.
- Sonst wird nach **Dauer einer Abstimmung** (Standard 2 Tage) ausgezählt; bei Gleichstand gilt der Antrag als abgelehnt.
- Dorfbewohner und Einwohner ohne Bürgerrecht stimmen nicht mit ab. Ohne Abstimmungen ändert nur der Bürgermeister das Recht.

## Ämter und Wahlen
Jede Gemeinde hat drei Ämter:

| Amt | Darf |
|---|---|
| Bürgermeister | Recht ändern (ohne Abstimmungen), Kasse verwalten, Grundstücke verwalten, Sitz verlegen, Gemeinde auflösen, Schatzmeister und Bauamt ernennen/entlassen |
| Schatzmeister | Geld aus der Gemeindekasse nehmen |
| Bauamt | Grundstücke der Gemeinde anlegen, anbieten, vergeben, auflösen |

- Solange niemand gewählt wurde, ist der dienstälteste Bürger kommissarisch Bürgermeister, anfangs der Gründer.
- **Wahl ausrufen:** im Gemeindehaus unter **Ämter und Wahlen** per Umschalt-Klick oder `/civ wahl ausrufen <Amt>`. Der Bürgermeister darf das jederzeit, andere Bürger nur, wenn das Amt frei oder die Amtszeit abgelaufen ist.
- **Kandidieren und wählen:** `/civ wahl kandidieren <Amt> [Programm]`, `/civ wahl stimmen <Amt> <Spieler>`, oder per Klick im Menü. Programme: `ordnung`, `freiheit`, `steuern`, `ausbau`, `umwelt`, `soziales`, `wirtschaft`, `bildung`, `tradition`, `fortschritt`, `frieden`, `staerke`, `beteiligung`, `landwirtschaft`, `heimat`, `ohne`. Dorfbewohner wählen das zu ihrer Persönlichkeit passende Programm. Die Stimme lässt sich bis zum Ende ändern und bleibt geheim.
- **Auszählung** am Ende von Tag *Ausruf + Wahldauer* (Standard 2 Tage). Höchste Stimmenzahl gewinnt; bei Gleichstand der Amtsinhaber, sonst wer zuerst kandidiert hat.
- **Amtszeit:** `term_days` > 0 lässt Ämter ablaufen und automatisch neu wählen; bei 0 (Standard) unbefristet.
- **Dorfbewohner wählen mit**, wenn `villager_vote_percent` > 0 ist (Gewicht in Prozent einer Bürgerstimme). Kandidieren können sie nicht.
- Wer austritt, verliert Ämter, Kandidatur und Stimme.

## Wahlverfahren
Jede Ebene stellt im Recht ein, wie gewählt wird:

| Regel | Möglichkeiten |
|---|---|
| `office_voting` (Ämter) | `plurality` relative Mehrheit · `runoff` absolute Mehrheit, sonst Stichwahl der zwei Besten |
| `parliament_voting` (Parlament) | `sainte_lague`, `dhondt`, `hare_niemeyer` (Verhältniswahl) · `majority` (Mehrheitswahl ohne Ausgleich) · `districts` (Mehrheitswahl je Wahlkreis) · `mixed` (Wahlkreissieger sicher, Rest als Ausgleich nach Sainte-Laguë) |
| `ballot_type` | `person` Personenwahl · `list` Listenwahl (Sitze gehen der Kandidatur-Reihenfolge der Partei nach) |
| `threshold_percent` | Sperrklausel 0–20 % für Verhältniswahlen |
| `term_limit` | wie oft jemand dasselbe Amt in Folge haben darf (0 = beliebig) |
| `compulsory_voting`, `voting_fine` | Wahlpflicht für Spieler; Nichtwähler zahlen eine Strafe an die Kasse |
| `election_debates` | ob am letzten Wahltag automatisch eine Debatte läuft |

## Wahlkreise
Unabhängig von Gemeinden kann jede Ebene eigene Wahlkreise schneiden: `/civ <Ebene> wahlkreis anlegen <Name>`, dann mit dem Grundstücksstab Gebiete markieren (`wahlkreis gebiet <Name>`) oder ganze Gemeinden zuordnen (`wahlkreis gemeinde <Name> <Gemeinde>`). `wahlkreis liste`, `leeren <Name>`, `aufloesen <Name>`. Spieler zählen im Wahlkreis ihres Wohngrundstücks, Dorfbewohner dort, wo sie zuletzt gesehen wurden. Bei `districts`/`mixed` gibt jeder Wahlkreis seinen Sieger.

Gebiete und Gemeinden müssen dabei nicht zusammenhängen – ein Wahlkreis kann aus beliebig verstreuten Teilen bestehen. Einzelne Zuordnungen lassen sich auch nachträglich wieder herausnehmen, ohne den ganzen Wahlkreis zu leeren: `wahlkreis gebiet-entfernen <Name>` (mit dem Grundstücksstab markiertes Rechteck) bzw. `wahlkreis gemeinde-entfernen <Name> <Gemeinde>`. So lässt sich ein bestehender Wahlkreis jederzeit neu zuschneiden.

## Parteien
Parteien sind Zusammenschlüsse von Personen (Spieler und Dorfbewohner) über alle Gemeinden hinweg; jede Person gehört höchstens einer Partei an.
- **Richtung:** zwei Achsen -100..100 – Ordnung (− Freiheit, + Ordnung) und Wirtschaft (− niedrige Steuern, + Ausbau). Die stärkere Achse ergibt das Wahlprogramm.
- **Befehle:** `/civ partei gruenden <ordnung|freiheit|steuern|ausbau|mitte> <Name>`, `beitreten <Name>`, `austreten`, `info [Name]`, `liste`, `menue`; nur der Vorsitz: `richtung <Ordnung> <Wirtschaft>`, `wappen`, `aufloesen`.
- **Dorfbewohner:** treten täglich mit etwas Glück einer passenden Partei bei und verlassen sie, wenn sie sich zu weit entfernt. Finden sich genug Gleichgesinnte ohne passende Partei (`villager_party_founders`, Standard 5), gründen sie eine. Abschaltbar mit `villager_parties=false`.

## Parlament
Gemeinden und Bünde können ein Parlament haben; `parliament_seats` (0–99, Verfassungsartikel) legt die Sitzzahl fest, 0 = kein Parlament.
- **Wahl:** automatisch am Tagesende, wenn Sitze vorgesehen sind und keins sitzt (oder die Wahlperiode um ist); per Hand `/civ wahl ausrufen parlament`.
- **Verhältniswahl:** Sitze gehen nach Sainte-Laguë an die Parteien, innerhalb einer Partei an die stimmenstärksten Kandidaten. Präsidium: stärkster Kandidat der stärksten Partei.
- **Gesetzgebung:** Sobald ein Parlament sitzt, gibt es keine Verordnungen mehr – jede Rechtsänderung wird ein Antrag, abstimmen dürfen nur Abgeordnete (je Sitz eine Stimme).
- **Sitzungen:** Das Parlament tagt alle `session_days` Tage (Standard täglich). Nur an Sitzungstagen stimmen Dorfbewohner-Abgeordnete ab, stellen Anträge und werden fällige Anträge ausgezählt; der Chat kündigt die Sitzung mit der Tagesordnung an. (Der zeitliche Ablauf von Anträgen und Tagesordnung wurde mit Issue #2 überarbeitet, siehe `docs/issue-02-parlamentssitzungen.md` sobald gemergt.)
- **Nachrücken:** Verlässt ein Abgeordneter mit Partei die Gemeinde, rückt der nächste nicht gewählte Kandidat derselben Partei nach.

## Wahlurne, Wahlbezirke und Wahlkarte
- **Wahlurne** (Block): Rechtsklick zeigt alle laufenden Wahlen am Ort; Klick wählt. Mit `ballot_box_required` zählen nur Stimmen an der Urne.
- Die letzten 10 Ergebnisse jeder Gemeinde/jedes Bundes bleiben gespeichert: `/civ wahlergebnis [Name | menue]`.
- `/civ umfrage [Name]` fragt Dorfbewohner, wen sie wählen würden; beim Parlament inklusive Sitzprognose.
- Webseite: Reiter „Wahlen“ mit Umfragen und Ergebnissen, auf der Karte die Ansicht „Wahlkarte“ (siehe auch [Map Fix, Issue #8](Spieler-Webseite.md)).

## Dorfbewohner-Politik (`villager_politics_enabled`, Standard an)
Gemeinden, Bünde und Firmen brauchen keinen Spieler mehr, um am Spielgeschehen teilzunehmen:
- **Kommissarische Regierung:** Hat eine Gemeinde keinen Bürger, der kein Dorfbewohner ist, und ist auch kein Amt gewählt, regiert der dienstälteste Dorfbewohner kommissarisch. Er ändert täglich höchstens eine Rechtsregelung, so wie er auch als Dorfbewohner-Abgeordneter im Parlament stimmen würde (nach Ordnung/Freiheit, Partei usw.).
- **Gebietswachstum:** Diese Gemeinden beanspruchen von selbst täglich einen freien Nachbar-Chunk ihres Kerngebiets, bis sie ihr Claim-Limit erreichen (`/civ grundstueck`-Chunkbefehle entsprechend).
- **Bündnisse:** Eine Einladung in einen Bund entscheidet der kommissarische Dorfbewohner-Bürgermeister selbst anhand seiner Persönlichkeit (Ordnungsliebende und Heimatverbundene treten eher bei) – kein Spielerklick nötig.
- Abschaltbar in der Server-Konfiguration mit `villager_politics_enabled=false`; dann bleiben solche Gemeinden ohne Bürgermeister stehen, wie zuvor.
- **Bewusst (noch) nicht umgesetzt** (siehe `docs/issue-01-villager-politik.md`): Dorfbewohner gründen selbst brandneue Gemeinden oder Bünde, und Dorfbewohner gründen/leiten eigene Firmen oder sind an der Börse aktiv – beides erfordert größere, hier bewusst nicht mitgerissene Folgeänderungen.

## Wahlkampf
- **Reden:** `/civ rede <Text>` – Dorfbewohner in Hörweite reagieren drei Tage lang auf das erkannte Thema. Eine wirksame Rede pro Tag.
- **Wahlversprechen:** `/civ versprechen geben <Text>`; das Gesetzbuch erkennt Messbares. `/civ versprechen [liste [Name]]` zeigt die Bilanz (gehalten, gebrochen, teilweise, nicht messbar).
- **Wahlplakate (Litfaßsäule):** werben für den Kandidaten oder seine Partei.
- **Debatte:** am letzten Wahltag (`election_debates`) oder per `/civ <Ebene> debatte starten [Amt]`; Beiträge mit `/civ debatte <Text>`.
- **Live-Auszählung:** Zwischenstände bei 10, 25, 45, 65, 85, 100 % mit Hochrechnung und Sitzprognose, abschaltbar (`live_count`, `live_count_projections`).
