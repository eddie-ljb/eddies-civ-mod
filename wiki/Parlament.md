# Parlament

Ab `parliament_seats > 0` hat eine Gemeinde (oder ein Bund) ein Parlament. Mit gewähltem Parlament stimmen nur noch
seine Abgeordneten über Anträge ab, nicht mehr alle Bürger.

## Sitzungen

Das Parlament tagt alle `session_days` Tage (Standard: jeden Tag). An einem Sitzungstag stimmen die
Dorfbewohner-Abgeordneten über die Anträge auf der **Tagesordnung** ab.

### Wie ein Antrag auf die Tagesordnung kommt

1. **Antrag stellen.** Das geht jederzeit, nicht nur an einem Sitzungstag:
   - Spieler mit Sitz: `/civ recht setzen <Regelung> <Wert>` oder per Antrags-Befehl; ihre eigene Stimme zählt sofort.
   - Dorfbewohner-Abgeordnete: stellen von sich aus gelegentlich einen Antrag (abhängig von ihrer Persönlichkeit),
     wenn gerade kein anderer Dorfbewohner-Antrag offen ist.
2. **Mindestoffenzeit.** Ein Antrag muss mindestens `parliament_motion_min_open_days` Tage offen sein (Standard: 1
   Tag), bevor die Dorfbewohner-Abgeordneten im Parlament über ihn abstimmen. So bleibt immer Zeit, dass Spieler mit
   Sitz selbst noch abstimmen können, bevor die Dorfbewohner-Mehrheit entscheidet.
3. **Tagesordnung einen Tag vorher.** Sobald feststeht, welche Anträge am nächsten Sitzungstag die Mindestoffenzeit
   erreicht haben werden, wird das **einen Tag vorher** an die Gemeinde bekanntgegeben („Tagesordnung für die morgige
   Sitzung … steht fest: *n* Anträge“). So weiß jeder vorher, worüber als Nächstes abgestimmt wird.
4. **Abstimmung am Sitzungstag.** Für jeden Antrag auf der Tagesordnung stimmen die Dorfbewohner-Abgeordneten ab
   (oder enthalten sich). Steht danach die Mehrheit fest, wird der Antrag entschieden.

Spieler mit Sitz stimmen unabhängig davon jederzeit selbst ab (`/civ abstimmen <Nr> ja|nein` oder im
Verfassungsmenü); was sie bis zur Frist (`vote_days`) nicht tun, zählt als Enthaltung.

## Recht: Ergebnis erst nach voller Beteiligung

Mit dem Gesetz **`full_turnout_required`** (Standard: aus) wird ein Ergebnis nicht mehr verkündet, sobald rein
rechnerisch eine Mehrheit feststeht, sondern erst, nachdem wirklich **alle** Stimmberechtigten abgestimmt haben (im
Parlament: alle Abgeordneten; ohne Parlament: alle Bürger). Das verhindert, dass eine knappe Mehrheit das Ergebnis
entscheidet, bevor die übrigen Mitglieder überhaupt reagieren konnten.

Damit nichts ewig offen bleibt: Die gewöhnliche Abstimmungsfrist `vote_days` zählt trotzdem aus, falls am Ende der
Frist noch nicht alle abgestimmt haben.

## Rechtsparameter auf einen Blick

| Parameter | Standard | Bedeutung |
|---|---|---|
| `parliament_seats` | 0 | Sitze im Parlament; 0 = kein Parlament |
| `session_days` | 1 | Alle wie viele Tage das Parlament tagt (1–7) |
| `parliament_motion_min_open_days` | 1 | Mindestoffenzeit eines Antrags vor der Abstimmung im Parlament (0–30 Tage) |
| `full_turnout_required` | aus | Ergebnis erst nach Stimme aller Stimmberechtigten verkünden |
| `vote_days` | 2 | Nach wie vielen Tagen eine Abstimmung ohne klare Mehrheit ausgezählt wird |

Alle Parameter werden wie gewohnt über `/civ recht setzen <Name> <Wert>` bzw. das Recht-Menü im Gemeindehaus
gesetzt; mit `laws_by_vote` oder Parlament brauchen Änderungen selbst eine Abstimmung.
