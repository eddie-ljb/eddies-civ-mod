-- Etappe 2: Sitz einer Gemeinde (Standort des Gemeindehauses). Leer bei Gemeinden aus Etappe 1.

ALTER TABLE polity ADD COLUMN seat_dimension TEXT;
ALTER TABLE polity ADD COLUMN seat_x INTEGER;
ALTER TABLE polity ADD COLUMN seat_y INTEGER;
ALTER TABLE polity ADD COLUMN seat_z INTEGER;
