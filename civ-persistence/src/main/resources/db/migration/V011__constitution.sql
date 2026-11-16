-- Verfassung: Tag des Ausrufens (NULL = Entwurf) und Präambel.

ALTER TABLE polity ADD COLUMN constitution_proclaimed_on INTEGER;
ALTER TABLE polity ADD COLUMN preamble TEXT NOT NULL DEFAULT '';
