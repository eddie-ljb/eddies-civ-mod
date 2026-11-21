# Issue #16: Parteilisten für Listenwahlen

## Problem

> Es soll Parteilisten bei jeder Wahl, bei der man über Liste wählt geben. Die Listenreihenfolge soll
> entweder per Abstimmung in der Partei oder über den Parteivorsitzenden definiert werden, je nachdem
> was in den Parteiregelungen definiert wurde. Je nach Liste und Stimmanteile der Partei werden dann
> die Plätze im Parlament usw. entsprechend besetzt.

Bisher füllte eine Listenwahl (`ballot_type=list`) die einer Partei zugeteilten Sitze einfach in
Kandidatur-Reihenfolge (wer zuerst kandidiert hat, zieht zuerst ein) – es gab keine von der Partei
selbst festgelegte Reihenfolge.

## Lösung

Eine Partei kann jetzt eine explizite **Parteiliste** festlegen: eine Reihenfolge ihrer Mitglieder, die
bestimmt, wer bei einer Listenwahl zuerst einzieht, wenn der Partei Sitze zugeteilt werden. Per
Parteiregel entscheidet entweder allein der Vorsitz oder die Mitglieder per Abstimmung.

## Implementierung

- **`PartyList`** (`civ-domain/.../polity`): kodiert die Liste als Text (`PLAYER:<uuid>,PERSON:<uuid>,…`,
  gespeichert im neuen Rechtsparameter `party_list`) und wendet sie auf eine Kandidatenliste an
  (`PartyList.apply`): gelistete Kandidaten zuerst in Listenreihenfolge, alle übrigen (z. B. weil sie
  nach dem Festlegen der Liste kandidiert haben, oder weil die Liste leer ist) danach in
  Kandidatur-Reihenfolge – niemand fällt weg.
- **Neue Rechtsparameter** (`StandardParameters`): `party_list` (Text, intern verwaltet) und
  `party_list_by_vote` (Standard: aus = Vorsitz entscheidet allein).
- **`ParliamentCount`**: bei Listenwahl wird die Kandidatenreihenfolge einer Liste jetzt über
  `PartyList.apply(...)` statt über die rohe Kandidatur-Reihenfolge gebildet.
- **`PartyService`** (`civ-server/.../party`): drei neue Aktionen –
  - `partyList(caller)` – zeigt die aktuelle Liste und den Entscheidungsmodus,
  - `setPartyListMode(caller, byVote)` – nur Vorsitz, schaltet den Modus um,
  - `setPartyList(caller, playerNames)` – setzt die Liste; ist `party_list_by_vote` aktiv, wird daraus
    ein Antrag, über den die Mitglieder abstimmen (bestehende `ProposeMotion`/`Motion.Change`-Maschinerie),
    sonst entscheidet der Vorsitz direkt per `AmendLaw`. Dorfbewohner-Mitglieder lassen sich (mangels
    ansprechbarem Namen) nicht in die Liste aufnehmen und bleiben in Kandidatur-Reihenfolge am Ende.
- **Befehle** (`CivCommandTree`): `/civ partei liste zeigen`, `/civ partei liste setzen <Spieler...>`,
  `/civ partei liste modus abstimmung|vorsitz` (als Kinder des schon bestehenden `/civ partei liste`,
  das weiterhin ohne Argument alle Parteien auflistet).
- **Übersetzungen**: neue `eddies-civ-mod.command.party_candidates_*`- und
  `eddies-civ-mod.command.unknown_player`-Schlüssel (de/en) sowie `eddies-civ-mod.law.party_list{,_by_vote}`
  für die Rechtsübersicht.

### Verhältnis zu Issue #15 (Parteiprogramme)

Issue #15 führt einen eigenen Abstimmungs-vs-Vorsitz-Modus für das Parteiprogramm ein
(`ProgramMode`/`programModeByVote`-artig). Dieser hier (`party_list_by_vote`) ist bewusst getrennt
gehalten, da beide PRs parallel entstanden sind – ein sinnvoller Folge-Schritt wäre, beide Modi auf
einen gemeinsamen "Parteiregel per Abstimmung oder Vorsitz"-Mechanismus zu vereinheitlichen, sobald
beide auf `master` sind.

## Tests

- `PartyListTest` (neu, `civ-domain`): Rundweg encode/decode, leere Liste, Überspringen ungültiger
  Einträge, Anwenden auf Kandidaten (gelistete zuerst, Rest in Kandidatur-Reihenfolge, gelistete
  Nicht-Kandidaten fallen weg), unveränderte Reihenfolge ohne gesetzte Liste.
- `./gradlew :civ-domain:test :civ-application:test :civ-server:test` – **BUILD SUCCESSFUL** (inkl. der
  bestehenden `TranslationCoverageTest`, die die beiden neuen `law.*`-Schlüssel jetzt verlangt).
- Kein dediziertes Ende-zu-Ende-Fixture für `ParliamentCount` mit Listenwahl ergänzt (hoher Setup-Aufwand
  für PolityView/Election/ElectionCount) – die Kernlogik der Reihenfolge ist aber vollständig über
  `PartyListTest` abgedeckt, und die Einbindung in `ParliamentCount` ist eine reine Ersetzung der
  Sortierfunktion für den Listenwahl-Zweig.
