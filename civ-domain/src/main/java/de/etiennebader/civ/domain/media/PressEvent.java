package de.etiennebader.civ.domain.media;

import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.PolityId;

/** Ereignisse der Presse. */
public sealed interface PressEvent extends DomainEvent permits ArticlePublished, ArticlesRemoved {

    PolityId newspaper();
}
