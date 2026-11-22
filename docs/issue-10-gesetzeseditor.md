# Issue #10: Gesetzes- und Rechte-Editor, Gesetzesüberblick

## Umsetzung
- `GET /api/laws` (öffentlich): Katalog aller Rechte, je Polity (Gemeinde/Bund) Verfassung, Paragraphen, ausdrücklich gesetzte Rechte; angemeldet zusätzlich die eigene Gesetzgebungs-Polity (`legislates`) und sichtbare Entwürfe.
- `POST /api/laws` (angemeldet) mit `action`: `check`, `enact`, `repeal`, `rights`, `draft`, `draftDelete`.
- Rechte-Editor: `EnactParagraph.Command` trägt jetzt `explicitValues`; `LawBookService.enactRights` dekodiert und validiert jeden Wert gegen den `ParameterCatalog` und erlässt einen Paragraphen ohne Text. Abstimmungspflicht/Verfassungsregeln laufen unverändert über `EnactParagraph`.
- Berechtigung: Schreibaktionen nur für `LawBookService.legislature(caller)` (geführte Ebene, sonst Wohngemeinde), serverseitig geprüft; Amtsrechte (Bürgermeister bzw. Abstimmung) prüft `EnactParagraph`.
- Validierung: Titel 1–80, Text höchstens 1000 Zeichen (`Statute`), höchstens 50 Rechte je Paragraph, 30 Entwürfe je Person.

## Entwürfe
Standardmäßig nur im Arbeitsspeicher: sie gehen beim Neustart verloren. Optional (Server-Konfiguration `law_drafts_persist=true`, Standard `false`) schreibt `LawBookService` sie nach jeder Änderung atomar in `law-drafts.tsv` im Datenordner (eine Zeile je Entwurf, Tab-getrennt, maskiert) und liest sie beim Start. Kein Datenbankschema, keine Migration; kaputte Zeilen werden übersprungen, Schreibfehler gehen nur auf stderr und lassen den Entwurf im Speicher gültig. Sichtbar für die Autorin/den Autor und die Partei zum Zeitpunkt des Speicherns; nur die Autorin/der Autor überschreibt oder löscht.

## Testen
`./gradlew :civ-server:test` (`LawBookServiceTest`, `WebIntegrationTest`). Manuell: Server starten, im Spiel `/civ web` für den Code, auf der Webseite anmelden, Seite „Recht“ öffnen.
