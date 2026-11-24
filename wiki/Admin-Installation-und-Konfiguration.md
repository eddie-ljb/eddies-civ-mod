[Home](Home.md) › Installation und Konfiguration

# Installation und Konfiguration

## Installation
Ein JAR für Server **und** Client: Der Mod bringt eigene Blöcke mit, deshalb brauchen ihn alle Spieler. Voraussetzungen: Fabric Loader ab 0.19.5, Fabric API ab 0.164.0, Java 25.

## Konfiguration
`config/eddies-civ-mod.properties` wird beim ersten Start angelegt:

| Eintrag | Bedeutung |
|---|---|
| `ticks_per_day` | Länge eines Zivilisationstags; 24000 = ein Minecraft-Tag |
| `claim_base` | Chunks, die jede Gemeinde beanspruchen darf |
| `claim_per_member` | zusätzliche Chunks je Mitglied |
| `price_reference_emeralds_per_player` | Geldmenge je Spieler, bei der der Markt Grundpreise zahlt |
| `villager_income_hundredths_per_day` | Einnahmen je Dorfbewohner und Tag; 100 = 1 Smaragd |
| `villager_emigration` | ob sehr unzufriedene Dorfbewohner wegziehen (Standard `true`) |
| `villager_parties`, `villager_party_founders` | ob Dorfbewohner Parteien beitreten/gründen (Standard `true`) und ab wie vielen Gleichgesinnten (Standard 5) |
| `federation_levels` | eigene Ebenen als `id:Name:Rang`, kommagetrennt, z. B. `kreis:Kreis:10,bund:Bund:40`; eingebaut `gemeinde` 0, `land` 20, `nation` 30 |
| `market_town_inhabitants`, `city_inhabitants`, `metropolis_inhabitants` | Einwohner für die Siedlungsstufen |
| `web_enabled`, `web_bind`, `web_port` | Webseite ein/aus, Adresse, Port (Standard `true`, `0.0.0.0`, `8765`) |
| `web_public_url` | Adresse, die `/civ web` den Spielern nennt, z. B. `https://civ.example.org` |
| `web_trust_proxy` | nur hinter Reverse-Proxy `true`: `X-Forwarded-For`/`-Proto` zählen (Standard `false`) |
| `live_count`, `live_count_seconds`, `live_count_projections` | Live-Auszählung an/aus, Dauer (s), Hochrechnungen an/aus (Standard `true`, 60, `true`) |
| `debate_minutes` | Länge einer Wahldebatte (Standard 5) |
| `anthem_on_enter` | Hymne beim Betreten eines Gebiets (Standard `false`) |
| `world_map`, `world_map_radius_chunks` | gerenderte Weltkarte für die Webseite an/aus, Umkreis um Spieler (Standard `true`, 6); Kacheln unter `<Welt>/data/eddies-civ-mod/map/` |
| `price_level_min`, `price_level_max` | Grenzen des Marktpreisfaktors (Standard `0.5`/`4.0`) |
| `price_level_damping` | Dämpfung der Preisreaktion auf die Geldmenge je Kopf (Standard `0.5` = Quadratwurzel-Formel; `1.0` ließe Preise linear mit der Geldmenge schwanken, also stärkere Inflation/Deflation) |

Weitere Dateien im Datenordner: `roads.tsv` (Wegebauten), `religion.tsv` (jetzt auch Schriften, Klerus-Ämter und Segen), `construction.tsv` (Bauaufträge) und im Bilderordner `maps.tsv` (Karte je Logo). Alle werden atomar geschrieben und beim Start gelesen.

Fehlen Einträge (nach einem Update), ergänzt der Mod sie beim Start mit Standardwerten.

Der Zustand liegt je Welt in `<Welt>/data/eddies-civ-mod/civ.sqlite` und wird im Hintergrund zusammen mit der Welt gespeichert.

## Rechte
Über die Permission-API von Fabric, also auch mit LuckPerms. Ohne Rechte-Mod gilt die Operator-Stufe.

| Knoten | Wofür | ohne Rechte-Mod |
|---|---|---|
| `eddies-civ-mod:use` | alle `/civ`-Befehle | alle Spieler |
| `eddies-civ-mod:web` | `/civ web` | alle Spieler |
| `eddies-civ-mod:found` | `/civ gruenden` ohne Gemeindehaus | Spielleiter (Stufe 2) |
| `eddies-civ-mod:admin` | `/civ admin …` | Spielleiter (Stufe 2) |

Weiter mit [Sicherungen und Webseiten-Betrieb](Admin-Sicherungen-und-Webseite.md).
