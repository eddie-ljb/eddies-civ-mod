# Issue #15: Parteiprogramme

## Problem

> "Es sollen Parteiprogramme von jeder Partei und deren Parteivorsitzenden definiert werden
> können. Dabei soll auch einstellbar sein, ob die Partei und deren Mitglieder darüber abstimmen
> oder ob der Vorsitzende das alleine entscheiden kann. Das Parteiprogramm soll sich auch auf die
> Villager auswirken und deren Entscheidungen beeinflussen. Je nach Loyalität folgen die Villager
> diesem komplett oder nur stellenweise (Niedrige Loyalität)."

Parteien hatten bereits eine Richtung (`order`/`economy`, zwei Achsen -100..100) und ein Wappen
(`PartyProfile`), die der Vorsitz jederzeit allein ändern konnte (`/civ partei richtung`,
`wappen`). Zwei Dinge fehlten: (1) ein Schalter, ob diese Änderungen weiterhin allein vom
Vorsitz oder stattdessen durch eine Abstimmung der Parteimitglieder entschieden werden, und (2)
eine nach Loyalität abgestufte statt einer starren Wirkung auf das Verhalten der Mitglieder.

## Lösung

- **`ProgramMode`** (neues Enum): `CHAIR` (Vorsitz entscheidet allein und sofort, Standard, wie
  bisher) oder `VOTE` (jede Änderung wird zum Antrag, über den abgestimmt wird).
- **`PartyProfile`** trägt den Modus jetzt mit (`mode()`, `withMode(...)`); Konstruktor ohne
  Modus-Argument setzt weiterhin `CHAIR`, damit bestehende Aufrufe unverändert funktionieren.
- **`ChangePartyProgram`** (neuer Use Case, ersetzt das bisherige, nur chair-fähige
  `ChangePartyProfile`): prüft den aktuellen Modus der Partei.
  - `CHAIR`: wie bisher — nur der Vorsitz darf ändern, die Änderung gilt sofort
    (`NotOfficeHolderException` sonst).
  - `VOTE`: jedes Parteimitglied mit Bürgerrecht kann eine Änderung vorschlagen; daraus wird ein
    `Motion.PartyProgram`-Antrag (neue `Motion`-Variante), über den die Partei genau wie über
    jede andere Rechtsänderung abstimmt (`Ballots`/`ProposeMotion`/`CastVote`). Bei Annahme setzt
    `Ballots.carryOut` das neue Programm um (`Polity.changePartyProfile`).
- **Befehl** `/civ partei programmodus [abstimmung|vorsitz]`: ohne Argument zeigt er den
  aktuellen Modus, mit Argument ändert ihn der Vorsitz (läuft selbst wieder über
  `ChangePartyProgram`, damit auch der Moduswechsel bei `VOTE` zur Abstimmung werden kann).
- **Villager-Einfluss, abgestuft nach Loyalität:** `PersonalityLegislator` (Dorfbewohner als
  Abgeordnete) gewichtete das Programm der eigenen Partei bislang mit vollem Gewicht in seine
  Nutzenrechnung für Rechtsentscheidungen im Parlament ein. Das ist jetzt mit
  `Personality.loyalty()` skaliert: ein neuer, aus der bestehenden Achse *Heimatverbundenheit*
  (`belonging`, -100..100) abgeleiteter Wert zwischen 0,3 und 0,9. Hohe Loyalität → Parteilinie
  zählt fast voll, niedrige Loyalität → deutlich schwächer, der Abgeordnete folgt stärker seiner
  eigenen Persönlichkeit statt der Partei. Dieselbe Formel ersetzt eine bisher inline in
  `PersonalityVoter` dupliziert vorhandene Berechnung (keine Verhaltensänderung dort, nur
  Entduplizierung).

  **Hinweis zur Abhängigkeit von Issue #14 ("loyality"):** Issue #14 führt einen eigenen, pro
  Dorfbewohner und Ziel (Gemeinde, Partei, Parteivorsitz, Unternehmen, …) **gespeicherten**
  Loyalitätswert ein. Zum Zeitpunkt dieser PR ist #14 noch nicht auf `master` gemergt. Diese PR
  nutzt deshalb `Personality.loyalty()` als Platzhalter — eine aus der festen Persönlichkeit
  abgeleitete, nicht persistierte Annäherung, bewusst analog zur bereits bestehenden Verwendung
  derselben Formel in `PersonalityVoter`. **Sobald #14 gemerged ist, sollte
  `PersonalityLegislator` auf den dortigen, partei-spezifischen Loyalitätswert umgestellt
  werden** statt auf diese generische Persönlichkeits-Annäherung.

## Bewusst nicht umgesetzt

- Keine eigene UI-Anzeige des Programms über die reine `/civ partei info`-Zeile hinaus (zeigt
  Richtung/Programm bereits); ein grafischer Vergleich mehrerer Parteiprogramme im Menü wäre ein
  guter Folge-Schritt, war aber nicht Teil des Kernauftrags.
- Keine Abstimmungspflicht-Vererbung an Bünde von Parteien (Parteien bilden keine Bünde).

## Implementierung (Dateien)

- **`civ-domain/.../polity/ProgramMode.java`** (neu), **`PartyProfile.java`** (Modus-Feld,
  `withMode`), **`Motion.java`** (`PartyProgram`-Variante), **`ErrorCode.java`** (kein neuer
  Fehlercode nötig — ein zunächst angelegter, nie geworfener `PROGRAM_VOTE_REQUIRED` wurde
  wieder entfernt).
- **`civ-application/.../party/ChangePartyProgram.java`** (neu, ersetzt `ChangePartyProfile`,
  das entfernt wurde, da es nach der Umstellung von `PartyService` keine Aufrufer mehr hatte).
- **`civ-application/.../polity/Ballots.java`**: `carryOut` behandelt `Motion.PartyProgram`.
- **`civ-application/.../population/PersonalityLegislator.java`**: Loyalitäts-Gewichtung der
  Parteilinie; **`PersonalityVoter.java`**: nutzt jetzt `Personality.loyalty()` statt eigener
  Inline-Formel; **`civ-domain/.../population/Personality.java`**: neue Methode `loyalty()`.
- **`civ-server/.../party/PartyService.java`**: `setDirection`/`setEmblem`/`programMode` laufen
  über `ChangePartyProgram`; neue Nachrichten (`MessageKey`, `TranslationKeys`,
  `PARTY_PROGRAM_MODE_INFO/SET`, `PARTY_PROGRAM_PROPOSED`) in `de_de.json`/`en_us.json`.
- **`civ-fabric/.../CivCommandTree.java`/`EnglishCommands.java`**: `/civ partei programmodus
  [abstimmung|vorsitz]` bzw. `program-mode [vote|chair]`.
- **`civ-persistence/.../migration/V020__party_program_mode.sql`**, **`PolityTable.java`**:
  Modus wird mit dem Parteiprofil persistiert.

## Tests

- `civ-domain/.../polity/PartyProfileTest.java` (neu): Standardmodus `CHAIR`, Wertebereich,
  Wappen-Validierung, `with*`-Methoden ändern nur das jeweilige Feld, `platform()`-Ableitung.
- `civ-domain/.../population/PersonalityTest.java`: neuer Test für `Personality.loyalty()`
  (Wertebereich 0,3–0,9, monoton in `belonging`).
- `civ-application/.../party/PartyTest.java`: neuer Test `programModeVoteGoesThroughProposal` —
  Moduswechsel auf `VOTE`, eine Programmänderung wird zunächst zum offenen Antrag (keine
  Mehrheit durch die Antragstellerin allein bei zwei Bürgern), eine zweite Stimme setzt ihn um;
  bestehender `lifecycle`-Test auf `ChangePartyProgram` umgestellt (ersetzt das entfernte
  `ChangePartyProfile`).
- `JAVA_HOME=/opt/jdk25 ./gradlew :civ-domain:test :civ-application:test :civ-server:test
  :civ-fabric:compileJava` — **BUILD SUCCESSFUL**.
