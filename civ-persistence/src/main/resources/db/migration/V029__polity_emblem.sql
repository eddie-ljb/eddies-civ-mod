-- Aufgestellte Flaggen- und Wappen-Blöcke einer Polity (stärken die Treue der Dorfbewohner).

CREATE TABLE polity_emblem (
    dimension TEXT    NOT NULL,
    x         INTEGER NOT NULL,
    y         INTEGER NOT NULL,
    z         INTEGER NOT NULL,
    polity_id TEXT    NOT NULL,
    kind      TEXT    NOT NULL,
    placed    INTEGER NOT NULL,
    PRIMARY KEY (dimension, x, y, z)
);

CREATE INDEX polity_emblem_polity ON polity_emblem (polity_id);
