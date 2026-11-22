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

## Third-Party-Mod-API (#51)

Fremde Fabric-Mods können eigene Gesetze anmelden, die Gemeinden per Abstimmung oder Verordnung setzen
(`/civ recht setzen <modid.name> ja|nein`, Gesetzeseditor, Gesetzbuch), und an ihren Stellen abfragen, ob das
geltende Recht eine Handlung erlaubt. Beispiel Applied Energistics 2: „ME-Systeme sind verboten“.

**1. Entrypoint** in der `fabric.mod.json` der fremden Mod (Eddies Civ Mod als Abhängigkeit/Compile-Only):

```json
"entrypoints": { "eddies-civ-mod:api": [ "com.example.Ae2Compat" ] }
```

**2. Gesetz anmelden** (`onCivApi` läuft beim Start von Eddies Civ Mod, unabhängig von der Ladereihenfolge):

```java
public final class Ae2Compat implements CivModApi {
    public static CivLaw ME_SYSTEMS;
    public void onCivApi() {
        ME_SYSTEMS = CivApi.registerLaw("ae2", "me_systems_allowed", CivLaw.Scope.TERRITORY);
    }
}
```

**3. Abfragen** an der Stelle im Mod-Code:

```java
if (!Ae2Compat.ME_SYSTEMS.permitsOrNotify(player, level, pos, Component.translatable("ae2compat.forbidden"))) {
    return InteractionResult.FAIL;
}
```

**Scope** legt fest, wessen Recht gilt: `TERRITORY` = Gemeinde, auf deren Gebiet `pos` liegt (für alle dort);
`CITIZENSHIP` = Gemeinde des Spielers (überall). In der Wildnis bzw. ohne Gemeinde gilt kein Recht, die Handlung
ist erlaubt; Admins mit `/civ admin umgehen` ebenfalls.

**Regeln:** ID `modid.name` (a-z, 0-9, `_`), Standard ist „erlaubt“, sodass eine neu installierte Mod nichts
verbietet. Anmeldung nur vor dem Serverstart. Anzeigename: Sprachschlüssel `eddies-civ-mod.law.<id>` in der
Sprachdatei der fremden Mod. Wird die Mod entfernt, verwirft der Spielstand ihre Gesetze beim Laden statt
abzubrechen. Das Paket `de.etiennebader.civ.api` ist die stabile Oberfläche; alles andere ist intern.
