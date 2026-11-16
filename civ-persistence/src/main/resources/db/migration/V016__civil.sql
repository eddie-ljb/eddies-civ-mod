-- Phase C: Straßen, Verträge, Briefe, Freundschaften und Chat.

CREATE TABLE street (
    polity_id TEXT    NOT NULL REFERENCES polity (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    name      TEXT    NOT NULL,
    dimension TEXT    NOT NULL,
    min_x     INTEGER NOT NULL,
    min_z     INTEGER NOT NULL,
    max_x     INTEGER NOT NULL,
    max_z     INTEGER NOT NULL,
    PRIMARY KEY (polity_id, position)
);

CREATE TABLE contract (
    id                TEXT    NOT NULL PRIMARY KEY,
    number            INTEGER NOT NULL,
    kind              TEXT    NOT NULL,
    offeror_kind      TEXT    NOT NULL,
    offeror_id        TEXT    NOT NULL,
    counterparty_kind TEXT    NOT NULL,
    counterparty_id   TEXT    NOT NULL,
    payer_is_offeror  INTEGER NOT NULL,
    currency          TEXT    NOT NULL,
    per_day           INTEGER NOT NULL,
    once              INTEGER NOT NULL,
    deposit           INTEGER NOT NULL,
    plot_polity_id    TEXT,
    plot_id           TEXT,
    title             TEXT    NOT NULL,
    text              TEXT    NOT NULL,
    term_days         INTEGER NOT NULL,
    offered_on        INTEGER NOT NULL,
    started_on        INTEGER,
    ends_on           INTEGER,
    status            TEXT    NOT NULL,
    end_reason        TEXT
);

CREATE TABLE letter (
    id         TEXT    NOT NULL PRIMARY KEY,
    position   INTEGER NOT NULL,
    from_id    TEXT    NOT NULL,
    to_id      TEXT    NOT NULL,
    subject    TEXT    NOT NULL,
    body       TEXT    NOT NULL,
    sent_on    INTEGER NOT NULL,
    deliver_on INTEGER NOT NULL,
    read       INTEGER NOT NULL
);

CREATE TABLE friendship (
    first_id  TEXT    NOT NULL,
    second_id TEXT    NOT NULL,
    request   INTEGER NOT NULL,
    PRIMARY KEY (first_id, second_id, request)
);

CREATE TABLE chat_message (
    seq    INTEGER NOT NULL PRIMARY KEY,
    from_id TEXT   NOT NULL,
    to_id  TEXT,
    text   TEXT    NOT NULL,
    at     INTEGER NOT NULL,
    origin TEXT    NOT NULL
);
