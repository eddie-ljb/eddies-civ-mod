-- Wahlprogramm je Kandidatur.

ALTER TABLE election_candidate ADD COLUMN platform TEXT NOT NULL DEFAULT 'NONE';
