-- Heimat-Häfen (Teleportziele) einer Polity: Zugangsregel samt Preis und die zwei zusätzlichen Ziele.
-- Der erste Hafen ist stets das Gemeindehaus (polity.seat) und wird hier nicht gespeichert.

CREATE TABLE polity_port_settings (
    polity_id TEXT    NOT NULL PRIMARY KEY,
    access    TEXT    NOT NULL,
    fee       INTEGER NOT NULL
);

CREATE TABLE polity_port (
    polity_id TEXT    NOT NULL,
    slot      INTEGER NOT NULL,
    dimension TEXT    NOT NULL,
    x         INTEGER NOT NULL,
    y         INTEGER NOT NULL,
    z         INTEGER NOT NULL,
    PRIMARY KEY (polity_id, slot)
);
