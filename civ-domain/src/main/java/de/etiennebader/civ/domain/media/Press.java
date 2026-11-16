package de.etiennebader.civ.domain.media;

import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Alle Artikel aller Zeitungen, höchstens die neuesten {@value #KEPT_ARTICLES}.
 */
public final class Press extends AggregateRoot<PressEvent> implements PressView {

    public static final int KEPT_ARTICLES = 500;

    private final List<Article> articles = new ArrayList<>();
    private int lastNumber;

    private Press() {
    }

    public static Press empty() {
        return new Press();
    }

    public static Press restore(Collection<Article> stored) {
        Press press = new Press();
        stored.stream().sorted(Comparator.comparingInt(Article::number)).forEach(press.articles::add);
        press.lastNumber = press.articles.stream().mapToInt(Article::number).max().orElse(0);
        return press;
    }

    /** Veröffentlicht einen Artikel; die Nummer vergibt die Presse. */
    public Article publish(Article draft) {
        Objects.requireNonNull(draft, "draft");
        if (articles.stream().anyMatch(article -> article.id().equals(draft.id()))) {
            throw new IllegalArgumentException("Schon erschienen: " + draft.id());
        }
        Article article = draft.numbered(++lastNumber);
        articles.add(article);
        while (articles.size() > KEPT_ARTICLES) {
            articles.removeFirst();
        }
        raise(new ArticlePublished(article.id(), article.newspaper(), article.number(), article.day()));
        return article;
    }

    /** Entfernt alle Artikel einer aufgelösten Zeitung. */
    public void forget(PolityId newspaper, Day day) {
        if (articles.removeIf(article -> article.newspaper().equals(newspaper))) {
            raise(new ArticlesRemoved(newspaper, day));
        }
    }

    @Override
    public List<Article> articles() {
        return List.copyOf(articles);
    }
}
