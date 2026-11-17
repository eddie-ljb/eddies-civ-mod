-- Firmenbetriebe: Branche, Ausstattung, Lager.

CREATE TABLE enterprise (
    company_id   TEXT    NOT NULL PRIMARY KEY,
    branch       TEXT    NOT NULL,
    invested     INTEGER NOT NULL,
    auto_sell    INTEGER NOT NULL,
    last_day     INTEGER,
    last_output  INTEGER NOT NULL,
    last_revenue INTEGER NOT NULL
);

CREATE TABLE enterprise_stock (
    company_id TEXT    NOT NULL,
    commodity  TEXT    NOT NULL,
    quantity   INTEGER NOT NULL,
    PRIMARY KEY (company_id, commodity)
);
