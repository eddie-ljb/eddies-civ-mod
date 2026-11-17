# Changelog

## 1.0.0

Erste offizielle Version. Ein JAR für Server und Client (beide brauchen den Mod, weil er eigene Blöcke mitbringt).

### Neu seit der Beta
- **Firmenproduktion:** Firmen wählen eine Branche (Forst, Schäferei, Ackerbau, Bergwerk, Fischerei, Viehzucht, Ziegelei, Obstbau) und erzeugen jeden Abend Waren. Mehr Angestellte und Investitionen in die Ausstattung (Stufen mit je +25 %) heben den Ertrag. Waren gehen automatisch an den Servermarkt oder ins Lager zum Abholen bzw. Verkauf von Hand. Einstellen, Kündigen, Firmenmenü (`/civ firma menue`) und Betrieb auf der Webseite.
- **Gericht:** Aussagen, Beweise (auch von Zeugen) und Plädoyers bis zur Beratung; Geschworene stimmen auch bei frühem Urteil ab; Berufung ohne höhere Ebene verhandelt dasselbe Gericht mit einem anderen Richter neu; Einstellen in jedem offenen Abschnitt.
- **Datensicherheit:** Integritätsprüfung beim Start, Sicherung vor jeder Schemaänderung, tägliche Sicherung (sieben bleiben), `/civ admin sicherung`.
- **Webseite:** Sicherheits-Header und CSP, Herkunftsprüfung bei Änderungen, Anfragelimit, Sitzungen verfallen nach zwei Tagen ohne Nutzung, Reverse-Proxy-Betrieb mit `web_trust_proxy`, `/civ web abmelden`.
- **Rechte:** Fabric-Permission-API, also auch LuckPerms: `eddies-civ-mod:use`, `:web`, `:found`, `:admin`.
- **Englische Befehle:** Jeder Befehl gibt es auch auf Englisch, z. B. `/civ court case 3`, `/civ company invest …`.
- **Handbuch:** Neue Spieler bekommen ein Handbuch, sonst `/civ handbuch`.
- **Fix:** Amtszeit, die nachträglich eingeführt wird, beendet jetzt auch bisher unbefristete Ämter und Parlamente.

### Enthalten aus den Beta-Phasen
- Gemeinden mit Gemeindehaus, Gebiet, Grundstücken, Schutz, Läden, Bank und Servermarkt mit dynamischen Preisen.
- Dorfbewohner als Einwohner mit Persönlichkeit, Stimmung, Wahlrecht, Abwanderung, Parteien und Investitionen.
- Ämter, Parlamente, Wahlverfahren (relative Mehrheit, Stichwahl, Sainte-Laguë, D'Hondt, Hare-Niemeyer, Mehrheitswahl, Wahlkreise, Mischsystem), Personen- und Listenwahl, Sperrklausel, Amtszeitbegrenzung, Wahlpflicht.
- Wahlkampf mit Reden, Litfaßsäulen, Versprechen, Debatten und Live-Auszählung mit Hochrechnung.
- Verfassung, Gesetzbuch, Gesetzes-Baukasten, Bünde und Ebenen (Land, Nation, eigene).
- Justiz mit Straf-, Zivil- und Verfassungsklagen, Richter, Staatsanwalt, Verteidigung, Zeugen, Geschworenen, Verbannung.
- Straßen, Adressen, Verträge, Post, Freunde, Chat, Firmen, Börse, Presse, Flaggen, Hymnen.
- Eingebettete Webseite mit Live-Karte und Gelände, Wahlen, Markt, Börse, Presse, Staat, Justiz und Chat.
