package de.etiennebader.civ.domain.media;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Optional;

/** Lesende Sicht auf alle Artikel. */
public interface PressView {

    /** Alle Artikel, älteste zuerst. */
    List<Article> articles();

    default List<Article> articlesOf(PolityId newspaper) {
        return articles().stream().filter(article -> article.newspaper().equals(newspaper)).toList();
    }

    default Optional<Article> byNumber(int number) {
        return articles().stream().filter(article -> article.number() == number).findFirst();
    }

    /** Artikel, die heute noch wirken. */
    default List<Article> fresh(Day today, int days) {
        return articles().stream().filter(article -> article.freshOn(today, days)).toList();
    }
}
