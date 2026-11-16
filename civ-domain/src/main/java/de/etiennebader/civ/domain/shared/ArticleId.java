package de.etiennebader.civ.domain.shared;

import java.util.Objects;
import java.util.UUID;

public record ArticleId(UUID value) {

    public ArticleId {
        Objects.requireNonNull(value, "value");
    }

    public static ArticleId generate(IdGenerator ids) {
        return new ArticleId(ids.next());
    }
}
