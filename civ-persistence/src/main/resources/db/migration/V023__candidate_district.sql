-- Wahlkreis je Kandidatur (#47): bei Wahlkreiswahlen an genau einen Bezirk gebunden, sonst NULL.

ALTER TABLE election_candidate ADD COLUMN district TEXT;
