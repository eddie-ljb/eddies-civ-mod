-- Gesetzbuch: Freitext von Paragraphen und Anträge über ganze Paragraphen oder Aufhebungen.

ALTER TABLE statute ADD COLUMN text TEXT NOT NULL DEFAULT '';

-- motion: CHANGE (parameter/value), ENACT (title, text, Werte in proposal_value), REPEAL (statute_ref, title)
ALTER TABLE proposal ADD COLUMN motion TEXT NOT NULL DEFAULT 'CHANGE';
ALTER TABLE proposal ADD COLUMN title TEXT NOT NULL DEFAULT '';
ALTER TABLE proposal ADD COLUMN text TEXT NOT NULL DEFAULT '';
ALTER TABLE proposal ADD COLUMN statute_ref TEXT;

CREATE TABLE proposal_value (
    proposal_id TEXT    NOT NULL REFERENCES proposal (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    parameter   TEXT    NOT NULL,
    value       TEXT    NOT NULL,
    PRIMARY KEY (proposal_id, parameter)
);
