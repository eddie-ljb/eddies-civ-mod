-- Phase B: Nachrücker im Parlament, Wahlergebnisse je Bezirk, Wahlversprechen und Wahlplakate.

ALTER TABLE parliament_seat ADD COLUMN reserve INTEGER NOT NULL DEFAULT 0;

CREATE TABLE election_result (
    id        TEXT    NOT NULL PRIMARY KEY,
    polity_id TEXT    NOT NULL REFERENCES polity (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    office    TEXT    NOT NULL,
    closed_on INTEGER NOT NULL CHECK (closed_on >= 0)
);

CREATE TABLE election_result_vote (
    result_id      TEXT    NOT NULL REFERENCES election_result (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    district_id    TEXT,
    candidate_kind TEXT    NOT NULL,
    candidate_id   TEXT    NOT NULL,
    votes          INTEGER NOT NULL CHECK (votes >= 0),
    PRIMARY KEY (result_id, position)
);

CREATE TABLE election_result_winner (
    result_id   TEXT    NOT NULL REFERENCES election_result (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    winner_kind TEXT    NOT NULL,
    winner_id   TEXT    NOT NULL,
    PRIMARY KEY (result_id, position)
);

CREATE TABLE promise (
    polity_id      TEXT    NOT NULL REFERENCES polity (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    candidate_kind TEXT    NOT NULL,
    candidate_id   TEXT    NOT NULL,
    office         TEXT    NOT NULL,
    text           TEXT    NOT NULL,
    made_on        INTEGER NOT NULL CHECK (made_on >= 0),
    PRIMARY KEY (polity_id, position)
);

CREATE TABLE promise_value (
    polity_id        TEXT    NOT NULL,
    promise_position INTEGER NOT NULL,
    position         INTEGER NOT NULL,
    parameter        TEXT    NOT NULL,
    value            TEXT    NOT NULL,
    PRIMARY KEY (polity_id, promise_position, position),
    FOREIGN KEY (polity_id, promise_position) REFERENCES promise (polity_id, position) ON DELETE CASCADE
);

CREATE TABLE poster (
    polity_id    TEXT    NOT NULL REFERENCES polity (id) ON DELETE CASCADE,
    dimension    TEXT    NOT NULL,
    x            INTEGER NOT NULL,
    y            INTEGER NOT NULL,
    z            INTEGER NOT NULL,
    subject_kind TEXT    NOT NULL,
    subject_id   TEXT    NOT NULL,
    owner_kind   TEXT    NOT NULL,
    owner_id     TEXT    NOT NULL,
    placed_on    INTEGER NOT NULL CHECK (placed_on >= 0),
    PRIMARY KEY (polity_id, dimension, x, y, z)
);
