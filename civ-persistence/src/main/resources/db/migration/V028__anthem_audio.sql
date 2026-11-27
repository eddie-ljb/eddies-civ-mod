-- Hochgeladene Aufnahme (OGG) zur Hymne einer Polity: SHA-256 der Datei, leer = keine Aufnahme.
-- Die Bytes liegen auf der Platte unter dem Hymnen-Ordner, benannt nach dem Hash.

ALTER TABLE polity_anthem ADD COLUMN audio TEXT NOT NULL DEFAULT '';
