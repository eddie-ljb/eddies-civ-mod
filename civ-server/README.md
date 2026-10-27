# civ-server

Bindeglied zwischen dem Kern und einem laufenden Spielserver, **ohne Minecraft**. `civ-fabric` übersetzt nur noch Spielereignisse und Befehle hierher; alles Fachliche ist ohne Spiel testbar.

## Bausteine

| Klasse | Aufgabe |
| --- | --- |
| `CivServer` | Eine laufende Zivilisation: `open(datei, settings, listener)` lädt den Zustand, `tick()` einmal pro Servertick (Tagesabschluss mit Grundsteuer), `save()` mit der Weltspeicherung, `playerJoined(...)`, `stop()` beim Herunterfahren |
| `ServerSettings` | Tageslänge in Ticks, Claim-Grenzen (Basis + je Mitglied), Währung. Vorerst feste Standardwerte |
| `save.AsyncSaver` | Schreibt erfasste Speicherstände in einem eigenen Thread (`eddies-civ-save`) in Erfassungsreihenfolge |
| `save.SaveListener` | Rückmeldung über Erfolg oder Fehler eines Speichervorgangs, z. B. fürs Log |
| `command.CivCommands` | Logik der `/civ`-Befehle und des Gemeindehauses (Gründen mit Amboss-Namen, Steckbrief per Rechtsklick), Rechtsübersicht und -änderung; macht aus `DomainException`s Fehlermeldungen |
| `protection.ProtectionService` | Gebietsschutz für Spieler: prüft Bauen, Benutzen und Behälter gegen die Regeln der Domäne, liefert den Hinweis bei Verweigerung, Umgehung für Admins (bis zum Neustart) |
| `command.Message`, `Reply`, `MessageKey` | Übersetzbare Antworten: Schlüssel plus Argumente; übersetzt wird erst auf dem Client |
| `command.Texts` | Namen statt UUIDs, übersetzbare Begriffe (Siedlungsstufe, Claim-Art, Geldbetrag) |
| `command.TranslationKeys` | Alle Schlüssel, die die Sprachdateien enthalten müssen |

## Speichern

```
Weltspeicherung (Spielthread) ─▶ CivServer.save()
    ├─ fehlgeschlagene Stände zurückgeben (requeue)
    ├─ WorldPersistence.prepareSave()  → StateDelta (unveränderliche Kopie, nur Geändertes)
    └─ AsyncSaver.submit()             → Speicher-Thread schreibt eine SQLite-Transaktion
Server stoppt ─▶ CivServer.stop(): auf ausstehende Stände warten (max. 30 s), Rest synchron schreiben, Datei schließen
```

Der Spielthread wartet nie auf die Festplatte. Schlägt ein Schreibvorgang fehl, gehen die Änderungen nicht verloren: Der nächste `save()` schreibt sie mit dem dann aktuellen Stand erneut.

## Gebietsschutz

Die Regeln stehen in `civ-domain` (`protection.ProtectionRules`):

| Ort | Mitglieder | Fremde |
| --- | --- | --- |
| Wildnis | alles | alles |
| Gemeindeland (auch Grundstücke der Gemeinde selbst) | alles | nur was das Recht erlaubt (`outsiders_may_build`, `outsiders_may_interact`, `outsiders_may_open_containers`, Standard: nichts) |
| Grundstück eines Spielers | Inhaber alles, Mitbürger nur Türen/Knöpfe | nichts |
| Botschaft (Grundstück einer anderen Gemeinde) | deren Mitglieder alles | nichts |
| Gemeindehaus | niemand verändert es | niemand |

Bürger ändern das Recht ihrer Gemeinde mit `/civ recht setzen <Regel> <ja|nein|Zahl>`.

## Kalender

Ein Zivilisationstag dauert standardmäßig 24000 Ticks (20 Minuten) und zählt echte Serverticks. Schlafen oder `/time set` überspringen also keine Steuertage. Der Stand (Tag und Ticks) wird mitgespeichert.

## Tests

`./gradlew :civ-server:test`: Tagestakt, Speichern im Hintergrund mit Neustart, Wiederholung nach Schreibfehler, Befehlslogik inklusive Fehlermeldungen, Abdeckung der Sprachdateien in `civ-fabric` und ein Architekturtest (keine Minecraft-Imports).
