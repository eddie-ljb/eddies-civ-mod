# Issue #18: Villager-Interaktionsmenü

## Problem
Es gab noch kein eigenes Interaktionsmenü für Dorfbewohner. Darüber sollte man Name, Charakter, Adresse,
Staatsbürgerschaft, Bildung usw. einsehen können, mit dem Dorfbewohner chatten, Verträge schließen, ihn politisch
beeinflussen, ihm Post schicken, und Dorfbewohner sollten sich auch untereinander beeinflussen, chatten und
Straftaten begehen können (je nach Charakter).

## Lösung
Die vorhandene Auskunft (`VillagerService.describe`, erreichbar über Schleichen + Rechtsklick) deckte Name, Gemeinde,
Persönlichkeit, Stimmung, Anschrift und Arbeitgeber bereits ab. Darauf aufbauend kamen neue, über Befehle erreichbare
Interaktionen hinzu, die tatsächlich etwas bewirken (nicht nur Text ausgeben):

- **Reden** (`/civ dorfbewohner reden <Thema>`): eine von drei stimmungsabhängigen, vorformulierten Antworten
  (zufrieden/gleichgültig/unzufrieden) zu einem von vier Themen (`greeting`, `weather`, `work`, `politics`).
- **Beeinflussen** (`/civ dorfbewohner beeinflussen`): hebt die Stimmung des Dorfbewohners spürbar (bis zu +6, klingt
  über mehrere Tage wieder ab, Obergrenze ±20), mit einer echten Abklingzeit von 3 Tagen je Spieler-Dorfbewohner-Paar.
- **Dorfbewohner-Post** (`/civ dorfbewohner post <Thema>`): eine eigene, bewusst einfache Pinnwand nur für
  Dorfbewohner (Absender, Thema, Tag), getrennt vom bestehenden Spieler-Briefkasten-System (`civ.community()`),
  das Dorfbewohner als Empfänger nicht kennt und das zu erweitern über den Rahmen dieses Issues hinausgegangen wäre.
- **Dorfbewohner untereinander**: beim Tagesabschluss tauschen sich je zwei Dorfbewohner derselben Gemeinde
  deterministisch (nach Tag und den beiden Dorfbewohnern) aus und heben sich dabei gegenseitig leicht in der
  Stimmung.
- **Straftaten**: ein sehr unzufriedener, sehr freiheitsliebender Dorfbewohner (negative `order`-Achse der
  Persönlichkeit UND negativer Zufriedenheitswert) hat eine kleine, tägliche, deterministische Chance (bis zu 5 %),
  mit dem Verdacht auf eine Straftat aufzufallen; das löst eine Ankündigung an seine Gemeinde aus.
- **Verträge**: Dorfbewohner können bereits als Arbeitgeber-Gegenpartei (`/civ vertrag arbeit dorfbewohner <Lohn>`)
  angestellt werden (bestehende Funktion, unverändert). Allgemeine freie Verträge (`frei`, `miete`) mit
  Dorfbewohnern erfordern eine Namensauflösung über Spielernamen, die für Dorfbewohner nicht existiert — bewusst
  nicht umgesetzt, siehe „Bewusst nicht umgesetzt“ unten.
- Die politische Beeinflussung und die Dorfbewohner-Kontakte fließen über `Satisfaction.withSocial(...)` direkt in
  die bestehende Zufriedenheit ein, wirken sich also automatisch auch auf Abwanderung, Wahlverhalten usw. aus.

### Geänderte/neue Dateien
- `civ-application/.../population/SocialEffects.java` (bereits vorhanden), `VillagerDialogue.java` (erweitert um
  `parseTopic`/`words()`), `VillagerMailbox.java` (bereits vorhanden), `Satisfaction.java` (`withSocial`, bereits
  vorhanden)
- `civ-server/.../villager/VillagerService.java`: neue Felder `social`/`mailbox`, `Calendar`-Abhängigkeit, Methoden
  `talk`, `influence`, `sendMail`, Tagesabschluss-Hook `socialAndCrime` (ruft `social.endOfDay(...)` und würfelt
  Straftat-Verdachtsfälle), `describe()` zeigt jetzt auch die letzte Post
- `civ-server/.../CivServer.java`: übergibt `calendar` an `VillagerService`
- `civ-server/.../command/MessageKey.java`, `TranslationKeys.java` (neues `dialogue_topic.*`): neue Schlüssel
- `civ-fabric/.../CivCommandTree.java`: neue Befehle `/civ dorfbewohner reden|beeinflussen|post`
- `civ-fabric/.../EnglishCommands.java`: Übersetzungen für die neuen Befehlswörter
- `civ-fabric/.../lang/{de_de,en_us}.json`: neue Texte
- `wiki/Spieler-Alltag.md`: neuer Abschnitt „Dorfbewohner: Interaktionsmenü“

## Bewusst nicht umgesetzt (und warum)
- **Bildung** in der Auskunft: Issue #21 (Education and Schools) existiert noch nicht auf `master`; das Feld wird
  deshalb schlicht weggelassen statt eines Platzhalters, der falsche Erwartungen weckt.
- **Allgemeine freie Verträge mit Dorfbewohnern** (`/civ vertrag frei`/`miete`): `CivilService.offerCustom`/
  `offerLease` lösen den Vertragspartner über einen Spielernamen auf; Dorfbewohner haben keinen solchen Namen im
  `PlayerDirectory`. Eine saubere Lösung bräuchte eine ActorRef-Variante dieser Befehle, was über den Rahmen dieses
  Issues hinausgeht (Arbeitsverträge mit Dorfbewohnern funktionieren bereits, siehe oben).
- **Echtes Gerichtsverfahren statt Verdachts-Ankündigung**: `JusticeService.file(...)` setzt einen spielerausgelösten
  `Caller` voraus; ein Dorfbewohner kann sich nicht selbst anklagen lassen. Die Verdachts-Ankündigung nennt die
  bestehende Möglichkeit, über `/civ gericht` mit dem Dorfbewohner als Beschuldigtem (`Optional<PersonRef>` in
  `defense`/`summon` schon vorhanden) tatsächlich Anklage zu erheben.
- **Freie Texteingabe beim Reden/Post**: bewusst vorformuliert (Themen- bzw. Vorlagen-Wörter) statt echter
  Sprachverarbeitung, wie in der Aufgabenstellung dieses mehrteiligen Einsatzes für alle Chat-/Dialog-Features
  dieses Mods vorgegeben.

## Tests
`JAVA_HOME=/opt/jdk25 ./gradlew :civ-domain:test :civ-application:test :civ-server:test :civ-fabric:compileJava` –
**BUILD SUCCESSFUL**. Neu in `VillagerServiceTest`:
- `talkRespondsByMood` – Antwort passt zur Stimmungslage.
- `influenceLiftsSatisfactionWithCooldown` – hebt die Zufriedenheit, zweiter Versuch direkt danach greift nicht.
- `mailShowsUpInDescribe` – gesendete Post erscheint in der Auskunft.
- `crimeSuspicionEventuallyFires` – unter 60 absichtlich sehr unzufriedenen, freiheitsliebenden Dorfbewohnern kommt
  binnen 120 simulierten Tagen mindestens ein Verdachtsfall vor (deterministisch dank `SeededIdGenerator`, nicht
  flaky).

`civ-fabric` wurde nur kompiliert (kein Minecraft-Client-Testbett in dieser Umgebung); die neuen Befehle selbst
sind daher nicht automatisiert end-to-end getestet, nur die darunterliegende Service-Logik.
