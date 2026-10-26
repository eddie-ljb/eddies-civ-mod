-- Phase 0: Gemeinden, Recht, Gebiet, Grundbuch, Hauptbuch und Audit-Log.
-- Konventionen: IDs als UUID-Text, Tage als ganze Zahl, Beträge in kleinster Währungseinheit.
-- Akteure werden als Paar (actor_kind, actor_id) gespeichert: PLAYER, PERSON oder POLITY plus UUID.
-- Jede Anweisung endet mit einem Semikolon am Zeilenende.

CREATE TABLE polity (
    id          TEXT    NOT NULL PRIMARY KEY,
    seq         INTEGER NOT NULL UNIQUE,
    name        TEXT    NOT NULL,
    kind        TEXT    NOT NULL,
    tier        TEXT    NOT NULL,
    currency    TEXT    NOT NULL,
    founded_on  INTEGER NOT NULL CHECK (founded_on >= 0)
);

CREATE TABLE membership (
    polity_id   TEXT    NOT NULL REFERENCES polity (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    actor_kind  TEXT    NOT NULL,
    actor_id    TEXT    NOT NULL,
    type        TEXT    NOT NULL,
    since       INTEGER NOT NULL CHECK (since >= 0),
    PRIMARY KEY (polity_id, actor_kind, actor_id)
);

CREATE INDEX membership_by_actor ON membership (actor_kind, actor_id);

CREATE TABLE statute (
    id          TEXT    NOT NULL PRIMARY KEY,
    polity_id   TEXT    NOT NULL REFERENCES polity (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    title       TEXT    NOT NULL,
    enacted_on  INTEGER NOT NULL CHECK (enacted_on >= 0)
);

CREATE INDEX statute_by_polity ON statute (polity_id, position);

CREATE TABLE statute_value (
    statute_id  TEXT    NOT NULL REFERENCES statute (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    parameter   TEXT    NOT NULL,
    value       TEXT    NOT NULL,
    PRIMARY KEY (statute_id, parameter)
);

CREATE TABLE claim (
    dimension   TEXT    NOT NULL,
    chunk_x     INTEGER NOT NULL,
    chunk_z     INTEGER NOT NULL,
    polity_id   TEXT    NOT NULL REFERENCES polity (id),
    type        TEXT    NOT NULL,
    since       INTEGER NOT NULL CHECK (since >= 0),
    PRIMARY KEY (dimension, chunk_x, chunk_z)
);

CREATE INDEX claim_by_polity ON claim (polity_id);

CREATE TABLE plot (
    id            TEXT    NOT NULL PRIMARY KEY,
    polity_id     TEXT    NOT NULL REFERENCES polity (id),
    seq           INTEGER NOT NULL UNIQUE,
    type          TEXT    NOT NULL,
    dimension     TEXT    NOT NULL,
    min_x         INTEGER NOT NULL,
    min_z         INTEGER NOT NULL,
    max_x         INTEGER NOT NULL,
    max_z         INTEGER NOT NULL,
    holder_kind   TEXT    NOT NULL,
    holder_id     TEXT    NOT NULL,
    holder_since  INTEGER NOT NULL CHECK (holder_since >= 0),
    CHECK (min_x <= max_x AND min_z <= max_z)
);

CREATE INDEX plot_by_polity ON plot (polity_id, seq);
CREATE INDEX plot_by_holder ON plot (holder_kind, holder_id);

CREATE TABLE account_balance (
    actor_kind  TEXT    NOT NULL,
    actor_id    TEXT    NOT NULL,
    currency    TEXT    NOT NULL,
    amount      INTEGER NOT NULL CHECK (amount > 0),
    PRIMARY KEY (actor_kind, actor_id, currency)
);

CREATE TABLE currency_circulation (
    currency    TEXT    NOT NULL PRIMARY KEY,
    supply      INTEGER NOT NULL CHECK (supply >= 0),
    cash        INTEGER NOT NULL CHECK (cash >= 0)
);

CREATE TABLE domain_event (
    seq         INTEGER PRIMARY KEY AUTOINCREMENT,
    day         INTEGER NOT NULL,
    type        TEXT    NOT NULL,
    payload     TEXT    NOT NULL
);

CREATE INDEX domain_event_by_day ON domain_event (day);
CREATE INDEX domain_event_by_type ON domain_event (type, day);
