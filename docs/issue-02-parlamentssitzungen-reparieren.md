# Issue #2: Fix of Parliamentary Sessions

## Problem

Die Villager-Abgeordneten eines Parlaments stellten und entschieden ihre Anträge bisher in **demselben** täglichen
Durchlauf (`ParliamentSession.execute`, aufgerufen aus `VotingService.endOfDay`):

1. `table()` wählte (gewürfelt, alle drei Tage möglich) einen Dorfbewohner-Abgeordneten, der einen Antrag stellte.
2. Im selben Aufruf stimmten sofort danach alle Dorfbewohner-Abgeordneten über **jeden** offenen Antrag ab.
3. Stand danach die Mehrheit rechnerisch fest, wurde der Antrag sofort entschieden (`Ballots.settle`).

Das Ganze lief ausschließlich an Sitzungstagen (`isSessionDay`), und zwar als ein einziger synchroner Schritt. Spieler
erfuhren vom Antrag und vom Ergebnis dadurch in derselben Tagesendnachricht — sie hatten nie ein Zeitfenster, um
selbst noch über den Antrag abzustimmen, bevor die (meist zahlenmäßig überlegenen) Dorfbewohner ihn bereits
entschieden hatten.

## Lösung

Der Parlamentsbetrieb wurde in drei zeitlich getrennte Schritte aufgeteilt:

1. **Anträge gehen jederzeit ein.** `table()` läuft jetzt an jedem Tag, nicht mehr nur an Sitzungstagen. Spieler
   konnten über `ProposeMotion`/`CastVote` ohnehin schon jederzeit Anträge stellen und abstimmen — das war unverändert
   nicht Teil des Bugs.
2. **Mindestoffenzeit.** Ein neuer Rechtsparameter `parliament_motion_min_open_days` (Standard: 1 Tag, 0–30) legt
   fest, wie lange ein Antrag offen sein muss, bevor im Parlament überhaupt über ihn abgestimmt wird. Erst danach
   zählt er zur Tagesordnung einer Sitzung.
3. **Tagesordnung einen Tag vorher.** Da die Mindestoffenzeit allein vom `openedOn`-Zeitpunkt abhängt, lässt sich am
   Vortag jeder Sitzung exakt berechnen, welche Anträge am nächsten Sitzungstag stimmberechtigt sein werden. Das
   System gibt diese Tagesordnung deshalb einen Tag im Voraus bekannt (`ParliamentSession.AgendaAnnounced`, Nachricht
   `parliament_agenda_announced`), bevor am Sitzungstag selbst abgestimmt wird.
4. **Volle Beteiligung erzwingbar.** Ein zweiter neuer Rechtsparameter `full_turnout_required` (Standard: aus) sorgt
   dafür, dass ein Ergebnis nicht mehr verkündet wird, sobald die Mehrheit rechnerisch feststeht, sondern erst, wenn
   alle Stimmberechtigten (bei Parlament: alle Abgeordneten, sonst alle Bürger) tatsächlich abgestimmt haben. Die
   reguläre Abstimmungsfrist (`vote_days`) bleibt dabei als Notbremse bestehen: Stimmen nicht alle ab, zählt
   `TallyProposals` den Antrag trotzdem zum Fristende aus, damit nichts ewig offen bleibt.

## Implementierung

- `civ-domain/.../law/StandardParameters.java`: neue Parameter `PARLIAMENT_MOTION_MIN_OPEN_DAYS` und
  `FULL_TURNOUT_REQUIRED`, in `all()` aufgenommen (nicht verfassungsmäßig, analog zu `vote_days`).
- `civ-application/.../polity/Ballots.java`: `evaluate()` liefert `OPEN`, solange `full_turnout_required` gilt und
  noch nicht alle Stimmberechtigten abgestimmt haben; `atDeadline()` (von `TallyProposals` genutzt) bleibt davon
  unberührt und zählt immer fristgerecht aus.
- `civ-application/.../polity/ParliamentSession.java`: `execute(day)` ruft `table()` jetzt für jeden Tag auf (sofern
  Dorfbewohner-Abgeordnete vorhanden sind), filtert die Abstimmung am Sitzungstag über die neue `onAgenda(polity, day)`
  (Mindestoffenzeit) und gibt einen Tag vorher die Tagesordnung über den neuen Outcome-Typ `AgendaAnnounced` bekannt.
- `civ-server/.../vote/VotingService.java`: neuer `switch`-Zweig für `AgendaAnnounced` → Ankündigung an die Gemeinde.
- `civ-server/.../command/MessageKey.java` + Sprachdateien (`de_de.json`, `en_us.json`): neue Schlüssel
  `PARLIAMENT_AGENDA_ANNOUNCED`, `law.parliament_motion_min_open_days`, `law.full_turnout_required`.
- Keine Schema-Änderung nötig: Rechtsparameter werden generisch gespeichert, `openedOn` eines Antrags existierte
  bereits.

## Tests

- `civ-application/.../office/ParliamentTest.java`:
  - `villagersInParliament` angepasst: ein am selben Tag gestellter Antrag wird **nicht** mehr sofort entschieden;
    stattdessen wird am Vortag die Tagesordnung für morgen angekündigt (`AgendaAnnounced`, 1 Antrag), und erst am
    Folgetag entscheidet die Abstimmung (`Decided`, `ACCEPTED`).
  - `fullTurnoutRequired` (neu): bei `full_turnout_required=true` bleibt eine rechnerisch sichere 2-von-3-Mehrheit so
    lange `OPEN`, bis auch der dritte Abgeordnete abgestimmt hat.
  - `fullTurnoutRequiredStillHitsDeadline` (neu): ohne volle Beteiligung zählt `TallyProposals` den Antrag trotzdem
    zum Ende der `vote_days`-Frist aus, statt unbegrenzt offen zu bleiben.
- Bestehende Tests (`civ-server/.../parliament/ParliamentServiceTest.java`,
  `civ-domain/.../polity/ParliamentTest.java`, `civ-application/.../office/CampaignTest.java#sessionDays`) wurden
  geprüft und benötigten keine Anpassung, da sie Spieler-Abstimmungen direkt über `CastVote` nutzen (unverändert
  jederzeit möglich) bzw. keine Dorfbewohner-Anträge auslösen.
- Ausgeführt: `./gradlew :civ-domain:test :civ-application:test :civ-server:test`.
