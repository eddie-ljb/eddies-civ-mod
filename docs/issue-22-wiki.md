# Issue #22: Wiki for mod

## Problem
> Es soll auf Github ein ausführliches Wiki zu allem angelegt werden. Dort sollen alle Infos für Server Owner, Admins, Spieler und mehr dargestellt werden.

Bisher gab es nur README.md, MODRINTH.md und CHANGELOG.md als eine lange, unstrukturierte Seite – kein nach Zielgruppe gegliedertes Nachschlagewerk.

## Lösung
Ein vollständiges Wiki wurde als Markdown-Seiten unter `wiki/` im Repository angelegt, aufgeteilt nach Zielgruppe (Spieler, Admins/Server-Owner, Entwickler) statt einer einzigen langen Seite.

**Wichtige Einschränkung:** Dieses Repository greift in dieser Session über ein GitHub-App-Zugangstoken zu, das ausdrücklich nur auf `eddie-ljb/eddies-civ-mod` beschränkt ist. Das separate Wiki-Git-Repository (`eddie-ljb/eddies-civ-mod.wiki.git`) ist davon nicht erfasst – ein `git clone`/`git push` dorthin schlägt mit einem Authentifizierungsfehler fehl (`could not read Username for 'https://github.com'`), und `add_repo` lehnt `eddie-ljb/eddies-civ-mod.wiki` als unbekanntes Repository ab (GitHub-Wikis sind keine regulären Repos über die REST-API). Deshalb konnte der Inhalt nicht direkt in das echte GitHub-Wiki gepusht werden. Die Inhalte liegen stattdessen versioniert im Hauptrepo unter `wiki/` und sind inhaltlich exakt so geschrieben, wie sie als Wiki-Seiten erscheinen sollen.

## Übersicht der angelegten Seiten
| Seite | Inhalt |
|---|---|
| `wiki/Home.md` | Übersicht + Inhaltsverzeichnis, gegliedert nach Spieler/Admin/Entwickler |
| `wiki/Spieler-Gemeinden-und-Grundstuecke.md` | Gemeinde gründen, Dorfbewohner, Läden, Grundstücke, Straßen/Adressen, Schutz |
| `wiki/Spieler-Politik.md` | Abstimmungen, Ämter und Wahlen, Wahlverfahren, Wahlkreise, Parteien, Parlament, Wahlurne/-karte, Wahlkampf |
| `wiki/Spieler-Recht-und-Verfassung.md` | Recht-Tabelle, Gesetzbuch, Verfassung, Bünde und Ebenen |
| `wiki/Spieler-Justiz.md` | Klagen, Gerichtsablauf, Geschworene, Berufung |
| `wiki/Spieler-Wirtschaft.md` | Bank, Markt, Firmen, Börse, Presse |
| `wiki/Spieler-Alltag.md` | Verträge, Post, Freunde, Flaggen und Hymnen, englische Befehle/Handbuch |
| `wiki/Spieler-Webseite.md` | Die eingebettete Server-Webseite, alle Reiter |
| `wiki/Befehlsuebersicht.md` | Vollständige `/civ`-Befehlsreferenz nach Themen, inkl. Hinweis zur deutsch→englisch-Übersetzung |
| `wiki/Admin-Installation-und-Konfiguration.md` | Voraussetzungen, alle Einträge aus `config/eddies-civ-mod.properties`, Rechte-Knoten |
| `wiki/Admin-Sicherungen-und-Webseite.md` | Datensicherung/Wiederherstellung, Reverse-Proxy/HTTPS-Absicherung |
| `wiki/Entwickler-Architektur.md` | Modulaufbau, Konventionen, Build-Befehle für Mitwirkende |

## Hinweis für den Owner
Um die Inhalte ins echte GitHub-Wiki zu übernehmen:
1. Unter **Settings → Features → Wikis** das Wiki-Feature aktivieren (falls noch nicht geschehen) und einmal eine erste Seite über die Weboberfläche anlegen (dadurch wird das Wiki-Git-Repository initialisiert).
2. Entweder die Inhalte der Dateien unter `wiki/*.md` einzeln per Web-Editor in die entsprechenden Wiki-Seiten einfügen (Dateiname ohne `.md` = Seitenname), oder, falls dem Session-Token künftig Zugriff auf `eddie-ljb/eddies-civ-mod.wiki.git` gewährt wird:
   ```
   git clone https://github.com/eddie-ljb/eddies-civ-mod.wiki.git
   cp wiki/*.md eddies-civ-mod.wiki/
   cd eddies-civ-mod.wiki && git add -A && git commit -m "wiki import" && git push
   ```
3. Die Dateien unter `wiki/` im Hauptrepo bleiben als versionierte Quelle bestehen und werden mit künftigen Issues/PRs weiter ergänzt (z. B. je eine neue Seite für größere neue Features).
