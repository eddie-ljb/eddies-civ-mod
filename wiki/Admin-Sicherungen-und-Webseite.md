[Home](Home.md) › Sicherungen und Webseiten-Betrieb

# Sicherungen und Webseiten-Betrieb

## Sicherungen
- Beim Start prüft der Mod die Datenbank (`PRAGMA quick_check`). Ist sie beschädigt, startet der Server nicht und nennt den Ordner mit den Sicherungen.
- Vor jeder Schemaänderung (nach einem Update) entsteht `backups/civ-v<alte Version>-….sqlite`, einmal je Spieltag `civ-daily-….sqlite`; von jeder Art bleiben die neuesten sieben. `/civ admin sicherung` schreibt sofort eine.
- **Wiederherstellen:** Server stoppen, die gewünschte Sicherung aus `<Welt>/data/eddies-civ-mod/backups/` als `civ.sqlite` in den Ordner darüber kopieren, Server starten.

## Webseite absichern
- Die Seite setzt Sicherheits-Header (CSP, kein Einbetten in fremde Seiten), prüft bei Änderungen die Herkunft, begrenzt Anfragen je Adresse (600 lesend, 120 schreibend je Minute) und Fehlversuche bei der Anmeldung (10 je Adresse, 200 insgesamt je Minute).
- Für HTTPS einen Reverse-Proxy davorsetzen und `web_bind=127.0.0.1`, `web_trust_proxy=true`, `web_public_url=https://…` eintragen. Beispiel für nginx:

```
location / {
    proxy_pass http://127.0.0.1:8765;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Siehe auch [Installation und Konfiguration](Admin-Installation-und-Konfiguration.md) für `web_enabled`, `web_bind`, `web_port` und `web_public_url`.
