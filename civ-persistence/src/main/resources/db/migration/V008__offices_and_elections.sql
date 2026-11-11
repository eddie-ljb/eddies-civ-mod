-- Ämter einer Gemeinde und laufende Wahlen mit Kandidaten und Stimmen.

CREATE TABLE office (
    polity_id   TEXT    NOT NULL REFERENCES polity (id) ON DELETE CASCADE,
    office      TEXT    NOT NULL,
    holder_kind TEXT    NOT NULL,
    holder_id   TEXT    NOT NULL,
    since       INTEGER NOT NULL CHECK (since >= 0),
    until       INTEGER CHECK (until IS NULL OR until >= since),
    PRIMARY KEY (polity_id, office)
);

CREATE TABLE election (
    id        TEXT    NOT NULL PRIMARY KEY,
    polity_id TEXT    NOT NULL REFERENCES polity (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    office    TEXT    NOT NULL,
    called_on INTEGER NOT NULL CHECK (called_on >= 0),
    closes_on INTEGER NOT NULL CHECK (closes_on >= called_on),
    UNIQUE (polity_id, office)
);

CREATE TABLE election_candidate (
    election_id    TEXT    NOT NULL REFERENCES election (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    candidate_kind TEXT    NOT NULL,
    candidate_id   TEXT    NOT NULL,
    PRIMARY KEY (election_id, candidate_kind, candidate_id)
);

CREATE TABLE election_ballot (
    election_id    TEXT    NOT NULL REFERENCES election (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    voter_kind     TEXT    NOT NULL,
    voter_id       TEXT    NOT NULL,
    candidate_kind TEXT    NOT NULL,
    candidate_id   TEXT    NOT NULL,
    PRIMARY KEY (election_id, voter_kind, voter_id)
);
