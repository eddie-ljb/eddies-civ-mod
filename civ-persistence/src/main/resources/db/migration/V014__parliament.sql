-- Parlamente: Sitze mit Partei und persönlichen Stimmen, dazu Wahltag und Ende der Wahlperiode.

ALTER TABLE polity ADD COLUMN parliament_elected_on INTEGER;
ALTER TABLE polity ADD COLUMN parliament_until INTEGER;

CREATE TABLE parliament_seat (
    polity_id   TEXT    NOT NULL REFERENCES polity (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    member_kind TEXT    NOT NULL,
    member_id   TEXT    NOT NULL,
    party_id    TEXT,
    votes       INTEGER NOT NULL CHECK (votes >= 0),
    PRIMARY KEY (polity_id, member_kind, member_id)
);
