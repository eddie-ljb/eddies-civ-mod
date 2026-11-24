[Home](Home.md) › Webseite

# Webseite

Der Server stellt eine Webseite bereit, standardmäßig auf Port 8765, hell und dunkel, auch auf dem Handy:

- **Start:** Live-Karte aller Gemeinden, Kennzahlen, wer online ist, Einstieg und wichtige Befehle.
- **Welt:** Register aller Gemeinden (Wappen, Postleitzahl, Straßen und Adressen), Bünde, Parteien, Firmen und Zeitungen mit Suche.
- **Karte:** alle beanspruchten Chunks je Dimension mit Straßen, Wahlkarte nach Bezirken; mit `world_map` darunter die echte Landschaft (Kacheln, alle paar Minuten neu gerendert, ähnlich Dynmap). Die Farbgebung für Bünde (Nation/Land/Gemeinde) und Filter sind in Issue #8 beschrieben/überarbeitet.
- **Staat:** Live-Auszählung mit Hochrechnung, laufende Debatten, Flaggen, Hymnen zum Abspielen, Wahlkreise.
- **Recht:** Überblick über Verfassung, Paragraphen und Rechte jeder Gemeinde und jedes Bundes; wer eine Ebene führt (sonst die Bürger ihrer Gemeinde), erlässt oder hebt Gesetze auf, ändert Rechte per Formular und speichert Gesetzesvorlagen als Entwurf (eigene und die der Partei; dauerhaft nur, wenn der Admin `law_drafts_persist=true` setzt, sonst bis zum Serverneustart).
- **Justiz:** alle Verfahren mit Akte und Protokoll, Klage einreichen, Aussagen, Plädoyers, Beweise, Urteile, Abstimmen als Geschworener.
- **Wahlen** mit Umfragen und Ergebnissen, **Markt** mit Preisen und Läden der Spieler.
- **Börse:** Kursliste mit Verlauf, Chart mit Umsatz, Kennzahlen, Aktionäre, Orderbuch, Handeln und Depot.
- **Presse:** Artikel mit Haltung und Thema, Zeitungen mit Redaktion und Verbreitung, Artikel schreiben mit Live-Erkennung.
- **Post:** Postfach, gesendete Briefe, Briefe schreiben und beantworten.
- **Chat:** allgemeiner Chat (mit dem Spiel verbunden) und Direktnachrichten an Freunde.
- **Logos:** Wappen, Wahlplakate und Leuchtreklamen der Einheiten hochladen, die man führt (Bilder werden im Browser auf 128 × 128 verkleinert), und entfernen; siehe [Logos](Spieler-Logos.md).
- **Freunde:** Anfragen senden, annehmen, ablehnen; wer online ist.
- **Mein Bereich:** Kontostand, Anschrift, Überweisen, Verträge unterschreiben/anbieten/kündigen, Ämter, Wahlen und Abstimmungen.

**Anmelden:** Mit `/civ web` holt man sich im Spiel einen Code und gibt ihn auf der Webseite ein. Sitzungen verfallen nach zwei Tagen ohne Nutzung oder nach sieben Tagen; `/civ web abmelden` meldet überall ab.

Admin-Themen zum Betrieb der Webseite (Reverse-Proxy, HTTPS, Absicherung) stehen unter [Sicherungen und Webseiten-Betrieb](Admin-Sicherungen-und-Webseite.md).

## Religion
Der Reiter „Religion“ zeigt alle Religionen mit Oberhaupt, Klerusmacht, Mitgliedern, Inbrunst und laufender Klerus-Wahl sowie die Glaubensverteilung unter den Dorfbewohnern. Angemeldet kann man beitreten, austreten, eine Religion gründen, für das Klerusamt kandidieren und wählen. Die **Heilige Schrift** jeder Religion steht im Wortlaut dort; wer sie bearbeiten darf (Gründer, Oberhaupt, Amtsträger mit dem Recht `schrift`), bekommt einen Editor mit Speichern und Umbenennen. Gründer und Oberhaupt legen **Klerus-Ämter** fest, vergeben und entziehen sie; Mitglieder mit dem Recht segnen und taufen Spieler. Siehe [Religion](Spieler-Religion.md).

## Aufbau
Die Navigation ist nach Themen gruppiert (Welt, Politik, Wirtschaft, Gesellschaft, Kontakt) und klappt auf dem Handy als Menü auf; Inhalte und Adressen der Reiter (`#karte`, `#recht` …) sind unverändert.
