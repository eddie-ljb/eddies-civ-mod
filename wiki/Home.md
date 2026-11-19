# Eddies Civ Mod – Wiki

Dieses Verzeichnis enthält die Inhalte für das GitHub-Wiki des Mods (Issue #22). Da dieser Arbeitsbereich keinen
Push-Zugriff auf das separate `*.wiki.git`-Repository von GitHub hat, liegen die Seiten hier versioniert im
Hauptrepository unter `wiki/` und werden in Pull Requests mitgepflegt. Ein Repository-Owner kann sie bei Bedarf
1:1 ins echte GitHub-Wiki kopieren (Wiki unter **Settings → Features → Wikis** aktivieren, dann je Datei eine
Wiki-Seite mit demselben Namen anlegen).

## Seiten

- [Karte](Karte.md) – Web- und Ingame-Karte, Farben und Ebenen-Filter.
> Diese Seiten liegen als Entwurf unter `wiki/` im Hauptrepo, weil das Build-Konto dieser Sitzung keinen Schreibzugriff
> auf das separate `eddies-civ-mod.wiki.git`-Repository hat. Bitte Inhalte von hier in das echte GitHub-Wiki
> übernehmen (Repo → Tab **Wiki** → Seite mit demselben Namen anlegen/bearbeiten).

- [Parlament](Parlament.md) – Sitzungen, Anträge, Tagesordnung, Abstimmungsregeln
Fabric-Mod für Minecraft 26.3: Gemeinden, Gebiete, Recht, Geld und ein Servermarkt mit dynamischen Preisen. Alle Menüs laufen auf dem Server als Truhenmenüs, deshalb können auch Spieler ohne den Mod auf einem Server mit dem Mod mitspielen (für Blöcke und eigene Items brauchen alle den Client-Teil des Mods).

Dieses Wiki ist nach Zielgruppe sortiert: Spieler finden hier, wie man Gemeinden gründet, wählt und wirtschaftet; Admins, wie der Server eingerichtet, abgesichert und gepflegt wird; Entwickler, wie der Code aufgebaut ist.

## Für Spieler
- [Gemeinden und Grundstücke](Spieler-Gemeinden-und-Grundstuecke.md) – Gemeinde gründen, Dorfbewohner, Läden, Grundstücke, Straßen und Adressen, Gebietsschutz
- [Politik: Ämter, Wahlen und Parteien](Spieler-Politik.md) – Abstimmungen, Ämter und Wahlen, Wahlverfahren, Wahlkreise, Parlament, Parteien, Wahlkampf
- [Recht, Verfassung und Bünde](Spieler-Recht-und-Verfassung.md) – Verfassung, Gesetzbuch, Gesetzes-Baukasten, Bünde und Ebenen
- [Justiz](Spieler-Justiz.md) – Klagen, Gerichtsverfahren, Geschworene, Berufung
- [Wirtschaft: Bank, Markt, Firmen und Börse](Spieler-Wirtschaft.md) – Bank, Servermarkt, Firmen, Aktien, Presse
- [Alltag: Post, Freunde, Verträge, Hoheitszeichen](Spieler-Alltag.md) – Post, Freunde und Chat, Verträge, Flaggen und Hymnen
- [Webseite](Spieler-Webseite.md) – die eingebettete Server-Webseite
- [Befehlsübersicht](Befehlsuebersicht.md) – alle `/civ`-Befehle, deutsch und englisch

## Für Admins / Server-Owner
- [Installation und Konfiguration](Admin-Installation-und-Konfiguration.md) – Voraussetzungen, `config/eddies-civ-mod.properties`, Rechte/Permissions
- [Sicherungen und Webseiten-Betrieb](Admin-Sicherungen-und-Webseite.md) – Datensicherung, Wiederherstellung, Reverse-Proxy/HTTPS, Absicherung

## Für Entwickler
- [Architektur](Entwickler-Architektur.md) – Modulaufbau und Verantwortlichkeiten

## Hinweis zu diesem Wiki
Diese Seiten liegen als Markdown-Dateien im Repository unter `wiki/` (siehe `docs/issue-22-wiki.md` für den Hintergrund) und werden laufend um neue Themen ergänzt, sobald dazu passende Issues umgesetzt werden.
