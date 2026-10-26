# civ-persistence

Speicheradapter von Eddies Civ Mod: SQLite über JDBC, eigene Schema-Migrationen und ein Audit-Log aller Domain Events. Kennt kein Minecraft; den Dateipfad bestimmt der Aufrufer (später `civ-fabric`, z. B. `<Welt>/data/eddies-civ-mod/civ.sqlite`).

## Zusammenspiel

```
CivApplication ──Events──▶ ChangeTracker          (civ-application)
                               │ merkt sich, was sich geändert hat
Weltspeicherung ──▶ WorldPersistence.flush()      (civ-application)
                               │ ChangeSet + aktueller Stand
                               ▼
                       SqliteStateStore.write()   (civ-persistence, eine Transaktion)
Serverstart     ──▶ WorldPersistence.restore() ◀── SqliteStateStore.load()
```

- **Arbeitsstand im Speicher, Schreiben mit der Weltspeicherung:** Datenbank und Welt bleiben konsistent. Stürzt der Server ab, fallen beide auf denselben Stand zurück; eingezahlte Smaragde können so nicht doppelt entstehen.
- **Nur Geändertes wird geschrieben:** Jede Zustandsänderung erzeugt ein Event; daraus leitet der `ChangeTracker` ab, welche Polities, Chunks, Grundstücke, Konten und Währungen neu zu schreiben sind. Gelöschtes (aufgelöstes Grundstück, freigegebener Chunk, Konto auf 0) wird gelöscht.
- **Laden prüft Invarianten erneut:** Aggregate entstehen über `restore(...)`-Fabriken. Ein inkonsistenter Speicherstand (z. B. verletzte Geldmengenerhaltung) führt zu einer `PersistenceException` statt zu stillen Fehlern.
- **Fehlgeschlagenes Speichern verliert nichts:** Die Änderungen bleiben vorgemerkt und werden beim nächsten Mal geschrieben.

## Schema

`src/main/resources/db/migration/V001__initial_schema.sql`

| Tabelle | Inhalt |
| --- | --- |
| `polity`, `membership`, `statute`, `statute_value` | Gemeinden mit Mitgliedern und geltendem Recht, Reihenfolgen über `seq`/`position` |
| `claim` | Hoheit pro Chunk |
| `plot` | Grundstücke, Anlagereihenfolge über `seq` |
| `account_balance`, `currency_circulation` | Konten ungleich 0, Geldmenge und Bargeld je Währung |
| `domain_event` | Audit-Log: Tag, Event-Typ, JSON |
| `schema_version` | erreichter Migrationsstand |

Akteure stehen als Paar `(actor_kind, actor_id)` mit `PLAYER`, `PERSON` oder `POLITY`. Die Datei lässt sich mit jedem SQLite-Werkzeug öffnen, z. B. *DB Browser for SQLite*:

```sql
SELECT day, payload FROM domain_event WHERE type = 'PlotTransferred' ORDER BY seq DESC LIMIT 20;
```

## Migrationen

Neue Schemaänderung = neue Datei `V002__beschreibung.sql` plus Eintrag in `SchemaMigrations`. Bestehende Dateien werden nie geändert. Jede Migration läuft mit ihrem Versionseintrag in einer Transaktion. Ist die Datenbank neuer als der Mod (Downgrade), wird sie nicht angefasst (`UnsupportedSchemaVersionException`). Anweisungen enden mit `;` am Zeilenende.

## Technik

- Treiber `org.xerial:sqlite-jdbc` (Version in `gradle.properties`), direkt über `SQLiteDataSource` statt `DriverManager`, damit er auch als eingebettetes JAR in Fabric gefunden wird.
- Eine Verbindung, Fremdschlüssel an, Standard-Journal: Nach jedem Commit liegt eine vollständige Einzeldatei vor, die Backups mit dem Weltordner kopieren können.
- Event-JSON ohne Bibliothek (`EventJson`): Records per Reflexion, Zugriffe je Klasse gecacht.

## Tests

`./gradlew :civ-persistence:test` — gegen echte SQLite-Dateien im Temp-Verzeichnis: Migrationen (leer, schrittweise, Rollback, zu neue Datenbank), vollständiger Round-Trip, inkrementelle Änderungen mit Löschungen, Audit-Log, Erkennung beschädigter Daten.
