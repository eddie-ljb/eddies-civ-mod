-- Servermarkt: Lagerbestand je Ware, wie er am Tag as_of war (danach täglicher Verbrauch).

CREATE TABLE market_stock (
    commodity   TEXT    NOT NULL PRIMARY KEY,
    currency    TEXT    NOT NULL,
    quantity    INTEGER NOT NULL CHECK (quantity >= 0),
    as_of       INTEGER NOT NULL CHECK (as_of >= 0)
);
