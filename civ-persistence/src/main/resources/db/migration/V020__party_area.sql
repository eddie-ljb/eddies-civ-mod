-- Geltungsgebiet einer Partei: Polity-ID der Ebene, auf die sie beschränkt ist; NULL = überall wählbar.

ALTER TABLE polity ADD COLUMN party_area TEXT;
