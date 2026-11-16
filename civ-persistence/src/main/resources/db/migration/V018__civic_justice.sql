-- Wahlkreise, Hoheitszeichen (Flaggen, Hymnen) und Justiz.

CREATE TABLE constituency (
    id        TEXT    NOT NULL PRIMARY KEY,
    position  INTEGER NOT NULL,
    polity_id TEXT    NOT NULL,
    name      TEXT    NOT NULL
);

CREATE TABLE constituency_area (
    constituency_id TEXT    NOT NULL,
    position        INTEGER NOT NULL,
    dimension       TEXT    NOT NULL,
    min_x           INTEGER NOT NULL,
    min_z           INTEGER NOT NULL,
    max_x           INTEGER NOT NULL,
    max_z           INTEGER NOT NULL,
    PRIMARY KEY (constituency_id, position)
);

CREATE TABLE constituency_town (
    constituency_id TEXT    NOT NULL,
    position        INTEGER NOT NULL,
    town_id         TEXT    NOT NULL,
    PRIMARY KEY (constituency_id, position)
);

CREATE TABLE polity_flag (
    polity_id TEXT NOT NULL PRIMARY KEY,
    base      TEXT NOT NULL,
    layers    TEXT NOT NULL
);

CREATE TABLE polity_anthem (
    polity_id  TEXT NOT NULL PRIMARY KEY,
    title      TEXT NOT NULL,
    instrument TEXT NOT NULL,
    notes      TEXT NOT NULL
);

CREATE TABLE court_case (
    id           TEXT    NOT NULL PRIMARY KEY,
    number       INTEGER NOT NULL,
    court_id     TEXT    NOT NULL,
    kind         TEXT    NOT NULL,
    plaintiff    TEXT    NOT NULL,
    defendant    TEXT    NOT NULL,
    charge       TEXT    NOT NULL,
    target       TEXT    NOT NULL,
    judge        TEXT,
    prosecutor   TEXT,
    defense      TEXT,
    stage        TEXT    NOT NULL,
    filed_on     INTEGER NOT NULL,
    stage_until  INTEGER NOT NULL,
    appeal_of    TEXT,
    v_upheld     INTEGER,
    v_fine       INTEGER,
    v_damages    INTEGER,
    v_exile      INTEGER,
    v_dismissal  INTEGER,
    v_reasoning  TEXT,
    v_decided_by TEXT,
    v_guilty     INTEGER,
    v_innocent   INTEGER,
    v_day        INTEGER
);

CREATE TABLE case_party (
    case_id  TEXT    NOT NULL,
    role     TEXT    NOT NULL,
    position INTEGER NOT NULL,
    actor    TEXT    NOT NULL,
    guilty   INTEGER,
    PRIMARY KEY (case_id, role, position)
);

CREATE TABLE case_evidence (
    case_id      TEXT    NOT NULL,
    number       INTEGER NOT NULL,
    submitted_by TEXT    NOT NULL,
    side         TEXT    NOT NULL,
    kind         TEXT    NOT NULL,
    title        TEXT    NOT NULL,
    content      TEXT    NOT NULL,
    weight       INTEGER NOT NULL,
    day          INTEGER NOT NULL,
    PRIMARY KEY (case_id, number)
);

CREATE TABLE case_statement (
    case_id  TEXT    NOT NULL,
    position INTEGER NOT NULL,
    speaker  TEXT    NOT NULL,
    role     TEXT    NOT NULL,
    text     TEXT    NOT NULL,
    tendency INTEGER NOT NULL,
    day      INTEGER NOT NULL,
    PRIMARY KEY (case_id, position)
);

CREATE TABLE court_staff (
    court_id TEXT    NOT NULL,
    role     TEXT    NOT NULL,
    position INTEGER NOT NULL,
    actor    TEXT    NOT NULL,
    PRIMARY KEY (court_id, role, position)
);

CREATE TABLE exile (
    polity_id TEXT    NOT NULL,
    actor     TEXT    NOT NULL,
    until     INTEGER NOT NULL,
    PRIMARY KEY (polity_id, actor)
);
