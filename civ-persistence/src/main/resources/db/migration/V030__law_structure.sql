-- Gesetze mit Teilen und Paragraphen, jeweils mit eigenem Namen (#83).
-- Absätze, Sätze und Aufzählungen ergeben sich aus dem Text und brauchen keine Spalten.

ALTER TABLE statute ADD COLUMN law_name TEXT NOT NULL DEFAULT '';
ALTER TABLE statute ADD COLUMN part_name TEXT NOT NULL DEFAULT '';
ALTER TABLE proposal ADD COLUMN law_name TEXT NOT NULL DEFAULT '';
ALTER TABLE proposal ADD COLUMN part_name TEXT NOT NULL DEFAULT '';
