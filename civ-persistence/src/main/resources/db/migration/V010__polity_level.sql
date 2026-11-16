-- Ebene einer Polity: 'gemeinde' für Gemeinden, sonst z. B. 'kreis', 'land', 'nation'.

ALTER TABLE polity ADD COLUMN level TEXT NOT NULL DEFAULT 'gemeinde';
