package de.etiennebader.civ.server.web;

import de.etiennebader.civ.server.command.TranslationKeys;

import de.etiennebader.civ.domain.polity.Platform;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.market.Quote;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.market.ItemTraits;
import de.etiennebader.civ.server.market.MarketOffer;
import de.etiennebader.civ.server.office.ElectionView;
import de.etiennebader.civ.server.office.OfficeView;
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

/**
 * Die Endpunkte der Webseite.
 *
 * <ul>
 *     <li>{@code GET /} die Seite selbst</li>
 *     <li>{@code GET /api/world} Tag, Preisniveau und alle Gemeinden</li>
 *     <li>{@code GET /api/map?dim=minecraft:overworld} beanspruchte Chunks einer Dimension</li>
 *     <li>{@code GET /api/market} Lager und Preise des Servermarkts</li>
 *     <li>{@code POST /api/login} mit {@code {"code": "..."}} aus {@code /civ web}</li>
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
                case "GET /api/shops" -> HttpResponse.json(200, game.call(this::shops));
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
                    .build());
        }
        return Json.object()
                .put("day", civ.calendar().today().value())
                .put("priceLevel", civ.market().priceLevelPercent())
                .put("players", civ.players().all().size())
                .raw("towns", "[" + String.join(",", towns) + "]")
                .build();
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

    private String me(WebSessions.Login login, Translator texts) {
        Optional<PolityView> town = civ.queries().municipalityOf(login.player());
        Json.ObjectBuilder out = Json.object()
                .put("name", login.name())
                .put("balance", money(civ.bank().balanceOf(login.player())));
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
