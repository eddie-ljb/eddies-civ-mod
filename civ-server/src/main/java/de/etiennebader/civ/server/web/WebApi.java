package de.etiennebader.civ.server.web;

import de.etiennebader.civ.application.media.ArticleAnalysis;
import de.etiennebader.civ.application.media.PressUseCases;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.domain.market.Quote;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.campaign.CampaignService;
import de.etiennebader.civ.server.campaign.PollView;
import de.etiennebader.civ.server.campaign.ResultView;
import de.etiennebader.civ.server.civil.ContractView;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.community.LetterView;
import de.etiennebader.civ.server.company.HoldingView;
import de.etiennebader.civ.server.company.OrderView;
import de.etiennebader.civ.server.market.ItemTraits;
import de.etiennebader.civ.server.market.MarketOffer;
import de.etiennebader.civ.server.office.ElectionView;
import de.etiennebader.civ.server.office.OfficeView;
import de.etiennebader.civ.server.parliament.ParliamentView;
import de.etiennebader.civ.server.press.ArticleView;
import de.etiennebader.civ.server.town.TownOverview;
import de.etiennebader.civ.server.vote.ProposalView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Die Endpunkte der Webseite.
 *
 * <ul>
 *     <li>{@code GET /} die Seite selbst</li>
 *     <li>{@code GET /api/world} Tag, Preisniveau und alle Gemeinden</li>
 *     <li>{@code GET /api/map?dim=minecraft:overworld} beanspruchte Chunks einer Dimension</li>
 *     <li>{@code GET /api/market} Lager und Preise des Servermarkts</li>
 *     <li>{@code POST /api/login} mit {@code {"code": "..."}} aus {@code /civ web}</li>
 *     <li>{@code GET /api/stats} Kennzahlen für die Startseite, {@code GET /api/streets} Straßen und Hausnummern</li>
 *     <li>{@code GET|POST /api/chat}, {@code GET|POST /api/friends}, {@code GET|POST /api/letters},
 *         {@code POST /api/letters/read}, {@code GET|POST /api/contracts}: nur angemeldet</li>
 *     <li>{@code GET /api/exchange}, {@code GET /api/exchange/book?company=}, {@code GET /api/press}; angemeldet
 *         {@code GET /api/depot}, {@code POST /api/exchange}, {@code POST /api/press}, {@code POST /api/press/preview}</li>
 *     <li>{@code GET /api/me}, {@code POST /api/transfer}, {@code POST /api/vote}, {@code POST /api/stand},
 *         {@code POST /api/elect}, {@code POST /api/logout}
 *         nur angemeldet</li>
 * </ul>
 */
public final class WebApi implements Function<HttpRequest, HttpResponse> {

    static final String SESSION_COOKIE = "civ_session";
    private static final PlayerRef ANONYMOUS = new PlayerRef(new PlayerId(new UUID(0, 0)));
    private static final BlockPosition NOWHERE = new BlockPosition(DimensionId.OVERWORLD, 0, 64, 0);
    private static final int MAX_MAP_CHUNKS = 20_000;
    private static final int MAX_OFFERS = 500;

    private final CivServer civ;
    private final GameThread game;
    private final WebSessions sessions;
    private final Function<String, Optional<ItemTraits>> items;
    private final Translator german;
    private final Translator english;
    private final byte[] page;

    public WebApi(CivServer civ, GameThread game, WebSessions sessions, Function<String, Optional<ItemTraits>> items) {
        this(civ, game, sessions, items, Translator.load("de_de"), Translator.load("en_us"));
    }

    WebApi(CivServer civ, GameThread game, WebSessions sessions, Function<String, Optional<ItemTraits>> items,
           Translator german, Translator english) {
        this.civ = Objects.requireNonNull(civ, "civ");
        this.game = Objects.requireNonNull(game, "game");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.items = Objects.requireNonNull(items, "items");
        this.german = german;
        this.english = english;
        this.page = loadPage();
    }

    @Override
    public HttpResponse apply(HttpRequest request) {
        Translator texts = request.header("accept-language").filter(lang -> lang.toLowerCase(Locale.ROOT).startsWith("en"))
                .isPresent() ? english : german;
        try {
            return switch (request.method() + " " + request.path()) {
                case "GET /", "GET /index.html" ->
                        new HttpResponse(200, "text/html; charset=utf-8", page, Map.of("Cache-Control", "no-cache"));
                case "GET /api/world" -> HttpResponse.json(200, game.call(() -> world(texts)));
                case "GET /api/map" -> HttpResponse.json(200, game.call(() ->
                        map(new DimensionId(request.query().getOrDefault("dim", DimensionId.OVERWORLD.value())))));
                case "GET /api/market" -> HttpResponse.json(200, game.call(this::market));
                case "GET /api/elections" -> HttpResponse.json(200, game.call(() -> elections(texts)));
                case "GET /api/shops" -> HttpResponse.json(200, game.call(this::shops));
                case "GET /api/stats" -> HttpResponse.json(200, game.call(this::stats));
                case "GET /api/streets" -> HttpResponse.json(200, game.call(this::streets));
                case "GET /api/chat" -> withLogin(request, login -> HttpResponse.json(200, game.call(() ->
                        chat(login, parseLong(request.query().getOrDefault("after", "0"))))));
                case "POST /api/chat" -> withLogin(request, login -> {
                    Map<String, String> body = Json.parseFlat(request.body());
                    return domain(texts, () -> game.call(() -> {
                        try {
                            civ.community().webPost(login.player(), Optional.ofNullable(body.get("to")),
                                    body.getOrDefault("text", ""));
                            return Reply.success(Message.of(MessageKey.CHAT_SENT));
                        } catch (DomainException e) {
                            return Reply.failure(civ.texts().error(e));
                        } catch (IllegalArgumentException e) {
                            return Reply.failure(Message.of(body.getOrDefault("text", "").isBlank()
                                    ? MessageKey.CHAT_EMPTY : MessageKey.UNKNOWN_RECIPIENT, body.getOrDefault("to", "")));
                        }
                    }));
                });
                case "GET /api/friends" -> withLogin(request, login -> HttpResponse.json(200, game.call(() ->
                        friends(login))));
                case "POST /api/friends" -> withLogin(request, login -> action(request, body -> game.call(() ->
                        switch (body.getOrDefault("action", "")) {
                            case "request", "accept" -> civ.community().befriend(login.player(), login.name(),
                                    body.getOrDefault("name", ""));
                            case "remove" -> civ.community().unfriend(login.player(), body.getOrDefault("name", ""));
                            default -> throw new IllegalArgumentException("Unbekannte Aktion");
                        }), texts));
                case "GET /api/letters" -> withLogin(request, login -> HttpResponse.json(200, game.call(() ->
                        letters(login))));
                case "POST /api/letters" -> withLogin(request, login -> action(request, body -> game.call(() ->
                        civ.community().sendLetter(login.player(), login.name(), body.getOrDefault("to", ""),
                                body.getOrDefault("subject", ""), body.getOrDefault("body", ""))), texts));
                case "POST /api/letters/read" -> withLogin(request, login -> {
                    Map<String, String> body = Json.parseFlat(request.body());
                    boolean read = game.call(() -> civ.community().markRead(login.player(), body.getOrDefault("id", "")));
                    return HttpResponse.json(200, Json.object().put("ok", read).build());
                });
                case "GET /api/contracts" -> withLogin(request, login -> HttpResponse.json(200, game.call(() ->
                        contracts(login, texts))));
                case "POST /api/contracts" -> withLogin(request, login -> action(request, body -> game.call(() ->
                        contractAction(login, body)), texts));
                case "GET /api/exchange" -> HttpResponse.json(200, game.call(this::exchange));
                case "GET /api/exchange/book" -> HttpResponse.json(200, game.call(() ->
                        orderBook(request.query().getOrDefault("company", ""), request.cookie(SESSION_COOKIE)
                                .flatMap(sessions::session))));
                case "GET /api/depot" -> withLogin(request, login -> HttpResponse.json(200, game.call(() ->
                        depot(login))));
                case "POST /api/exchange" -> withLogin(request, login -> action(request, body -> game.call(() ->
                        exchangeAction(login, body)), texts));
                case "GET /api/press" -> HttpResponse.json(200, game.call(this::press));
                case "POST /api/press" -> withLogin(request, login -> action(request, body -> game.call(() ->
                        civ.press().publish(login.player(), body.getOrDefault("paper", ""),
                                body.getOrDefault("title", ""), body.getOrDefault("body", ""))), texts));
                case "POST /api/press/preview" -> withLogin(request, login -> {
                    Map<String, String> body = Json.parseFlat(request.body());
                    return HttpResponse.json(200, game.call(() -> preview(body.getOrDefault("title", ""),
                            body.getOrDefault("body", ""))));
                });
                case "POST /api/login" -> login(request);
                case "POST /api/logout" -> logout(request);
                case "GET /api/me" -> withLogin(request, login -> HttpResponse.json(200, game.call(() -> me(login, texts))));
                case "POST /api/transfer" -> withLogin(request, login -> action(request, body -> game.call(() ->
                        civ.bank().transfer(caller(login), body.getOrDefault("to", ""), body.getOrDefault("amount", ""))),
                        texts));
                case "POST /api/vote" -> withLogin(request, login -> action(request, body -> {
                    int number = Integer.parseInt(body.getOrDefault("number", "0"));
                    boolean yes = Boolean.parseBoolean(body.getOrDefault("yes", "false"));
                    return game.call(() -> civ.voting().vote(caller(login), number, yes));
                }, texts));
                case "POST /api/stand" -> withLogin(request, login -> action(request, body -> {
                    Office office = Office.valueOf(body.getOrDefault("office", ""));
                    Platform platform = Platform.valueOf(body.getOrDefault("platform", "NONE"));
                    return game.call(() -> civ.offices().stand(caller(login), office, platform));
                }, texts));
                case "POST /api/elect" -> withLogin(request, login -> action(request, body -> {
                    Office office = Office.valueOf(body.getOrDefault("office", ""));
                    UUID candidate = UUID.fromString(body.getOrDefault("candidate", ""));
                    return game.call(() -> civ.offices().vote(caller(login), office, candidateOf(login, office, candidate)));
                }, texts));
                default -> request.path().startsWith("/api/")
                        ? HttpResponse.error(404, "Unbekannter Endpunkt")
                        : HttpResponse.error(404, "Nicht gefunden");
            };
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                return HttpResponse.error(400, "Ungültige Anfrage");
            }
            return HttpResponse.error(503, "Der Spielserver antwortet gerade nicht");
        } catch (IllegalArgumentException e) {
            return HttpResponse.error(400, "Ungültige Anfrage");
        }
    }

    private String world(Translator texts) {
        List<String> towns = new ArrayList<>();
        for (PolityView polity : civ.queries().polities()) {
            if (polity.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            TownOverview town = civ.towns().overview(polity, ANONYMOUS);
            towns.add(Json.object()
                    .put("id", town.id().value().toString())
                    .put("name", town.name())
                    .put("tier", texts.translate(town.tier()))
                    .put("founded", town.foundedOn())
                    .put("mayor", town.mayor())
                    .raw("members", Json.array(town.members(), Json::string))
                    .put("villagers", town.villagers())
                    .put("mood", civ.villagers().moodOf(polity).averageScore())
                    .put("atRisk", civ.villagers().moodOf(polity).atRisk())
                    .put("chunks", town.chunks())
                    .put("claimLimit", town.claimLimit())
                    .put("treasury", money(town.treasury()))
                    .put("plots", civ.plots().plotsOf(town.id(), ANONYMOUS).size())
                    .put("plotsForSale", civ.plots().plotsOf(town.id(), ANONYMOUS).stream()
                            .filter(plot -> plot.askingPrice().isPresent()).count())
                    .put("votes", polity.proposals().size())
                    .put("constitution", polity.constitution().proclaimedOn().map(day -> day.value()).orElse(0L))
                    .put("paragraphs", polity.laws().inForce().size())
                    .put("parliament", parliamentSummary(polity, texts))
                    .put("partOf", String.join(" › ", civ.queries().ancestorsOf(polity.id()).stream()
                            .map(ancestor -> ancestor.name().value()).toList()))
                    .build());
        }
        List<String> federations = new ArrayList<>();
        for (PolityView polity : civ.queries().polities()) {
            if (polity.kind() != PolityKind.FEDERATION) {
                continue;
            }
            Object level = civ.federations().levelOf(polity).displayName();
            List<String> constituents = polity.constituents().stream()
                    .map(ref -> civ.queries().polity(ref.id()).map(found -> found.name().value()).orElse("?"))
                    .toList();
            federations.add(Json.object()
                    .put("id", polity.id().value().toString())
                    .put("name", polity.name().value())
                    .put("level", level instanceof Message message ? texts.translate(message) : level.toString())
                    .put("rank", civ.federations().levelOf(polity).rank())
                    .put("head", polity.mayor().map(civ.texts()::actor).orElse("-"))
                    .raw("constituents", Json.array(constituents, Json::string))
                    .put("players", polity.members().stream().filter(m -> m.member() instanceof PlayerRef).count())
                    .put("villagers", polity.members().stream()
                            .filter(m -> m.member() instanceof PersonRef).count())
                    .put("parent", civ.queries().parentOf(polity.id()).map(parent -> parent.name().value()).orElse(""))
                    .put("founded", polity.foundedOn().value())
                    .put("constitution", polity.constitution().proclaimedOn().map(day -> day.value()).orElse(0L))
                    .put("paragraphs", polity.laws().inForce().size())
                    .put("parliament", parliamentSummary(polity, texts))
                    .build());
        }
        return Json.object()
                .put("day", civ.calendar().today().value())
                .put("priceLevel", civ.market().priceLevelPercent())
                .put("players", civ.players().all().size())
                .raw("towns", "[" + String.join(",", towns) + "]")
                .raw("federations", "[" + String.join(",", federations) + "]")
                .raw("parties", Json.array(civ.parties().parties(ANONYMOUS),
                        party -> Json.object()
                                .put("id", party.id().value().toString())
                                .put("name", party.name())
                                .put("platform", texts.translate(Message.of(TranslationKeys.platform(party.platform()))))
                                .put("order", party.order())
                                .put("economy", party.economy())
                                .put("chair", party.chair())
                                .put("players", party.players())
                                .put("villagers", party.villagers())
                                .put("founded", party.foundedOn())
                                .build()))
                .build();
    }

    /** Wahlergebnisse je Bezirk und Umfragen aller Gemeinden und Bünde. */
    private String elections(Translator texts) {
        List<String> results = new ArrayList<>();
        List<String> polls = new ArrayList<>();
        for (PolityView polity : civ.queries().polities()) {
            if (polity.kind() == PolityKind.PARTY) {
                continue;
            }
            String level = polity.kind() == PolityKind.FEDERATION
                    ? levelName(polity, texts) : texts.translate(Message.of(MessageKey.WEB_MUNICIPALITY));
            for (ResultView result : civ.campaigns().results(polity)) {
                results.add(Json.object()
                        .put("polity", polity.id().value().toString())
                        .put("polityName", result.polityName())
                        .put("level", level)
                        .put("office", result.office().name())
                        .put("officeName", texts.translate(result.officeName()))
                        .put("day", result.closedOn())
                        .raw("winners", Json.array(result.winners(), Json::string))
                        .raw("totals", Json.array(result.totals(), line -> Json.object()
                                .put("name", line.name())
                                .put("party", line.party().orElse(""))
                                .put("votes", CampaignService.votes(line.votes()))
                                .put("percent", line.percent())
                                .build()))
                        .raw("districts", Json.array(result.districts(), district -> Json.object()
                                .put("town", district.town().map(id -> id.value().toString()).orElse(""))
                                .put("name", district.name())
                                .put("leader", district.leader())
                                .put("party", district.leaderParty().orElse(""))
                                .put("key", district.leaderKey())
                                .put("votes", CampaignService.votes(district.votes()))
                                .build()))
                        .build());
            }
            for (PollView poll : civ.campaigns().polls(polity)) {
                polls.add(Json.object()
                        .put("polity", polity.id().value().toString())
                        .put("polityName", poll.polityName())
                        .put("level", level)
                        .put("officeName", texts.translate(poll.officeName()))
                        .put("closesOn", poll.closesOn())
                        .put("sample", poll.sample())
                        .put("undecided", poll.undecided())
                        .raw("entries", Json.array(poll.entries(), entry -> Json.object()
                                .put("name", entry.name())
                                .put("party", entry.party())
                                .put("percent", entry.percent())
                                .build()))
                        .raw("lists", Json.array(poll.lists(), list -> Json.object()
                                .put("name", list.name())
                                .put("percent", list.percent())
                                .put("seats", list.seats())
                                .build()))
                        .build());
            }
        }
        return Json.object()
                .raw("results", "[" + String.join(",", results) + "]")
                .raw("polls", "[" + String.join(",", polls) + "]")
                .build();
    }

    private String levelName(PolityView polity, Translator texts) {
        Object level = civ.federations().levelOf(polity).displayName();
        return level instanceof Message message ? texts.translate(message) : level.toString();
    }

    /** Sitzverteilung, z. B. „Rot 2 · Fraktionslos 1“; leer ohne Parlament. */
    private String parliamentSummary(PolityView polity, Translator texts) {
        ParliamentView view = civ.parliaments().view(polity, ANONYMOUS);
        if (!view.seated()) {
            return view.seatsByLaw() > 0
                    ? texts.translate(Message.of(MessageKey.PARLIAMENT_SEATS_PROVIDED, view.seatsByLaw())) : "";
        }
        return view.factions().stream()
                .map(faction -> faction.name()
                        .orElseGet(() -> texts.translate(Message.of(MessageKey.PARLIAMENT_INDEPENDENT)))
                        + " " + faction.members().size())
                .collect(Collectors.joining(" · "));
    }

    private String map(DimensionId dimension) {
        List<String> chunks = new ArrayList<>();
        for (PolityView polity : civ.queries().polities()) {
            for (ChunkCoord chunk : civ.queries().territory().territoryOf(polity.id())) {
                if (!chunk.dimension().equals(dimension) || chunks.size() >= MAX_MAP_CHUNKS) {
                    continue;
                }
                Optional<Claim> claim = civ.queries().territory().claimAt(chunk);
                chunks.add(Json.object()
                        .put("x", chunk.x())
                        .put("z", chunk.z())
                        .put("town", polity.id().value().toString())
                        .put("outpost", claim.map(c -> c.type().name().equals("OUTPOST")).orElse(false))
                        .build());
            }
        }
        List<String> seats = new ArrayList<>();
        for (PolityView polity : civ.queries().polities()) {
            polity.seat().filter(seat -> seat.dimension().equals(dimension)).ifPresent(seat -> seats.add(Json.object()
                    .put("town", polity.id().value().toString())
                    .put("name", polity.name().value())
                    .put("x", seat.x())
                    .put("z", seat.z())
                    .build()));
        }
        return Json.object()
                .put("dimension", dimension.value())
                .raw("chunks", "[" + String.join(",", chunks) + "]")
                .raw("seats", "[" + String.join(",", seats) + "]")
                .build();
    }

    private String market() {
        List<String> offers = new ArrayList<>();
        for (MarketOffer offer : civ.market().offers(MAX_OFFERS)) {
            Optional<Quote> quote = items.apply(offer.commodity().id()).flatMap(civ.market()::quote);
            if (quote.isEmpty()) {
                continue;
            }
            offers.add(Json.object()
                    .put("id", offer.commodity().id())
                    .put("name", prettyName(offer.commodity().id()))
                    .put("stock", offer.stock())
                    .put("buy", money(quote.get().buyPrice()))
                    .put("sell", money(quote.get().sellPrice()))
                    .put("buyUnits", quote.get().buyPrice().amount())
                    .build());
        }
        return Json.object()
                .put("priceLevel", civ.market().priceLevelPercent())
                .raw("offers", "[" + String.join(",", offers) + "]")
                .build();
    }

    private String shops() {
        return Json.object().raw("shops", Json.array(civ.queries().shops().shops(), shop -> Json.object()
                .put("name", prettyName(shop.commodity().id()))
                .put("id", shop.commodity().id())
                .put("price", money(shop.price()))
                .put("priceUnits", shop.price().amount())
                .put("owner", civ.shops().shopAt(shop.position()).map(view -> view.owner()).orElse("?"))
                .put("town", civ.queries().territory().sovereignOf(shop.position().chunk())
                        .flatMap(id -> civ.queries().polity(id)).map(town -> town.name().value()).orElse(null))
                .put("x", shop.position().x())
                .put("y", shop.position().y())
                .put("z", shop.position().z())
                .put("dimension", shop.position().dimension().value())
                .build())).build();
    }

    /** Alle notierten Firmen mit Kursverlauf; ohne Anmeldung. */
    private String exchange() {
        return Json.object()
                .put("day", civ.calendar().today().value())
                .raw("listings", Json.array(civ.companies().listings(), listing -> Json.object()
                        .put("id", listing.id())
                        .put("name", listing.name())
                        .put("head", listing.head())
                        .put("price", listing.price())
                        .put("units", listing.units())
                        .raw("change", Double.toString(listing.change()))
                        .put("bookValue", listing.bookValue())
                        .put("marketCap", listing.marketCap())
                        .put("treasury", listing.treasury())
                        .put("shares", listing.shares())
                        .raw("dividendYield", Double.toString(listing.dividendYield()))
                        .put("volume", listing.volumeToday())
                        .put("employees", listing.employees())
                        .put("freeFloat", listing.freeFloat())
                        .raw("quotes", Json.array(listing.quotes(), quote -> Json.object()
                                .put("day", quote.day()).put("open", quote.open()).put("high", quote.high())
                                .put("low", quote.low()).put("close", quote.close()).put("volume", quote.volume())
                                .build()))
                        .raw("holders", Json.array(listing.holders(), holder -> Json.object()
                                .put("name", holder.name()).put("shares", holder.shares())
                                .raw("percent", Double.toString(Math.round(holder.percent() * 10) / 10.0)).build()))
                        .build()))
                .raw("trades", Json.array(civ.queries().exchange().trades().reversed().stream().limit(30).toList(),
                        trade -> Json.object()
                                .put("company", civ.queries().polity(trade.company()).map(p -> p.name().value())
                                        .orElse("?"))
                                .put("buyer", civ.texts().actor(trade.buyer()))
                                .put("seller", civ.texts().actor(trade.seller()))
                                .put("quantity", trade.quantity())
                                .put("price", money(Money.of(trade.price(), CurrencyId.EMERALD)))
                                .put("day", trade.day().value())
                                .build()))
                .build();
    }

    private String orderBook(String companyId, Optional<WebSessions.Login> viewer) {
        PolityId company = new PolityId(UUID.fromString(companyId));
        ActorRef who = viewer.<ActorRef>map(WebSessions.Login::player).orElse(ANONYMOUS);
        return Json.object().raw("orders", Json.array(civ.companies().book(company, who), this::order)).build();
    }

    private String order(OrderView order) {
        return Json.object()
                .put("number", order.number())
                .put("company", order.company())
                .put("trader", order.trader())
                .put("side", order.side())
                .put("quantity", order.quantity())
                .put("remaining", order.remaining())
                .put("limit", order.limit())
                .put("units", order.units())
                .put("placedOn", order.placedOn())
                .put("mine", order.mine())
                .build();
    }

    private String depot(WebSessions.Login login) {
        List<HoldingView> holdings = civ.companies().holdingsOf(login.player());
        long total = holdings.stream().mapToLong(HoldingView::valueUnits).sum();
        return Json.object()
                .put("value", money(Money.of(total, CurrencyId.EMERALD)))
                .raw("holdings", Json.array(holdings, holding -> Json.object()
                        .put("companyId", holding.companyId())
                        .put("company", holding.company())
                        .put("shares", holding.shares())
                        .put("free", holding.free())
                        .put("price", holding.price())
                        .put("value", holding.value())
                        .build()))
                .raw("orders", Json.array(civ.companies().ordersOf(login.player()), this::order))
                .raw("companies", Json.array(civ.companies().led(login.player()).stream()
                        .map(company -> company.name().value()).toList(), Json::string))
                .raw("papers", Json.array(civ.press().papersOf(login.player()).stream()
                        .map(paper -> paper.name().value()).toList(), Json::string))
                .build();
    }

    private Reply exchangeAction(WebSessions.Login login, Map<String, String> body) {
        String action = body.getOrDefault("action", "");
        if (action.equals("cancel")) {
            return civ.companies().cancel(login.player(), Integer.parseInt(body.getOrDefault("number", "0")));
        }
        Side side = switch (action) {
            case "buy" -> Side.BUY;
            case "sell" -> Side.SELL;
            default -> throw new IllegalArgumentException("Unbekannte Aktion");
        };
        long quantity = Long.parseLong(body.getOrDefault("quantity", "0"));
        String limit = body.getOrDefault("limit", "");
        return civ.companies().trade(login.player(), side, body.getOrDefault("company", ""), quantity,
                limit.isBlank() ? Optional.empty() : Optional.of(limit));
    }

    /** Zeitungen und neueste Artikel; ohne Anmeldung. */
    private String press() {
        return Json.object()
                .raw("newspapers", Json.array(civ.press().newspapers(), paper -> Json.object()
                        .put("id", paper.id())
                        .put("name", paper.name())
                        .put("head", paper.head())
                        .raw("editors", Json.array(paper.editors(), Json::string))
                        .put("villagerEditors", paper.villagerEditors())
                        .raw("towns", Json.array(paper.towns(), Json::string))
                        .put("articles", paper.articles())
                        .put("readers", paper.readers())
                        .put("founded", paper.founded())
                        .build()))
                .raw("articles", Json.array(civ.press().latest(80), this::article))
                .build();
    }

    private String article(ArticleView article) {
        return Json.object()
                .put("number", article.number())
                .put("newspaperId", article.newspaperId())
                .put("newspaper", article.newspaper())
                .put("author", article.author())
                .put("title", article.title())
                .put("body", article.body())
                .put("topic", article.topic())
                .put("subject", article.subject())
                .put("tendency", article.tendency())
                .put("tendencyWord", article.tendencyWord())
                .put("day", article.day())
                .raw("towns", Json.array(article.towns(), Json::string))
                .put("readers", article.readers())
                .build();
    }

    private String preview(String title, String body) {
        ArticleAnalysis analysis = civ.press().analyse(title, body);
        return Json.object()
                .put("subject", analysis.subject().map(civ.texts()::actor).orElse(""))
                .put("tendency", analysis.tendency())
                .put("tendencyWord", ArticleAnalysis.tendencyWord(analysis.tendency()))
                .put("topic", PressUseCases.topicName(analysis.topic()))
                .build();
    }

    /** Kennzahlen für die Startseite; ohne Anmeldung. */
    private String stats() {
        long towns = civ.queries().polities().stream().filter(p -> p.kind() == PolityKind.TERRITORIAL).count();
        long federations = civ.queries().polities().stream().filter(p -> p.kind() == PolityKind.FEDERATION).count();
        long parties = civ.queries().polities().stream().filter(p -> p.kind() == PolityKind.PARTY).count();
        long villagers = civ.queries().polities().stream().filter(p -> p.kind() == PolityKind.TERRITORIAL)
                .mapToLong(p -> p.members().stream().filter(m -> m.member() instanceof PersonRef).count()).sum();
        long streets = civ.queries().polities().stream().mapToLong(p -> p.streets().size()).sum();
        long elections = civ.queries().polities().stream().mapToLong(p -> p.elections().size()).sum();
        return Json.object()
                .put("day", civ.calendar().today().value())
                .put("towns", towns)
                .put("federations", federations)
                .put("parties", parties)
                .put("players", civ.players().all().size())
                .put("villagers", villagers)
                .put("online", civ.community().onlineCount())
                .raw("onlineNames", Json.array(civ.community().onlineNames(), Json::string))
                .put("contracts", civ.queries().contracts().active().size())
                .put("letters", civ.queries().community().letters().size())
                .put("messages", civ.queries().community().messages().size())
                .put("streets", streets)
                .put("companies", civ.queries().companies().size())
                .put("newspapers", civ.queries().newspapers().size())
                .put("articles", civ.queries().press().articles().size())
                .put("elections", elections)
                .put("priceLevel", civ.market().priceLevelPercent())
                .build();
    }

    /** Straßen und Hausnummern aller Gemeinden; ohne Anmeldung. */
    private String streets() {
        List<String> towns = new ArrayList<>();
        for (PolityView polity : civ.queries().polities()) {
            if (polity.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            towns.add(Json.object()
                    .put("id", polity.id().value().toString())
                    .put("name", polity.name().value())
                    .put("postalCode", polity.postalCode())
                    .raw("streets", Json.array(polity.streets(), street -> Json.object()
                            .put("name", street.name())
                            .put("minX", street.area().minX())
                            .put("minZ", street.area().minZ())
                            .put("maxX", street.area().maxX())
                            .put("maxZ", street.area().maxZ())
                            .build()))
                    .raw("addresses", Json.array(civ.civil().addressesOf(polity), address -> Json.object()
                            .put("street", address.street())
                            .put("number", address.number())
                            .put("line", address.line())
                            .build()))
                    .build());
        }
        return Json.object().raw("towns", "[" + String.join(",", towns) + "]").build();
    }

    private String chat(WebSessions.Login login, long after) {
        return Json.object()
                .raw("messages", Json.array(civ.community().chat(login.player(), after), message -> Json.object()
                        .put("seq", message.seq())
                        .put("from", message.from())
                        .put("to", message.to())
                        .put("text", message.text())
                        .put("at", message.at())
                        .put("origin", message.origin())
                        .put("mine", message.mine())
                        .build()))
                .raw("online", Json.array(civ.community().onlineNames(), Json::string))
                .build();
    }

    private String friends(WebSessions.Login login) {
        return Json.object().raw("friends", Json.array(civ.community().friends(login.player()), friend -> Json.object()
                .put("id", friend.id())
                .put("name", friend.name())
                .put("online", friend.online())
                .put("state", friend.state())
                .put("town", friend.town())
                .build()))
                .raw("players", Json.array(civ.players().all().stream().map(known -> known.name())
                        .filter(name -> !name.equalsIgnoreCase(login.name()))
                        .sorted(String.CASE_INSENSITIVE_ORDER).toList(), Json::string))
                .build();
    }

    private String letters(WebSessions.Login login) {
        Function<LetterView, String> letter = view -> Json.object()
                .put("id", view.id())
                .put("number", view.number())
                .put("from", view.from())
                .put("to", view.to())
                .put("subject", view.subject())
                .put("body", view.body())
                .put("sentOn", view.sentOn())
                .put("deliverOn", view.deliverOn())
                .put("read", view.read())
                .put("address", view.address())
                .build();
        return Json.object()
                .raw("inbox", Json.array(civ.community().inbox(login.player()), letter))
                .raw("sent", Json.array(civ.community().sent(login.player()), letter))
                .put("today", civ.calendar().today().value())
                .build();
    }

    private String contracts(WebSessions.Login login, Translator texts) {
        return Json.object().raw("contracts", Json.array(civ.civil().contractsOf(login.player()), contract -> Json.object()
                .put("number", contract.number())
                .put("kind", contract.kind().name())
                .put("kindName", texts.translate(civ.civil().kindName(contract.kind())))
                .put("status", contract.status().name())
                .put("statusName", texts.translate(civ.civil().statusName(contract.status())))
                .put("title", contract.title())
                .put("text", contract.text())
                .put("offeror", contract.offeror())
                .put("counterparty", contract.counterparty())
                .put("other", contract.other())
                .put("pays", contract.pays())
                .put("perDay", contract.perDay())
                .put("once", contract.once())
                .put("deposit", contract.deposit())
                .put("termDays", contract.termDays())
                .put("address", contract.address().orElse(null))
                .put("offeredOn", contract.offeredOn())
                .raw("startedOn", contract.startedOn().map(String::valueOf).orElse("null"))
                .raw("endsOn", contract.endsOn().map(String::valueOf).orElse("null"))
                .put("canSign", contract.canSign())
                .put("canDecline", contract.canDecline())
                .put("canNotice", contract.canNotice())
                .build())).build();
    }

    private Reply contractAction(WebSessions.Login login, Map<String, String> body) {
        Caller caller = caller(login);
        return switch (body.getOrDefault("action", "")) {
            case "sign" -> civ.civil().sign(caller, Integer.parseInt(body.getOrDefault("number", "0")));
            case "decline" -> civ.civil().decline(caller, Integer.parseInt(body.getOrDefault("number", "0")));
            case "notice" -> civ.civil().giveNotice(caller, Integer.parseInt(body.getOrDefault("number", "0")));
            case "custom" -> civ.civil().offerCustom(caller, body.getOrDefault("to", ""),
                    body.getOrDefault("once", "0"), body.getOrDefault("perDay", "0"),
                    body.getOrDefault("title", "") + " | " + body.getOrDefault("text", ""),
                    Long.parseLong(body.getOrDefault("days", "0")));
            case "employment" -> civ.civil().offerEmployment(caller, body.getOrDefault("to", ""),
                    body.getOrDefault("perDay", ""), Long.parseLong(body.getOrDefault("days", "0")));
            default -> throw new IllegalArgumentException("Unbekannte Aktion");
        };
    }

    /** Antwort mit übersetzter Meldung; fachliche Fehler (z. B. Direktnachricht an Nicht-Freunde) als 422. */
    private static HttpResponse domain(Translator texts, java.util.function.Supplier<Reply> action) {
        Reply reply = action.get();
        return HttpResponse.json(reply.success() ? 200 : 422, Json.object()
                .put("ok", reply.success())
                .put("message", texts.translate(reply))
                .build());
    }

    private static long parseLong(String value) {
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String me(WebSessions.Login login, Translator texts) {
        Optional<PolityView> town = civ.queries().municipalityOf(login.player());
        Json.ObjectBuilder out = Json.object()
                .put("name", login.name())
                .put("balance", money(civ.bank().balanceOf(login.player())))
                .put("address", civ.civil().residenceLine(login.player()).orElse(null))
                .put("unread", civ.community().inbox(login.player()).stream().filter(letter -> !letter.read()).count())
                .put("friendRequests", civ.community().friends(login.player()).stream()
                        .filter(friend -> friend.state().equals("incoming")).count())
                .put("openContracts", civ.civil().contractsOf(login.player()).stream()
                        .filter(ContractView::canSign).count())
                .put("day", civ.calendar().today().value());
        if (town.isPresent()) {
            List<ProposalView> proposals = civ.voting().proposalsOf(town.get(), login.player());
            out.put("town", town.get().name().value())
                    .raw("proposals", Json.array(proposals, proposal -> Json.object()
                            .put("number", proposal.number())
                            .put("law", texts.translate(proposal.law()))
                            .put("value", proposal.value() instanceof Message message
                                    ? texts.translate(message) : String.valueOf(proposal.value()))
                            .put("proposer", proposal.proposer())
                            .put("inFavour", proposal.inFavour())
                            .put("against", proposal.against())
                            .put("electorate", proposal.electorate())
                            .put("decidedOn", proposal.decidedOn())
                            .put("ownVote", proposal.ownVote().map(yes -> yes ? "yes" : "no").orElse(null))
                            .build()));
            out.raw("offices", Json.array(civ.offices().officesOf(town.get(), login.player()),
                    office -> office(office, texts)));
            out.raw("platforms", Json.array(List.of(Platform.values()), platform -> Json.object()
                    .put("id", platform.name())
                    .put("name", texts.translate(Message.of(TranslationKeys.platform(platform))))
                    .build()));
        } else {
            out.put("town", null).raw("proposals", "[]").raw("offices", "[]").raw("platforms", "[]");
        }
        return out.build();
    }

    private static String office(OfficeView office, Translator texts) {
        Json.ObjectBuilder out = Json.object()
                .put("office", office.office().name())
                .put("title", texts.translate(office.name()))
                .put("holder", office.holder().orElse(null))
                .put("implicit", office.implicit())
                .raw("until", office.until().map(String::valueOf).orElse("null"));
        if (office.election().isEmpty()) {
            return out.raw("election", "null").build();
        }
        ElectionView election = office.election().get();
        return out.raw("election", Json.object()
                .put("closesOn", election.closesOn())
                .put("ballots", election.ballots())
                .put("electorate", election.electorate())
                .put("ownCandidacy", election.ownCandidacy())
                .put("ownChoice", election.ownChoice().map(actor -> actor.uuid().toString()).orElse(null))
                .raw("candidates", Json.array(election.candidates(), candidate -> Json.object()
                        .put("id", candidate.actor().uuid().toString())
                        .put("name", candidate.name())
                        .put("platform", candidate.platform().name())
                        .put("platformName", texts.translate(Message.of(TranslationKeys.platform(candidate.platform()))))
                        .put("party", candidate.party().orElse(""))
                        .build()))
                .build()).build();
    }

    /** Der Kandidat mit dieser UUID in der laufenden Wahl; sonst ein Spieler mit dieser UUID, was abgelehnt wird. */
    private ActorRef candidateOf(WebSessions.Login login, Office office, UUID id) {
        return civ.queries().municipalityOf(login.player())
                .flatMap(town -> town.electionFor(office))
                .flatMap(election -> election.candidates().stream()
                        .filter(candidate -> candidate.uuid().equals(id)).findFirst())
                .orElseGet(() -> new PlayerRef(new PlayerId(id)));
    }

    private HttpResponse login(HttpRequest request) {
        Map<String, String> body = Json.parseFlat(request.body());
        return sessions.redeem(body.getOrDefault("code", ""), request.remoteAddress())
                .map(token -> HttpResponse.json(200, Json.object().put("ok", true).build())
                        .withHeader("Set-Cookie", SESSION_COOKIE + "=" + token
                                + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=" + WebSessions.SESSION_LIFETIME.toSeconds()))
                .orElseGet(() -> HttpResponse.error(401, "Code ungültig oder abgelaufen. Hole dir im Spiel mit /civ web einen neuen."));
    }

    private HttpResponse logout(HttpRequest request) {
        request.cookie(SESSION_COOKIE).ifPresent(sessions::logout);
        return HttpResponse.json(200, Json.object().put("ok", true).build())
                .withHeader("Set-Cookie", SESSION_COOKIE + "=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0");
    }

    private HttpResponse withLogin(HttpRequest request, Function<WebSessions.Login, HttpResponse> action) {
        Optional<WebSessions.Login> login = request.cookie(SESSION_COOKIE).flatMap(sessions::session);
        if (login.isEmpty()) {
            return HttpResponse.error(401, "Nicht angemeldet");
        }
        if (request.method().equals("POST") && !request.header("content-type").orElse("").startsWith("application/json")) {
            return HttpResponse.error(400, "JSON erwartet");
        }
        return action.apply(login.get());
    }

    private static HttpResponse action(HttpRequest request, Function<Map<String, String>, Reply> action, Translator texts) {
        Reply reply = action.apply(Json.parseFlat(request.body()));
        return HttpResponse.json(reply.success() ? 200 : 422, Json.object()
                .put("ok", reply.success())
                .put("message", texts.translate(reply))
                .build());
    }

    private static Caller caller(WebSessions.Login login) {
        return new Caller(login.player(), login.name(), NOWHERE);
    }

    private static String money(Money money) {
        return MoneyFormat.whole(money.amount()) + "," + MoneyFormat.cents(money.amount());
    }

    /** {@code minecraft:oak_log} → {@code Oak Log}; Itemnamen kennt nur der Client. */
    static String prettyName(String id) {
        String path = id.substring(id.indexOf(':') + 1).replace('/', ' ').replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : path.split(" ")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    private static byte[] loadPage() {
        try (InputStream in = WebApi.class.getResourceAsStream("/web/index.html")) {
            return in == null ? "<h1>Eddies Civ Mod</h1>".getBytes(java.nio.charset.StandardCharsets.UTF_8) : in.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
