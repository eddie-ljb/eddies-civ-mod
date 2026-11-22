-- Kreditangebote und „Selbstbedienung“ von Banken (#4).

ALTER TABLE bank ADD COLUMN self_service INTEGER NOT NULL DEFAULT 0 CHECK (self_service IN (0, 1));

CREATE TABLE loan_offer (
    id         TEXT    NOT NULL PRIMARY KEY,
    lender     TEXT    NOT NULL,
    borrower   TEXT    NOT NULL,
    currency   TEXT    NOT NULL,
    principal  INTEGER NOT NULL CHECK (principal > 0),
    term_days  INTEGER NOT NULL CHECK (term_days > 0),
    offered_on INTEGER NOT NULL,
    expires_on INTEGER NOT NULL,
    seq        INTEGER NOT NULL
);
