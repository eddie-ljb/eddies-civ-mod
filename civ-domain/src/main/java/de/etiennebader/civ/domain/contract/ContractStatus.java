package de.etiennebader.civ.domain.contract;

/**
 * Stand eines Vertrags.
 */
public enum ContractStatus {
    /** Angeboten, noch nicht unterschrieben. */
    OFFERED,
    /** Beide haben unterschrieben; er gilt. */
    ACTIVE,
    /** Beendet: regulär, gekündigt oder gebrochen. */
    ENDED,
    /** Abgelehnt oder zurückgezogen, bevor er galt. */
    DECLINED
}
