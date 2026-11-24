package de.etiennebader.civ.server.press;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.KnownPlayer;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.media.ArticleAnalysis;
import de.etiennebader.civ.application.media.PressUseCases;
import de.etiennebader.civ.application.media.VillagerEditors;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.media.Article;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.VillagerIdentity;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Zeitungen.
 *
 * <ul>
 *   <li>{@code /civ zeitung gruenden <Name>}, {@code liste}, {@code aufloesen <Zeitung>}</li>
 *   <li>{@code /civ zeitung redakteur <Zeitung> <Spieler>} bzw. {@code dorfbewohner} (nächster),
 *       {@code entlassen <Zeitung> <Spieler>}</li>
 *   <li>{@code /civ zeitung schreiben <Zeitung> <Titel> | <Text>} oder mit Buch und Feder in der Hand
 *       {@code veroeffentlichen <Zeitung>}</li>
 *   <li>{@code /civ zeitung lesen [Zeitung]} gibt die aktuelle Ausgabe als Buch, {@code artikel <Nr>} zeigt einen</li>
 * </ul>
 */
public final class PressService {

    /** So viele Artikel stehen in einer Ausgabe. */
    public static final int EDITION_SIZE = 12;

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final CurrencyId currency;
    private final Texts texts;
    private final Announcements announcements;
    private final VillagerEditors villagerEditors;

    public PressService(CivApplication app, Calendar calendar, PlayerDirectory players, CurrencyId currency,
                        Texts texts, Announcements announcements) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.villagerEditors = new VillagerEditors(app::queries, app.press(), texts::actor);
    }

    /** {@code /civ zeitung gruenden <Name>} */
    public Reply found(Caller caller, String name) {
        return attempt(() -> {
            app.companies().found(caller.player(), PolityName.of(name.strip()), PolityKind.NEWSPAPER, currency,
                    calendar.today());
            return Reply.success(Message.of(MessageKey.NEWSPAPER_FOUNDED, name.strip()));
        });
    }

    /** {@code /civ zeitung redakteur <Zeitung> <Spieler>} oder ein Dorfbewohner. */
    public Reply addEditor(Caller caller, String paperName, ActorRef editor) {
        return withPaper(paperName, paper -> attempt(() -> {
            app.companies().admit(caller.player(), paper.id(), PolityKind.NEWSPAPER, editor, calendar.today());
            return Reply.success(Message.of(MessageKey.ORGANISATION_ADMITTED, texts.actor(editor),
                    paper.name().value()));
        }));
    }

    public Reply addEditor(Caller caller, String paperName, String playerName) {
        return players.findByName(playerName.strip()).map(player -> addEditor(caller, paperName, player))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, playerName.strip())));
    }

    /** {@code /civ zeitung entlassen <Zeitung> <Spieler>} */
    public Reply removeEditor(Caller caller, String paperName, String playerName) {
        Optional<? extends ActorRef> editor = players.findByName(playerName.strip());
        if (editor.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, playerName.strip()));
        }
        return withPaper(paperName, paper -> attempt(() ->
                app.companies().dismiss(caller.player(), paper.id(), PolityKind.NEWSPAPER, editor.get(),
                        calendar.today())
                        ? Reply.success(Message.of(MessageKey.ORGANISATION_DISMISSED, playerName.strip(),
                        paper.name().value()))
                        : Reply.failure(Message.of(MessageKey.ORGANISATION_NOT_MEMBER, playerName.strip()))));
    }

    /** {@code /civ zeitung aufloesen <Zeitung>} */
    public Reply dissolve(Caller caller, String paperName) {
        return withPaper(paperName, paper -> attempt(() -> {
            app.companies().dissolve(caller.player(), paper.id(), calendar.today());
            return Reply.success(Message.of(MessageKey.ORGANISATION_DISSOLVED, paper.name().value()));
        }));
    }

    /** {@code /civ zeitung schreiben <Zeitung> <Titel> | <Text>} */
    public Reply write(Caller caller, String paperName, String titleAndBody) {
        String[] parts = titleAndBody.split("\\|", 2);
        return publish(caller.player(), paperName, parts[0], parts.length > 1 ? parts[1] : "");
    }

    /** Veröffentlicht einen Artikel, z. B. aus einem Buch oder von der Webseite; Gegenstand und Tendenz erkennt der Mod. */
    public Reply publish(ActorRef author, String paperName, String title, String body) {
        String cleanTitle = title.strip();
        String cleanBody = body.strip();
        if (cleanTitle.isEmpty() || cleanTitle.length() > Article.MAX_TITLE || cleanBody.length() > Article.MAX_BODY) {
            return Reply.failure(Message.of(MessageKey.ARTICLE_INVALID, Article.MAX_TITLE, Article.MAX_BODY));
        }
        return withPaper(paperName, paper -> attempt(() -> {
            ArticleAnalysis analysis = analyse(cleanTitle, cleanBody);
            Article article = app.press().publish(author, paper.id(), cleanTitle, cleanBody, analysis,
                    calendar.today());
            headline(paper, article);
            return Reply.success(Message.of(MessageKey.ARTICLE_PUBLISHED, article.number(), paper.name().value(),
                    article.towns().size(), article.readers()),
                    Message.of(MessageKey.ARTICLE_ANALYSIS, article.subject().map(texts::actor).orElse("–"),
                            ArticleAnalysis.tendencyWord(article.tendency()),
                            PressUseCases.topicName(article.topic())));
        }));
    }

    /** Wertet einen Text aus, ohne ihn zu veröffentlichen, z. B. als Vorschau auf der Webseite. */
    public ArticleAnalysis analyse(String title, String body) {
        Map<String, ActorRef> names = new LinkedHashMap<>();
        for (KnownPlayer player : players.all()) {
            names.put(player.name(), player.player());
        }
        return app.press().analyse(title, body, names);
    }

    private void headline(PolityView paper, Article article) {
        Message message = Message.of(MessageKey.PRESS_HEADLINE, paper.name().value(), article.title(),
                article.number());
        for (PolityId town : article.towns()) {
            announcements.add(Announcement.toTown(town, message));
        }
    }

    /** {@code /civ zeitung liste} */
    public Reply list() {
        List<NewspaperView> papers = newspapers();
        if (papers.isEmpty()) {
            return Reply.success(Message.of(MessageKey.NEWSPAPERS_NONE));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.NEWSPAPERS_HEADER, papers.size()));
        for (NewspaperView paper : papers) {
            lines.add(Message.of(MessageKey.NEWSPAPER_ENTRY, paper.name(), paper.head(), paper.editors().size(),
                    String.join(", ", paper.towns()), paper.articles()));
        }
        return Reply.success(lines);
    }

    /** {@code /civ zeitung artikel <Nr>} */
    public Reply show(int number) {
        Optional<Article> article = queries.press().byNumber(number);
        if (article.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.ARTICLE_UNKNOWN, number));
        }
        ArticleView view = view(article.get());
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.ARTICLE_HEADER, view.newspaper(), view.title(), view.author(), view.day()));
        for (String line : view.body().split("\n")) {
            lines.add(Message.of(MessageKey.LETTER_LINE, line));
        }
        lines.add(Message.of(MessageKey.ARTICLE_ANALYSIS, view.subject(), view.tendencyWord(), view.topic()));
        return Reply.success(lines);
    }

    /**
     * Die aktuelle Ausgabe als Buchseiten: Titelseite, dann je Artikel eine Seite.
     *
     * @param paperName leer für alle Zeitungen gemischt
     */
    public Optional<Edition> edition(Optional<String> paperName) {
        Optional<PolityView> paper = paperName.filter(name -> !name.isBlank()).flatMap(this::paper);
        if (paperName.filter(name -> !name.isBlank()).isPresent() && paper.isEmpty()) {
            return Optional.empty();
        }
        List<Article> articles = new ArrayList<>(paper.map(found -> queries.press().articlesOf(found.id()))
                .orElseGet(() -> queries.press().articles()));
        if (articles.isEmpty()) {
            return Optional.empty();
        }
        List<Article> latest = articles.reversed().subList(0, Math.min(EDITION_SIZE, articles.size()));
        String title = paper.map(found -> found.name().value()).orElse("Presseschau");
        List<String> pages = new ArrayList<>();
        StringBuilder front = new StringBuilder(title).append("\n")
                .append("Ausgabe zum Tag ").append(calendar.today().value()).append("\n\n");
        for (Article article : latest) {
            front.append("• ").append(article.title()).append("\n");
        }
        pages.add(front.toString());
        for (Article article : latest) {
            ArticleView view = view(article);
            pages.add(view.title() + "\n" + view.newspaper() + ", " + view.author() + "\n\n" + view.body());
        }
        return Optional.of(new Edition(title, paper.flatMap(PolityView::mayor).map(texts::actor).orElse(title),
                pages));
    }

    /** Eine Ausgabe als Buch. */
    public record Edition(String title, String author, List<String> pages) {
    }

    /** Eine Zeitung je so vielen Dorfbewohnern (mindestens eine) darf es durch Dorfbewohner geben. */
    static final int VILLAGERS_PER_PAPER = 12;
    /** Höchstens so viele Mitbürger holt ein Dorfbewohner als Gründer in seine Redaktion. */
    static final int MAX_FOUNDING_EDITORS = 2;
    /** Tageswahrscheinlichkeit einer Zeitungsgründung bei vollem Ehrgeiz. */
    static final double FOUNDING_CHANCE = 0.10;
    private static final String[] PAPER_TITLES = {"Anzeiger", "Bote", "Kurier", "Tagblatt", "Rundschau", "Chronik",
            "Zeitung", "Echo"};

    /**
     * Ehrgeizige Dorfbewohner gründen Zeitungen: höchstens eine je Tag und solange es weniger Zeitungen als ein
     * {@value #VILLAGERS_PER_PAPER}-tel der Dorfbewohner gibt. Der Gründer leitet die Zeitung und holt bis zu
     * {@value #MAX_FOUNDING_EDITORS} Mitbürger seiner Gemeinde in die Redaktion; geschrieben wird dann wie sonst auch
     * ({@link VillagerEditors}).
     *
     * @return ob heute eine Zeitung gegründet wurde
     */
    public boolean villagersFound(Day day) {
        List<PolityView> towns = queries.polities().stream().filter(town -> town.kind() == PolityKind.TERRITORIAL)
                .sorted(java.util.Comparator.comparing(town -> town.id().value())).toList();
        long villagers = towns.stream().mapToLong(town -> town.members().stream()
                .filter(member -> member.member() instanceof PersonRef).count()).sum();
        if (queries.newspapers().size() >= Math.max(1, villagers / VILLAGERS_PER_PAPER)) {
            return false;
        }
        List<PolityView> papers = queries.newspapers();
        for (PolityView town : towns) {
            List<PersonRef> residents = town.members().stream().map(Membership::member)
                    .filter(PersonRef.class::isInstance).map(PersonRef.class::cast)
                    .sorted(java.util.Comparator.comparing(PersonRef::uuid)).toList();
            for (PersonRef villager : residents) {
                double drive = VillagerAmbitions.drive(villager) / 100.0;
                if (!VillagerAmbitions.isAmbitious(villager)
                        || papers.stream().anyMatch(paper -> paper.holds(villager, Office.MAYOR))
                        || VillagerAmbitions.roll(villager, day, 0x91) >= FOUNDING_CHANCE * drive) {
                    continue;
                }
                if (found(town, villager, residents, papers, day)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean found(PolityView town, PersonRef founder, List<PersonRef> residents, List<PolityView> papers,
                          Day day) {
        String name = de.etiennebader.civ.application.population.FoundingNames.newspaper(founder,
                town.name().value(), candidate -> paper(candidate).isPresent()
                        || queries.polityNamed(candidate).isPresent());
        try {
            PolityId id = app.companies().found(founder, PolityName.of(name), PolityKind.NEWSPAPER, currency, day);
            int editors = 0;
            for (PersonRef resident : residents) {
                if (editors >= MAX_FOUNDING_EDITORS) {
                    break;
                }
                if (resident.equals(founder) || papers.stream().anyMatch(paper -> paper.isMember(resident))) {
                    continue;
                }
                try {
                    app.companies().admit(founder, id, PolityKind.NEWSPAPER, resident, day);
                    editors++;
                } catch (DomainException ignored) {
                    // dann schreibt der Gründer eben allein weiter
                }
            }
            announcements.add(Announcement.toTown(town.id(), Message.of(MessageKey.VILLAGER_NEWSPAPER_FOUNDED, name,
                    VillagerIdentity.of(founder).fullName(), town.name().value())));
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    /** Tagesende: Dorfbewohner-Redakteure schreiben; neue Artikel erscheinen in den Gemeinden. */
    public List<Article> endOfDay(Day newDay) {
        app.pressInfluence().today(newDay);
        List<Article> written = villagerEditors.execute(newDay);
        for (Article article : written) {
            queries.polity(article.newspaper()).ifPresent(paper -> headline(paper, article));
        }
        return written;
    }

    // ---- Ansichten ----

    public List<NewspaperView> newspapers() {
        List<NewspaperView> views = new ArrayList<>();
        for (PolityView paper : queries.newspapers()) {
            List<String> editors = paper.members().stream().map(Membership::member).map(texts::actor).toList();
            int villagers = (int) paper.members().stream().filter(member -> member.member() instanceof PersonRef).count();
            List<String> towns = app.press().distribution(paper).stream()
                    .map(id -> queries.polity(id).map(town -> town.name().value()).orElse("?")).toList();
            List<Article> articles = queries.press().articlesOf(paper.id());
            views.add(new NewspaperView(paper.id().value().toString(), paper.name().value(),
                    paper.mayor().map(texts::actor).orElse("–"), editors, villagers, towns, articles.size(),
                    articles.stream().mapToInt(Article::readers).sum(), paper.foundedOn().value()));
        }
        return views;
    }

    /** Die neuesten Artikel, neueste zuerst. */
    public List<ArticleView> latest(int limit) {
        List<Article> articles = queries.press().articles();
        return articles.reversed().stream().limit(limit).map(this::view).toList();
    }

    public ArticleView view(Article article) {
        String paper = queries.polity(article.newspaper()).map(found -> found.name().value()).orElse("?");
        return new ArticleView(article.number(), article.newspaper().value().toString(), paper,
                texts.actor(article.author()), article.title(), article.body(),
                PressUseCases.topicName(article.topic()), article.subject().map(texts::actor).orElse(""),
                article.tendency(), ArticleAnalysis.tendencyWord(article.tendency()), article.day().value(),
                article.towns().stream().map(id -> queries.polity(id).map(town -> town.name().value()).orElse("?"))
                        .toList(), article.readers());
    }

    /** Zeitungen, deren Redaktion der Spieler angehört. */
    public List<PolityView> papersOf(ActorRef editor) {
        return queries.newspapers().stream().filter(paper -> paper.isMember(editor)).toList();
    }

    /** Zeitungen, die der Spieler leitet. */
    public List<PolityView> led(ActorRef head) {
        return queries.newspapers().stream().filter(paper -> paper.holds(head, Office.MAYOR)).toList();
    }

    public Optional<PolityView> paper(String name) {
        return queries.polityNamed(name.strip()).filter(polity -> polity.kind() == PolityKind.NEWSPAPER);
    }

    private Reply withPaper(String name, Function<PolityView, Reply> action) {
        return paper(name).map(action)
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.NEWSPAPER_UNKNOWN, name.strip())));
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}
