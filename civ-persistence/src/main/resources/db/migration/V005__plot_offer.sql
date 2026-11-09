-- Verkaufsangebote für Grundstücke: Preis in kleinsten Einheiten und Währung, beides NULL ohne Angebot.

ALTER TABLE plot ADD COLUMN asking_price INTEGER CHECK (asking_price IS NULL OR asking_price >= 0);
ALTER TABLE plot ADD COLUMN asking_currency TEXT;
