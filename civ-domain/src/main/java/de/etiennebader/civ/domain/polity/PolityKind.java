package de.etiennebader.civ.domain.polity;

/**
 * Grundart einer Polity: Gebietskörperschaften, Bünde, Parteien, Firmen und Zeitungen.
 */
public enum PolityKind {
    /** Gemeinde mit eigenem Gebiet und Gemeindehaus. */
    TERRITORIAL,
    /** Bund aus Gemeinden oder anderen Bünden, z. B. Kreis, Land, Nation; ohne eigenes Gebiet. */
    FEDERATION,
    /** Partei: Mitglieder sind Personen überall, mit politischer Richtung und Vorsitz; ohne Gebiet und Recht. */
    PARTY,
    /** Firma: Konto, Leitung (Geschäftsführung), Aktien an der Börse, Angestellte über Arbeitsverträge. */
    COMPANY,
    /** Zeitung: Redaktion als Mitglieder, Chefredaktion als Leitung, Artikel in der Presse. */
    NEWSPAPER
}
