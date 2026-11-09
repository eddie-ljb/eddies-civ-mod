-- Läden von Spielern: je Kistenposition höchstens einer.

CREATE TABLE shop (
    dimension  TEXT    NOT NULL,
    x          INTEGER NOT NULL,
    y          INTEGER NOT NULL,
    z          INTEGER NOT NULL,
    seq        INTEGER NOT NULL,
    owner_kind TEXT    NOT NULL,
    owner_id   TEXT    NOT NULL,
    commodity  TEXT    NOT NULL,
    price      INTEGER NOT NULL CHECK (price > 0),
    currency   TEXT    NOT NULL,
    opened_on  INTEGER NOT NULL CHECK (opened_on >= 0),
    PRIMARY KEY (dimension, x, y, z)
);
