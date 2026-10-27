-- Etappe 1 der Minecraft-Anbindung: Kalender der Zivilisation und bekannte Spielernamen.

CREATE TABLE world_clock (
    id              INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
    today           INTEGER NOT NULL CHECK (today >= 0),
    ticks_into_day  INTEGER NOT NULL CHECK (ticks_into_day >= 0)
);

CREATE TABLE known_player (
    player_id   TEXT    NOT NULL PRIMARY KEY,
    name        TEXT    NOT NULL
);
