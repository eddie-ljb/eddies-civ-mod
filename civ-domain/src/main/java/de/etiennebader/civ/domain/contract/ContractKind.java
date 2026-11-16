package de.etiennebader.civ.domain.contract;

/**
 * Art eines Vertrags.
 */
public enum ContractKind {
    /** Miete eines Grundstücks: der Mieter zahlt täglich, hinterlegt eine Kaution und darf das Grundstück nutzen. */
    LEASE,
    /** Arbeit: der Arbeitgeber zahlt täglich Lohn; ein Dorfbewohner arbeitet dafür für ihn statt für die Gemeinde. */
    EMPLOYMENT,
    /** Freier Vertrag: Text, auf Wunsch mit einmaliger und täglicher Zahlung. */
    CUSTOM,
    /** Bündnis zwischen zwei Gemeinden oder Bünden: Bürger der einen gelten in der anderen nicht als Fremde. */
    ALLIANCE
}
