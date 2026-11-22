-- Eigene Währungen samt Zentralbank (#4) sowie Banken, Kreditscores und Kredite.
-- Smaragd ist eingebaut und wird nicht gespeichert.

CREATE TABLE currency (
    code            TEXT    NOT NULL PRIMARY KEY,
    name            TEXT    NOT NULL,
    symbol          TEXT    NOT NULL UNIQUE,
    rate_numerator  INTEGER NOT NULL CHECK (rate_numerator > 0),
    rate_denominator INTEGER NOT NULL CHECK (rate_denominator > 0),
    issuer          TEXT,
    chair           TEXT,
    base_rate_percent INTEGER NOT NULL CHECK (base_rate_percent BETWEEN 0 AND 100),
    item_id         TEXT,
    seq             INTEGER NOT NULL
);

CREATE TABLE bank (
    actor TEXT    NOT NULL PRIMARY KEY,
    seq   INTEGER NOT NULL
);

CREATE TABLE credit_score (
    actor TEXT    NOT NULL PRIMARY KEY,
    score INTEGER NOT NULL CHECK (score BETWEEN 300 AND 850)
);

CREATE TABLE loan (
    id          TEXT    NOT NULL PRIMARY KEY,
    lender      TEXT    NOT NULL,
    borrower    TEXT    NOT NULL,
    currency    TEXT    NOT NULL,
    principal   INTEGER NOT NULL CHECK (principal > 0),
    rate_bp     INTEGER NOT NULL CHECK (rate_bp >= 0),
    interest    INTEGER NOT NULL CHECK (interest >= 0),
    issued_on   INTEGER NOT NULL,
    due_on      INTEGER NOT NULL,
    repaid      INTEGER NOT NULL CHECK (repaid >= 0),
    status      TEXT    NOT NULL CHECK (status IN ('ACTIVE', 'REPAID', 'DEFAULTED')),
    seq         INTEGER NOT NULL
);
