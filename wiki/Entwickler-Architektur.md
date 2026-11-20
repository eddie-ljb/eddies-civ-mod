[Home](Home.md) › Architektur

# Architektur

Das Projekt ist ein Gradle-Multi-Modul-Build (`settings.gradle`), nach klassischer Schichtenarchitektur getrennt, damit sich die Spiellogik ohne Minecraft testen lässt:

| Modul | Inhalt |
|---|---|
| `civ-domain` | Fachlogik ohne Abhängigkeiten (Entitäten, Value Objects, reine Regeln – z. B. `Parliament`, `LawRecognizer`, `VotingMethod`) |
| `civ-application` | Use Cases und Persistenzschnittstellen (z. B. `ParliamentSession`, `ProposeMotion`, `TallyProposals`, `CivApplication`) |
| `civ-persistence` | SQLite-Anbindung, Schema-Migrationen (`db/migration/V###__*.sql`) |
| `civ-server` | Server-Ablauf, Befehle (`CivCommands`) und Menülogik, ohne Minecraft-Abhängigkeit – darum per Hand/Unit-Test gut prüfbar |
| `civ-fabric` | dünner Adapter zu Minecraft und Fabric (Blöcke, Items, Mixins, Client-Screens, Brigadier-Befehlsbaum inkl. `EnglishCommands`) |
| `civ-sim` | deterministische Simulation (für Balancing/Property-Tests über mehrere Spieltage) |

## Konventionen
- Sprache im Code und in Commit-Messages: überwiegend Deutsch (Projekt richtet sich an eine deutschsprachige Community), Paketnamen englisch (`de.etiennebader.civ.*`).
- Jeder deutsche Slash-Befehl bekommt über `EnglishCommands.WORDS` automatisch ein englisches Pendant – neue Befehlswörter sollten dort ergänzt werden, nicht durch eine zweite Implementierung.
- Persistenz-Änderungen laufen über eine neue, nummerierte Migration in `civ-persistence/src/main/resources/db/migration/`, nie über Änderungen an bestehenden Migrationen.
- Tests liegen modulweise unter `src/test/java`; `civ-domain` und `civ-application` sind reine JVM-Tests ohne Minecraft, `civ-sim` prüft über simulierte Spieltage.

## Build
```
./gradlew build          # alles bauen und testen
./gradlew :civ-domain:test
./gradlew :civ-fabric:runServer   # Testserver lokal starten
```

Siehe `README.md` für das Feature-Set aus Spielersicht und dieses Wiki für die ausführliche, nach Zielgruppe sortierte Dokumentation.
