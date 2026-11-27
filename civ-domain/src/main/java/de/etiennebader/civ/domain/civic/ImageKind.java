package de.etiennebader.civ.domain.civic;

/**
 * Art eines selbst hochgeladenen Bildes einer Polity.
 */
public enum ImageKind {
    /** Wappen als eigenes Bild; schwebt über dem Gemeindehaus und ziert Wappen-Blöcke. */
    WAPPEN,
    /** Flagge als eigenes Bild statt Banner-Muster; ziert Flaggen-Blöcke. */
    FLAGGE,
    /** Wahlplakat im Wahlkampf. */
    WAHLPLAKAT,
    /** Leuchtreklame, z. B. für Unternehmen. */
    LEUCHTREKLAME
}
