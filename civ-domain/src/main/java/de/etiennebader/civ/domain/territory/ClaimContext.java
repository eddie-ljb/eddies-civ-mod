package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Alles, was eine {@link ClaimPolicy} für ihre Entscheidung braucht.
 *
 * <p>Das Paket {@code territory} kennt weder Mitglieder noch Recht einer Polity. Der Aufrufer
 * füllt {@code memberCount} und {@code maxOutposts} deshalb aus Polity und geltendem Recht.
 */
public record ClaimContext(PolityId claimant, ClaimType type, ChunkCoord target, int memberCount, int maxOutposts) {

    public ClaimContext {
        Objects.requireNonNull(claimant, "claimant");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        if (memberCount < 0) {
            throw new IllegalArgumentException("memberCount darf nicht negativ sein: " + memberCount);
        }
        if (maxOutposts < 0) {
            throw new IllegalArgumentException("maxOutposts darf nicht negativ sein: " + maxOutposts);
        }
    }
}
