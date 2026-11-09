-- Abstimmungen über Rechtsänderungen und die abgegebenen Stimmen.

CREATE TABLE proposal (
    id            TEXT    NOT NULL PRIMARY KEY,
    polity_id     TEXT    NOT NULL REFERENCES polity (id) ON DELETE CASCADE,
    position      INTEGER NOT NULL,
    parameter     TEXT    NOT NULL,
    value         TEXT    NOT NULL,
    proposer_kind TEXT    NOT NULL,
    proposer_id   TEXT    NOT NULL,
    opened_on     INTEGER NOT NULL CHECK (opened_on >= 0)
);

CREATE INDEX proposal_by_polity ON proposal (polity_id, position);

CREATE TABLE proposal_vote (
    proposal_id TEXT    NOT NULL REFERENCES proposal (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    voter_kind  TEXT    NOT NULL,
    voter_id    TEXT    NOT NULL,
    in_favour   INTEGER NOT NULL CHECK (in_favour IN (0, 1)),
    PRIMARY KEY (proposal_id, voter_kind, voter_id)
);
