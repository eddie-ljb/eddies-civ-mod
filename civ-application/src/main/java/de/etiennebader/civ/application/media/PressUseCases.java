package de.etiennebader.civ.application.media;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.media.Article;
import de.etiennebader.civ.domain.media.Press;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.ArticleId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Artikel veröffentlichen. Schreiben darf die Redaktion (Mitglieder der Zeitung). Die Ausgabe erscheint in den
 * Gemeinden der Redaktion; wer dort als Dorfbewohner wohnt, liest sie vielleicht.
 */
public final class PressUseCases {

    /** So viele Artikel darf eine Zeitung am Tag bringen. */
    public static final int ARTICLES_PER_DAY = 5;

    /** Der Akteur gehört nicht zur Redaktion. */
    public static final class NotAnEditorException extends DomainException {

        public NotAnEditorException(Object newspaper) {
            super(ErrorCode.NOT_AN_EDITOR, newspaper);
        }
    }

    /** Die Zeitung hat heute schon genug gebracht. */
    public static final class PressLimitException extends DomainException {

        public PressLimitException(int limit) {
            super(ErrorCode.PRESS_LIMIT, limit);
        }
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public PressUseCases(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    private WorldQueries queries() {
        return new WorldQueries(aggregates.repositories());
    }

    /**
     * Wertet einen Text aus, ohne ihn zu veröffentlichen.
     *
     * @param knownPlayers Namen bekannter Spieler
     */
    public ArticleAnalysis analyse(String title, String body, Map<String, ? extends ActorRef> knownPlayers) {
        Map<String, ActorRef> names = new LinkedHashMap<>(knownPlayers);
        for (PolityView polity : queries().polities()) {
            names.put(polity.name().value(), polity.asActor());
        }
        return ArticleAnalysis.of(title, body, names);
    }

    /**
     * Veröffentlicht einen Artikel.
     *
     * @param analysis Gegenstand, Tendenz und Thema, z. B. aus {@link #analyse}
     * @throws NotAnEditorException wenn der Autor nicht zur Redaktion gehört
     * @throws PressLimitException  wenn die Zeitung heute schon {@value #ARTICLES_PER_DAY} Artikel gebracht hat
     */
    public Article publish(ActorRef author, PolityId newspaper, String title, String body, ArticleAnalysis analysis,
                           Day day) {
        Polity paper = aggregates.polity(newspaper);
        if (paper.kind() != PolityKind.NEWSPAPER || !paper.isMember(author)) {
            throw new NotAnEditorException(paper.name().value());
        }
        Press press = aggregates.press();
        long today = press.articlesOf(newspaper).stream().filter(article -> article.day().equals(day)).count();
        if (today >= ARTICLES_PER_DAY) {
            throw new PressLimitException(ARTICLES_PER_DAY);
        }
        List<PolityId> towns = distribution(paper).stream()
                .filter(town -> !censored(aggregates.polity(town), analysis)).toList();
        int readers = 0;
        ArticleId id = ArticleId.generate(ids);
        Article draft = new Article(id, 0, newspaper, author, title, body, analysis.topic(), analysis.subject(),
                analysis.tendency(), day, towns, 0);
        for (PolityId town : towns) {
            for (Membership member : aggregates.polity(town).members()) {
                if (member.member() instanceof PersonRef villager && PressInfluence.reads(villager, draft)) {
                    readers++;
                }
            }
        }
        Article article = press.publish(new Article(id, 0, newspaper, author, title, body, analysis.topic(),
                analysis.subject(), analysis.tendency(), day, towns, readers));
        aggregates.commit(press);
        return article;
    }

    /**
     * Ohne Pressefreiheit ({@code press_freedom} aus) erscheint in einer Gemeinde kein Artikel, der sie selbst oder
     * einen ihrer Amtsträger kritisiert.
     */
    static boolean censored(Polity town, ArticleAnalysis analysis) {
        if (town.laws().valueOf(de.etiennebader.civ.domain.law.StandardParameters.PRESS_FREEDOM)
                || analysis.tendency() >= 0 || analysis.subject().isEmpty()) {
            return false;
        }
        ActorRef subject = analysis.subject().get();
        return subject.equals(town.asActor()) || java.util.Arrays.stream(de.etiennebader.civ.domain.polity.Office.values())
                .anyMatch(office -> town.holds(subject, office));
    }

    /** Wo eine Zeitung erscheint: in den Gemeinden ihrer Redaktion. */
    public List<PolityId> distribution(PolityView paper) {
        Set<PolityId> towns = new LinkedHashSet<>();
        WorldQueries queries = queries();
        for (Membership member : paper.members()) {
            queries.municipalityOf(member.member()).ifPresent(town -> towns.add(town.id()));
        }
        return new ArrayList<>(towns);
    }

    /** Name für ein Thema in Artikeln. */
    public static String topicName(Platform topic) {
        return switch (topic) {
            case ORDER -> "Sicherheit und Ordnung";
            case FREEDOM -> "Freiheit";
            case LOW_TAXES -> "niedrige Steuern";
            case INVESTMENT -> "Ausbau";
            case ENVIRONMENT -> "Umwelt und Natur";
            case WELFARE -> "Soziales";
            case ECONOMY -> "Wirtschaft und Handel";
            case EDUCATION -> "Bildung";
            case TRADITION -> "Tradition und Heimat";
            case PROGRESS -> "Fortschritt und Technik";
            case PEACE -> "Frieden und Diplomatie";
            case EXPANSION -> "Stärke und Expansion";
            case DEMOCRACY -> "Bürgerbeteiligung";
            case AGRICULTURE -> "Landwirtschaft";
            case AUTONOMY -> "Selbstbestimmung";
            case NONE -> "Allgemeines";
        };
    }
}
