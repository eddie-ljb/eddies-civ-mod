package de.etiennebader.civ.server.plot;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.land.AssignPlot;
import de.etiennebader.civ.application.land.BuyPlot;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.land.DissolvePlot;
import de.etiennebader.civ.application.land.OfferPlot;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.MoneyFormat;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.town.TownRoles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Grundstücke im Spiel.
 *
 * <ul>
 *     <li>Bürger wählen zwei Ecken aus und legen daraus ein Grundstück der Gemeinde an, oder nehmen einen ganzen Chunk.</li>
 *     <li>Inhaber bieten Grundstücke zum Verkauf an; Bürger handeln dabei für Gemeindeland.</li>
 *     <li>Jeder kann angebotene Grundstücke kaufen.</li>
 *     <li>Bürger vergeben Gemeindeland kostenlos und lösen es wieder auf.</li>
 * </ul>
 */
public final class PlotService {

    /** Größtes Grundstück, das sich in einem Schritt anlegen lässt. */
    static final long MAX_AREA = 128L * 128L;

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final PlayerDirectory players;
    private final Texts texts;
    private final Map<PlayerRef, Selection> selections = new HashMap<>();
    private final Map<PlayerRef, Optional<PlotId>> lastPlot = new HashMap<>();

    /** Die beiden ausgewählten Ecken eines künftigen Grundstücks. */
    public record Selection(Optional<BlockPosition> first, Optional<BlockPosition> second) {

        static final Selection EMPTY = new Selection(Optional.empty(), Optional.empty());

        Selection with(int corner, BlockPosition position) {
            return corner == 1 ? new Selection(Optional.of(position), second) : new Selection(first, Optional.of(position));
        }

        /** Die Fläche, wenn beide Ecken in derselben Dimension gesetzt sind. */
        public Optional<Area> area() {
            if (first.isEmpty() || second.isEmpty() || !first.get().dimension().equals(second.get().dimension())) {
                return Optional.empty();
            }
            return Optional.of(Area.spanning(first.get().dimension(), first.get().x(), first.get().z(),
                    second.get().x(), second.get().z()));
        }
    }

    public PlotService(CivApplication app, Calendar calendar, PlayerDirectory players, Texts texts) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
    }

    /** Setzt Ecke 1 oder 2 auf die Position des Spielers. */
    public Reply selectCorner(Caller caller, int corner) {
        if (corner != 1 && corner != 2) {
            throw new IllegalArgumentException("Ecke 1 oder 2: " + corner);
        }
        Selection selection = selections.getOrDefault(caller.player(), Selection.EMPTY).with(corner, caller.position());
        selections.put(caller.player(), selection);
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.PLOT_CORNER_SET, corner, caller.position().x(), caller.position().z()));
        selection.area().ifPresent(area ->
                lines.add(Message.of(MessageKey.PLOT_SELECTION, area.width(), area.depth(), area.size())));
        return Reply.success(lines);
    }

    public Selection selection(PlayerRef player) {
        return selections.getOrDefault(player, Selection.EMPTY);
    }

    /** Legt aus der Auswahl ein Grundstück der eigenen Gemeinde an. */
    public Reply createFromSelection(Caller caller, String typeInput) {
        Optional<Area> area = selection(caller.player()).area();
        if (area.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_SELECTION_INCOMPLETE));
        }
        Reply reply = create(caller, area.get(), typeInput);
        if (reply.success()) {
            selections.remove(caller.player());
        }
        return reply;
    }

    /** Macht den ganzen Chunk, in dem der Spieler steht, zu einem Grundstück. */
    public Reply createChunk(Caller caller, String typeInput) {
        return create(caller, Area.ofChunk(caller.chunk()), typeInput);
    }

    private Reply create(Caller caller, Area area, String typeInput) {
        Optional<PlotType> type = parseType(typeInput);
        if (type.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.INVALID_PLOT_TYPE, typeInput.strip(), String.join(", ", typeNames())));
        }
        if (area.size() > MAX_AREA) {
            return Reply.failure(Message.of(MessageKey.PLOT_TOO_LARGE, area.size(), MAX_AREA));
        }
        return asCitizen(caller, town -> attempt(() -> {
            app.createPlot().execute(new CreatePlot.Command(town.id(), area, type.get(), calendar.today()));
            return Reply.success(Message.of(MessageKey.PLOT_CREATED, Message.of(TranslationKeys.plotType(type.get())),
                    area.width(), area.depth(), town.name().value()));
        }));
    }

    /** Beschreibt das Grundstück, auf dem der Spieler steht. */
    public Reply info(Caller caller) {
        Optional<PlotView> plot = plotAt(caller.player(), caller.position());
        if (plot.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_NONE_HERE));
        }
        PlotView view = plot.get();
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.PLOT_INFO, Message.of(TranslationKeys.plotType(view.type())), view.townName(),
                view.area().width(), view.area().depth(), view.area().size()));
        lines.add(Message.of(MessageKey.PLOT_INFO_HOLDER, view.holder(), view.holderSince()));
        lines.add(view.askingPrice()
                .map(price -> Message.of(MessageKey.PLOT_INFO_OFFER, texts.money(price)))
                .orElseGet(() -> Message.of(MessageKey.PLOT_INFO_NOT_OFFERED)));
        dailyTax(view).ifPresent(tax -> lines.add(Message.of(MessageKey.PLOT_INFO_TAX, texts.money(tax))));
        return Reply.success(lines);
    }

    /** Bietet das Grundstück an der Position des Spielers zum Verkauf an. */
    public Reply offer(Caller caller, String priceInput) {
        long units;
        try {
            units = priceInput.strip().equals("0") ? 0 : MoneyFormat.parse(priceInput);
        } catch (IllegalArgumentException e) {
            return Reply.failure(Message.of(MessageKey.INVALID_AMOUNT, priceInput.strip()));
        }
        return withOwnPlot(caller, (view, seller) -> attempt(() -> {
            Money price = Money.of(units, queries.polity(view.town()).orElseThrow().currency());
            app.offerPlot().execute(new OfferPlot.Command(view.town(), view.id(), seller, Optional.of(price),
                    calendar.today()));
            return Reply.success(Message.of(MessageKey.PLOT_OFFERED, texts.money(price)));
        }));
    }

    public Reply withdrawOffer(Caller caller) {
        return withOwnPlot(caller, (view, seller) -> attempt(() -> {
            app.offerPlot().execute(new OfferPlot.Command(view.town(), view.id(), seller, Optional.empty(),
                    calendar.today()));
            return Reply.success(Message.of(MessageKey.PLOT_OFFER_WITHDRAWN));
        }));
    }

    /** Kauft das Grundstück, auf dem der Spieler steht. */
    public Reply buyHere(Caller caller) {
        Optional<PlotView> plot = plotAt(caller.player(), caller.position());
        if (plot.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_NONE_HERE));
        }
        return buy(caller, plot.get().town(), plot.get().id());
    }

    /** Kauft ein angebotenes Grundstück, z. B. aus der Liste im Gemeindehaus. */
    public Reply buy(Caller caller, PolityId town, PlotId plot) {
        Optional<Plot> current = queries.land(town).plot(plot);
        if (current.isPresent() && current.get().isHeldBy(caller.player())) {
            return Reply.failure(Message.of(MessageKey.PLOT_ALREADY_YOURS));
        }
        return attempt(() -> {
            Money price = app.buyPlot().execute(new BuyPlot.Command(town, plot, caller.player(), calendar.today()));
            lastPlot.remove(caller.player());
            return Reply.success(Message.of(MessageKey.PLOT_BOUGHT, texts.money(price)));
        });
    }

    /** Ein Bürger vergibt Gemeindeland kostenlos an einen Spieler. */
    public Reply assign(Caller caller, String playerName) {
        Optional<PlayerRef> recipient = players.findByName(playerName.strip());
        if (recipient.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, playerName.strip()));
        }
        return withTownPlot(caller, view -> attempt(() -> {
            app.assignPlot().execute(new AssignPlot.Command(view.town(), view.id(), recipient.get(), calendar.today()));
            return Reply.success(Message.of(MessageKey.PLOT_ASSIGNED, texts.actor(recipient.get())));
        }));
    }

    /** Ein Bürger löst ein Grundstück der Gemeinde auf. */
    public Reply dissolve(Caller caller) {
        return withTownPlot(caller, view -> attempt(() -> {
            app.dissolvePlot().execute(new DissolvePlot.Command(view.town(), view.id(), calendar.today()));
            return Reply.success(Message.of(MessageKey.PLOT_DISSOLVED));
        }));
    }

    /** Alle Grundstücke einer Gemeinde, angebotene zuerst. */
    public List<PlotView> plotsOf(PolityId town, PlayerRef viewer) {
        Optional<PolityView> polity = queries.polity(town);
        if (polity.isEmpty()) {
            return List.of();
        }
        return queries.land(town).plots().stream()
                .map(plot -> view(polity.get(), plot, viewer))
                .sorted(Comparator.comparing((PlotView view) -> view.askingPrice().isEmpty())
                        .thenComparingInt(view -> view.area().minX())
                        .thenComparingInt(view -> view.area().minZ()))
                .toList();
    }

    /** Das Grundstück an einer Stelle, falls dort eines liegt. */
    public Optional<PlotView> plotAt(PlayerRef viewer, BlockPosition position) {
        Optional<PolityId> sovereign = queries.territory().sovereignOf(position.chunk());
        if (sovereign.isEmpty()) {
            return Optional.empty();
        }
        Optional<PolityView> polity = queries.polity(sovereign.get());
        if (polity.isEmpty()) {
            return Optional.empty();
        }
        return queries.land(sovereign.get()).plotAt(position.dimension(), position.x(), position.z())
                .map(plot -> view(polity.get(), plot, viewer));
    }

    /**
     * Meldet, wenn ein Spieler ein anderes Grundstück betritt.
     *
     * @return der Hinweis für die Anzeige über der Schnellzugriffsleiste
     */
    public Optional<Message> moved(PlayerRef player, BlockPosition position) {
        Optional<PlotView> plot = plotAt(player, position);
        Optional<PlotId> id = plot.map(PlotView::id);
        Optional<PlotId> previous = lastPlot.put(player, id);
        if (id.equals(previous) || (previous == null && id.isEmpty())) {
            return Optional.empty();
        }
        if (plot.isEmpty()) {
            return Optional.of(Message.of(MessageKey.PLOT_LEFT));
        }
        PlotView view = plot.get();
        Message type = Message.of(TranslationKeys.plotType(view.type()));
        return Optional.of(view.askingPrice()
                .map(price -> Message.of(MessageKey.PLOT_ENTERED_FOR_SALE, type, view.holder(), texts.money(price)))
                .orElseGet(() -> Message.of(MessageKey.PLOT_ENTERED, type, view.holder())));
    }

    public void forget(PlayerRef player) {
        lastPlot.remove(player);
        selections.remove(player);
    }

    /** Die Namen der Grundstücksarten für Befehle, z. B. {@code wohnen}. */
    public static List<String> typeNames() {
        return List.of("wohnen", "gewerbe", "industrie", "landwirtschaft", "oeffentlich", "botschaft");
    }

    static Optional<PlotType> parseType(String input) {
        String name = input.strip().toLowerCase(Locale.ROOT);
        return Optional.ofNullable(switch (name) {
            case "wohnen", "residential" -> PlotType.RESIDENTIAL;
            case "gewerbe", "commercial" -> PlotType.COMMERCIAL;
            case "industrie", "industrial" -> PlotType.INDUSTRIAL;
            case "landwirtschaft", "agricultural" -> PlotType.AGRICULTURAL;
            case "oeffentlich", "öffentlich", "public" -> PlotType.PUBLIC;
            case "botschaft", "embassy" -> PlotType.EMBASSY;
            default -> null;
        });
    }

    private PlotView view(PolityView town, Plot plot, PlayerRef viewer) {
        ActorRef holder = plot.holder();
        return new PlotView(town.id(), town.name().value(), plot.id(), plot.type(), plot.area(), texts.actor(holder),
                holder.equals(town.asActor()), holder.equals(viewer), plot.holderSince().value(), plot.askingPrice());
    }

    /** Tägliche Grundsteuer, die der Inhaber für dieses Grundstück allein zahlen würde. */
    private Optional<Money> dailyTax(PlotView view) {
        if (view.townLand()) {
            return Optional.empty();
        }
        PolityView town = queries.polity(view.town()).orElseThrow();
        long rate = town.laws().valueOf(StandardParameters.LAND_TAX_PER_BLOCK);
        long free = town.laws().valueOf(StandardParameters.LAND_TAX_FREE_BLOCKS);
        long taxed = Math.max(0, view.area().size() - free);
        if (rate == 0 || taxed == 0) {
            return Optional.empty();
        }
        return Optional.of(Money.of(rate * taxed, town.currency()));
    }

    @FunctionalInterface
    private interface TownAction {

        Reply apply(PolityView town);
    }

    @FunctionalInterface
    private interface HolderAction {

        Reply apply(PlotView plot, ActorRef seller);
    }

    @FunctionalInterface
    private interface PlotAction {

        Reply apply(PlotView plot);
    }

    private Reply asCitizen(Caller caller, TownAction action) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        if (!TownRoles.isCitizen(town.get(), caller.player())) {
            return Reply.failure(Message.of(ErrorCode.NOT_A_CITIZEN.translationKey(), caller.name(),
                    town.get().name().value()));
        }
        return action.apply(town.get());
    }

    /** Für den Inhaber selbst oder, bei Gemeindeland, für einen Bürger der Gemeinde. */
    private Reply withOwnPlot(Caller caller, HolderAction action) {
        Optional<PlotView> plot = plotAt(caller.player(), caller.position());
        if (plot.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_NONE_HERE));
        }
        PlotView view = plot.get();
        if (view.heldByViewer()) {
            return action.apply(view, caller.player());
        }
        PolityView town = queries.polity(view.town()).orElseThrow();
        if (view.townLand() && TownRoles.isCitizen(town, caller.player())) {
            return action.apply(view, town.asActor());
        }
        return Reply.failure(Message.of(MessageKey.PLOT_NOT_YOURS, view.holder()));
    }

    /** Nur für Gemeindeland, nur für Bürger der Gemeinde. */
    private Reply withTownPlot(Caller caller, PlotAction action) {
        Optional<PlotView> plot = plotAt(caller.player(), caller.position());
        if (plot.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_NONE_HERE));
        }
        PlotView view = plot.get();
        PolityView town = queries.polity(view.town()).orElseThrow();
        if (!TownRoles.isCitizen(town, caller.player())) {
            return Reply.failure(Message.of(ErrorCode.NOT_A_CITIZEN.translationKey(), caller.name(), town.name().value()));
        }
        if (!view.townLand()) {
            return Reply.failure(Message.of(MessageKey.PLOT_NOT_TOWN_LAND, view.holder()));
        }
        return action.apply(view);
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}
