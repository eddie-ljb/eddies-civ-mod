package de.etiennebader.civ.domain.polity;

/**
 * Grundart einer Polity. In Phase 0 gibt es nur Gebietskörperschaften; Parteien, Firmen,
 * Gewerkschaften, Medienhäuser und Religionen folgen später.
 */
public enum PolityKind {
    /** Gemeinde mit eigenem Gebiet und Gemeindehaus. */
    TERRITORIAL,
    /** Bund aus Gemeinden oder anderen Bünden, z. B. Kreis, Land, Nation; ohne eigenes Gebiet. */
    FEDERATION,
    /** Partei: Mitglieder sind Personen überall, mit politischer Richtung und Vorsitz; ohne Gebiet und Recht. */
    PARTY
}
