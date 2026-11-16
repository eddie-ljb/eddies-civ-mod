-- Phase D: Börse (Notierungen, Bestände, Orders, Geschäfte, Tageskurse, Dividenden) und Presse.

CREATE TABLE stock_listing (
    company_id    TEXT    NOT NULL PRIMARY KEY,
    total_shares  INTEGER NOT NULL,
    initial_price INTEGER NOT NULL,
    listed_on     INTEGER NOT NULL
);

CREATE TABLE stock_holding (
    company_id  TEXT    NOT NULL,
    holder_kind TEXT    NOT NULL,
    holder_id   TEXT    NOT NULL,
    shares      INTEGER NOT NULL,
    PRIMARY KEY (company_id, holder_kind, holder_id)
);

CREATE TABLE stock_order (
    id          TEXT    NOT NULL PRIMARY KEY,
    number      INTEGER NOT NULL,
    company_id  TEXT    NOT NULL,
    trader_kind TEXT    NOT NULL,
    trader_id   TEXT    NOT NULL,
    side        TEXT    NOT NULL,
    quantity    INTEGER NOT NULL,
    remaining   INTEGER NOT NULL,
    limit_price INTEGER NOT NULL,
    placed_on   INTEGER NOT NULL
);

CREATE TABLE stock_trade (
    position    INTEGER NOT NULL PRIMARY KEY,
    company_id  TEXT    NOT NULL,
    buyer_kind  TEXT    NOT NULL,
    buyer_id    TEXT    NOT NULL,
    seller_kind TEXT    NOT NULL,
    seller_id   TEXT    NOT NULL,
    quantity    INTEGER NOT NULL,
    price       INTEGER NOT NULL,
    day         INTEGER NOT NULL
);

CREATE TABLE stock_quote (
    company_id TEXT    NOT NULL,
    day        INTEGER NOT NULL,
    open       INTEGER NOT NULL,
    high       INTEGER NOT NULL,
    low        INTEGER NOT NULL,
    close      INTEGER NOT NULL,
    volume     INTEGER NOT NULL,
    PRIMARY KEY (company_id, day)
);

CREATE TABLE dividend (
    position   INTEGER NOT NULL PRIMARY KEY,
    company_id TEXT    NOT NULL,
    per_share  INTEGER NOT NULL,
    total      INTEGER NOT NULL,
    day        INTEGER NOT NULL
);

CREATE TABLE article (
    id           TEXT    NOT NULL PRIMARY KEY,
    number       INTEGER NOT NULL,
    newspaper_id TEXT    NOT NULL,
    author_kind  TEXT    NOT NULL,
    author_id    TEXT    NOT NULL,
    title        TEXT    NOT NULL,
    body         TEXT    NOT NULL,
    topic        TEXT    NOT NULL,
    subject_kind TEXT,
    subject_id   TEXT,
    tendency     INTEGER NOT NULL,
    day          INTEGER NOT NULL,
    towns        TEXT    NOT NULL,
    readers      INTEGER NOT NULL
);
