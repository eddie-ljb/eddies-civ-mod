-- Parteien: Richtung auf zwei Achsen und Wappen (Item-ID); NULL bei anderen Polities.

ALTER TABLE polity ADD COLUMN party_order INTEGER;
ALTER TABLE polity ADD COLUMN party_economy INTEGER;
ALTER TABLE polity ADD COLUMN party_emblem TEXT;
