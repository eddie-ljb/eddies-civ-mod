-- Hochgeladene Bilder (Wappen, Wahlplakat, Leuchtreklame) einer Polity. Die PNG-Bytes selbst liegen
-- auf der Platte unter dem Bilder-Ordner, benannt nach "hash"; hier nur die Metadaten.

CREATE TABLE polity_image (
    id        TEXT    NOT NULL PRIMARY KEY,
    polity_id TEXT    NOT NULL,
    kind      TEXT    NOT NULL,
    hash      TEXT    NOT NULL,
    width     INTEGER NOT NULL,
    height    INTEGER NOT NULL,
    uploaded  INTEGER NOT NULL
);

CREATE INDEX polity_image_polity ON polity_image (polity_id);
