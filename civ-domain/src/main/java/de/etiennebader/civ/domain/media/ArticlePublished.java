package de.etiennebader.civ.domain.media;

import de.etiennebader.civ.domain.shared.ArticleId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/** Ein Artikel ist erschienen. */
public record ArticlePublished(ArticleId article, PolityId newspaper, int number, Day occurredOn)
        implements PressEvent {

    public ArticlePublished {
        Objects.requireNonNull(article, "article");
        Objects.requireNonNull(newspaper, "newspaper");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
