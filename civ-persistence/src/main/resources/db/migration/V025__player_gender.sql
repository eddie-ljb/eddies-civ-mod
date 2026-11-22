-- Geschlecht der Spieler (#20): FEMALE, MALE oder DIVERSE; NULL, solange der Spieler keines angegeben hat.

ALTER TABLE known_player ADD COLUMN gender TEXT CHECK (gender IN ('FEMALE', 'MALE', 'DIVERSE'));
