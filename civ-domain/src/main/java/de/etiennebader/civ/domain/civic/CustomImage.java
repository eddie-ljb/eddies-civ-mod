package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein über die Webseite hochgeladenes Bild (Wappen, Wahlplakat, Leuchtreklame) einer Polity.
 * Die Bilddatei selbst liegt serverseitig auf der Platte, benannt nach {@link #hash()}; hier steht
 * nur die Metadaten-Zeile, damit das Register klein bleibt.
 *
 * @param id         eindeutige Kennung dieses Uploads
 * @param polity     Gemeinde, Land, Nation, Bund oder Unternehmen, dem das Bild gehört
 * @param kind       Art des Bildes
 * @param hash       SHA-256 der PNG-Bytes (hex), zugleich Dateiname auf der Platte
 * @param width      Breite in Pixeln
 * @param height     Höhe in Pixeln
 * @param uploadedAt Tag des Hochladens
 */
public record CustomImage(String id, PolityId polity, ImageKind kind, String hash, int width, int height,
                          Day uploadedAt) {

    /** Größte erlaubte Kantenlänge; größere Bilder würden ohnehin nur verkleinert als Karte dargestellt. */
    public static final int MAX_DIMENSION = 128;

    /** Höchstens so viele Bilder derselben Art je Polity (Wahlplakate, Leuchtreklamen; Wappen und Flagge ersetzen sich selbst). */
    public static final int MAX_PER_KIND = 10;

    public CustomImage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(uploadedAt, "uploadedAt");
        if (width <= 0 || width > MAX_DIMENSION || height <= 0 || height > MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "Bildgröße muss zwischen 1x1 und " + MAX_DIMENSION + "x" + MAX_DIMENSION + " liegen: "
                            + width + "x" + height);
        }
    }
}
