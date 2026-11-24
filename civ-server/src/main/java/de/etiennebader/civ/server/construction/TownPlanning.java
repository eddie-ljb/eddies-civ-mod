package de.etiennebader.civ.server.construction;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.land.BuyPlot;
import de.etiennebader.civ.application.land.CreatePlot;
import de.etiennebader.civ.application.land.OfferPlot;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.construction.Blueprint;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.command.TranslationKeys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Stadtplanung durch Dorfbewohner: Ist das Bauamt (Planer, sonst Bürgermeister) einer Gemeinde ein Dorfbewohner,
 * schneidet er im Gemeindegebiet neue Grundstücke zu und schreibt sie aus.
 *
 * <ul>
 *   <li><b>Wohnen, Gewerbe, Industrie, Landwirtschaft:</b> werden zum Verkauf angeboten, zum Preis von
 *       {@value #PRICE_PER_BLOCK} Hundertsteln je Block. Je Gemeinde stehen höchstens {@value #MAX_OFFERED}
 *       Grundstücke gleichzeitig zum Verkauf.</li>
 *   <li><b>Öffentliche Plätze, Schule, Gotteshaus:</b> behält die Gemeinde; dort lässt das Bauamt später kommunale
 *       Bauten errichten ({@link ConstructionService}). Welche Art als Nächstes kommt, folgt dem Bedarf: ab sechs
 *       Einwohnern eine Schule, ab zehn ein Gotteshaus, dazu öffentliche Plätze.</li>
 *   <li><b>Wohnungssuchende:</b> Dorfbewohner ohne eigenes Wohngrundstück kaufen das billigste angebotene, wenn ihr
 *       Geld zusätzlich für ein Haus darauf reicht; danach lassen sie bauen.</li>
 * </ul>
 *
 * <p>Der Zufall ist fest je Person und Tag.
 */
final class TownPlanning {

    static final long PRICE_PER_BLOCK = 6;
    static final int MAX_OFFERED = 3;
    /** Tageswahrscheinlichkeit, dass das Bauamt ein neues Grundstück zuschneidet. */
    static final double PLAN_CHANCE = 0.3;
    /** Tageswahrscheinlichkeit, dass ein Dorfbewohner ohne Wohnland eines kauft. */
    static final double BUY_CHANCE = 0.1;
    /** Höchstens so viele Grundstücke je beanspruchtem Chunk. */
    static final int PLOTS_PER_CHUNK = 3;

    private final CivApplication app;
    private final BlueprintLibrary library;
    private final Announcements announcements;
    private final Texts texts;
    private Function<PolityId, Optional<ActorRef>> authority = id -> Optional.empty();

    TownPlanning(CivApplication app, BlueprintLibrary library, Announcements announcements, Texts texts) {
        this.app = Objects.requireNonNull(app, "app");
        this.library = Objects.requireNonNull(library, "library");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.texts = Objects.requireNonNull(texts, "texts");
    }

    private java.util.function.Predicate<Area> site = area -> true;
    private de.etiennebader.civ.server.road.RoadService roads;
    private de.etiennebader.civ.server.road.StreetAccess streets;

    /** Die Straßenpflicht: Grundstücke schneidet das Bauamt nur an gebauten Straßen zu. */
    void streetAccess(de.etiennebader.civ.server.road.StreetAccess streets) {
        this.streets = streets;
    }

    private de.etiennebader.civ.server.land.LandRules land;

    /** Land vor Wasser: Grundstücke und Straßen legt das Bauamt nur an Land an. */
    void landRules(de.etiennebader.civ.server.land.LandRules land) {
        this.land = land;
    }

    private boolean plotOnLand(Area area) {
        return land == null || land.isLandPlot(area);
    }

    private boolean streetOnLand(Area area) {
        return land == null || land.isLandStreet(area);
    }

    private boolean onStreet(PolityView town, Area area) {
        return streets == null || streets.served(town, area);
    }

    /** Der Wegebau, den das Bauamt nach einer neuen Straße beauftragt. */
    void roads(de.etiennebader.civ.server.road.RoadService roads) {
        this.roads = roads;
    }

    /** Ob eine Fläche sicher frei von Gebautem ist; kommt von {@link ConstructionService}. */
    void site(java.util.function.Predicate<Area> site) {
        this.site = Objects.requireNonNull(site, "site");
    }

    /** Wer in einer Gemeinde das Bauamt ist; kommt von {@link ConstructionService}. */
    void authority(Function<PolityId, Optional<ActorRef>> authority) {
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    void endOfDay(Day day) {
        WorldQueries world = app.queries();
        List<PolityView> towns = world.polities().stream().filter(p -> p.kind() == PolityKind.TERRITORIAL)
                .sorted(Comparator.comparing(p -> p.id().value())).toList();
        for (PolityView town : towns) {
            authority.apply(town.id()).filter(PersonRef.class::isInstance).map(PersonRef.class::cast)
                    .ifPresent(planner -> {
                        foundStreet(town, planner, day);
                        plan(town, planner, day);
                    });
            buyLand(town, day);
        }
    }

    // ------------------------------------------------------------------ Ausschreiben

    private void plan(PolityView town, PersonRef planner, Day day) {
        if (VillagerAmbitions.roll(planner, day, 0x96) >= PLAN_CHANCE) {
            return;
        }
        WorldQueries world = app.queries();
        LandView land = world.land(town.id());
        List<ChunkCoord> chunks = world.territory().territoryOf(town.id()).stream()
                .sorted(Comparator.comparing((ChunkCoord c) -> c.dimension().value()).thenComparingInt(ChunkCoord::x)
                        .thenComparingInt(ChunkCoord::z)).toList();
        List<Plot> plots = new ArrayList<>(land.plots());
        long offered = plots.stream().filter(plot -> plot.holder().equals(town.asActor()) && plot.isForSale()).count();
        if (offered >= MAX_OFFERED || plots.size() >= (long) PLOTS_PER_CHUNK * chunks.size()) {
            return;
        }
        PlotType type = nextType(town, plots, planner, day);
        int size = sizeOf(type);
        int inset = (ChunkCoord.SIZE - size) / 2;
        for (ChunkCoord chunk : chunks) {
            if (town.seat().filter(seat -> seat.chunk().equals(chunk)).isPresent()) {
                continue; // der Kern mit dem Gemeindehaus bleibt frei
            }
            Area area = new Area(chunk.dimension(), chunk.minBlockX() + inset, chunk.minBlockZ() + inset,
                    chunk.minBlockX() + inset + size - 1, chunk.minBlockZ() + inset + size - 1);
            if (!site.test(area)) {
                continue; // dort steht schon etwas: kein Grundstück darüber
            }
            if (!onStreet(town, area)) {
                continue; // erst die Straße, dann das Grundstück
            }
            if (!plotOnLand(area)) {
                continue; // nicht ins Wasser
            }
            try {
                PlotId id = app.createPlot().execute(new CreatePlot.Command(town.id(), area, type, day));
                publish(town, id, area, type, day);
                return;
            } catch (DomainException | IllegalArgumentException | IllegalStateException ignored) {
                // überlappt oder liegt nicht ganz im Gebiet: nächster Chunk
            }
        }
    }

    // ------------------------------------------------------------------ Straßen

    /** Tageswahrscheinlichkeit, dass das Bauamt eine neue Straße anlegt, wenn es Bedarf gibt. */
    static final double STREET_CHANCE = 0.4;
    /** Höchstens so viele Straßen; sonst je vier Grundstücke eine. */
    static final int MAX_STREETS = 8;
    /** So viele Chunks lang darf eine Straße höchstens werden. */
    static final int MAX_STREET_CHUNKS = 5;

    /**
     * Das Bauamt gründet eine Straße, wenn es mehr Grundstücke als Straßen gibt (eine Straße je vier Grundstücke, die
     * erste schon ab zwei): Es sucht im Gebiet einen freien Streifen zwischen zwei Chunks, an dem keine Grundstücke
     * und keine andere Straße liegen, tauft ihn und lässt den Weg bauen. Ist die Kasse gut gefüllt, wird gepflastert, sonst
     * trampelt das Bauamt den Weg mit der Schaufel.
     */
    private void foundStreet(PolityView town, PersonRef planner, Day day) {
        WorldQueries world = app.queries();
        List<Plot> plots = new ArrayList<>(world.land(town.id()).plots());
        int streetCount = town.streets().size();
        boolean streetRequired = this.streets == null || this.streets.required(town);
        // Erst die Straße, dann die Grundstücke: Die erste Straße kommt ohne Grundstücke, jede weitere, sobald je
        // Straße vier Grundstücke stehen. Solange ein Weg noch gebaut wird, kommt keine neue Straße.
        boolean needed = streetRequired ? streetCount == 0 || !roomForPlot(town, plots)
                : plots.size() >= 2 && streetCount * 4 < plots.size() + 2;
        if (streetCount >= MAX_STREETS || !needed || (roads != null && roads.unfinished(town.id()) > 0)
                || VillagerAmbitions.roll(planner, day, 0x9A) >= STREET_CHANCE) {
            return;
        }
        java.util.Set<ChunkCoord> claimed = new java.util.HashSet<>(world.territory().territoryOf(town.id()));
        List<ChunkCoord> chunks = claimed.stream()
                .sorted(Comparator.comparingLong((ChunkCoord c) -> distanceToSeat(town, c))
                        .thenComparingInt(ChunkCoord::x).thenComparingInt(ChunkCoord::z)).toList();
        boolean horizontalFirst = VillagerAmbitions.roll(planner, day, 0x9B) < 0.5;
        for (ChunkCoord start : chunks) {
            for (boolean horizontal : new boolean[] {horizontalFirst, !horizontalFirst}) {
                Optional<Area> corridor = corridor(claimed, start, horizontal);
                if (corridor.isEmpty()) {
                    continue;
                }
                Area area = corridor.get();
                if (plots.stream().anyMatch(plot -> plot.area().overlaps(area))
                        || town.streets().stream().anyMatch(street -> street.area().overlaps(area))
                        || town.seat().filter(seat -> area.contains(seat.dimension(), seat.x(), seat.z())).isPresent()
                        || !site.test(area) || !streetOnLand(area)) {
                    continue;
                }
                String name = de.etiennebader.civ.application.population.FoundingNames.street(planner,
                        town.streets().stream().map(de.etiennebader.civ.domain.polity.Street::name)
                                .collect(java.util.stream.Collectors.toSet()), town.name().value());
                try {
                    app.streets().lay(planner, town.id(), name, area, day);
                } catch (DomainException | IllegalArgumentException | IllegalStateException ignored) {
                    continue;
                }
                orderRoad(town, planner, name, area, day);
                return;
            }
        }
    }

    /**
     * Ob an den bisherigen Straßen noch ein Grundstück Platz hat: ein Chunk (nicht der Kern), dessen mittiger Platz frei
     * und an einer gebauten Straße liegt. Ist nichts mehr frei, braucht die Gemeinde die nächste Straße. (Früher kam sie
     * erst bei vier Grundstücken je Straße; lagen weniger Chunks an der Straße, kam nie wieder eine.)
     */
    private boolean roomForPlot(PolityView town, List<Plot> plots) {
        WorldQueries world = app.queries();
        for (ChunkCoord chunk : world.territory().territoryOf(town.id())) {
            if (town.seat().filter(seat -> seat.chunk().equals(chunk)).isPresent()) {
                continue;
            }
            int size = sizeOf(PlotType.RESIDENTIAL);
            int inset = (ChunkCoord.SIZE - size) / 2;
            Area area = new Area(chunk.dimension(), chunk.minBlockX() + inset, chunk.minBlockZ() + inset,
                    chunk.minBlockX() + inset + size - 1, chunk.minBlockZ() + inset + size - 1);
            if (plots.stream().noneMatch(plot -> plot.area().overlaps(area)) && onStreet(town, area)
                    && plotOnLand(area) && site.test(area)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ein zwei Blöcke breiter Streifen auf der Grenze zwischen {@code start} und seinem östlichen (waagerecht) bzw.
     * südlichen Nachbarn, so lang wie die Reihe zusammenhängender Chunks, die beide Seiten dem Ort gehören. Die Grundstücke
     * (innen eingerückt) lassen dort Platz.
     */
    private static Optional<Area> corridor(java.util.Set<ChunkCoord> claimed, ChunkCoord start, boolean horizontal) {
        int stepX = horizontal ? 1 : 0;
        int stepZ = horizontal ? 0 : 1;
        // horizontal: Straße in X-Richtung auf der Grenze zwischen Reihe z und z+1
        ChunkCoord neighbour = new ChunkCoord(start.dimension(), start.x() + (horizontal ? 0 : 1),
                start.z() + (horizontal ? 1 : 0));
        if (!claimed.contains(neighbour)) {
            return Optional.empty();
        }
        int length = 1;
        ChunkCoord last = start;
        while (length < MAX_STREET_CHUNKS) {
            ChunkCoord next = new ChunkCoord(start.dimension(), last.x() + stepX, last.z() + stepZ);
            ChunkCoord nextNeighbour = new ChunkCoord(start.dimension(), next.x() + (horizontal ? 0 : 1),
                    next.z() + (horizontal ? 1 : 0));
            if (!claimed.contains(next) || !claimed.contains(nextNeighbour)) {
                break;
            }
            last = next;
            length++;
        }
        if (horizontal) {
            int border = neighbour.minBlockZ();
            return Optional.of(new Area(start.dimension(), start.minBlockX(), border - 1, last.maxBlockX(), border));
        }
        int border = neighbour.minBlockX();
        return Optional.of(new Area(start.dimension(), border - 1, start.minBlockZ(), border, last.maxBlockZ()));
    }

    private static long distanceToSeat(PolityView town, ChunkCoord chunk) {
        return town.seat().map(seat -> {
            long dx = seat.chunk().x() - chunk.x();
            long dz = seat.chunk().z() - chunk.z();
            return dx * dx + dz * dz;
        }).orElse(0L);
    }

    private void orderRoad(PolityView town, PersonRef planner, String name, Area area, Day day) {
        boolean paving = false;
        if (roads != null) {
            de.etiennebader.civ.domain.polity.Street street = new de.etiennebader.civ.domain.polity.Street(name, area);
            paving = roads.canAffordPaving(town, street);
            roads.start(town, street, paving);
        }
        announcements.add(Announcement.toTown(town.id(), Message.of(MessageKey.VILLAGER_STREET_FOUNDED,
                town.name().value(), name, Message.of(paving ? MessageKey.ROAD_TERM_PAVED : MessageKey.ROAD_TERM_PATH))));
    }

    private void publish(PolityView town, PlotId id, Area area, PlotType type, Day day) {
        Message typeName = Message.of(TranslationKeys.plotType(type));
        if (isCivic(type)) {
            announcements.add(Announcement.toTown(town.id(), Message.of(MessageKey.TOWN_PLOT_PLANNED, typeName,
                    area.width(), area.depth(), town.name().value())));
            return;
        }
        Money price = Money.of(area.size() * PRICE_PER_BLOCK, town.currency());
        app.offerPlot().execute(new OfferPlot.Command(town.id(), id, town.asActor(), Optional.of(price), day));
        announcements.add(Announcement.toTown(town.id(), Message.of(MessageKey.TOWN_PLOT_OFFERED, typeName,
                area.width(), area.depth(), texts.money(price), town.name().value())));
    }

    /** Was die Gemeinde als Nächstes braucht: Schule, Gotteshaus, Plätze, sonst Wohnen, Gewerbe, Industrie, Land. */
    private PlotType nextType(PolityView town, List<Plot> plots, PersonRef planner, Day day) {
        long villagers = town.members().stream().filter(m -> m.member() instanceof PersonRef).count();
        java.util.function.Predicate<PlotType> missing = type -> plots.stream().noneMatch(p -> p.type() == type);
        double roll = VillagerAmbitions.roll(planner, day, 0x97);
        if (villagers >= 6 && missing.test(PlotType.EDUCATION) && roll < 0.5) {
            return PlotType.EDUCATION;
        }
        if (villagers >= 10 && missing.test(PlotType.RELIGIOUS) && roll < 0.5) {
            return PlotType.RELIGIOUS;
        }
        long squares = plots.stream().filter(p -> p.type() == PlotType.PUBLIC).count();
        if (villagers >= 8 && squares < 1 + villagers / 12 && roll < 0.3) {
            return PlotType.PUBLIC;
        }
        double pick = VillagerAmbitions.roll(planner, day, 0x98);
        return pick < 0.6 ? PlotType.RESIDENTIAL : pick < 0.78 ? PlotType.COMMERCIAL
                : pick < 0.9 ? PlotType.INDUSTRIAL : PlotType.AGRICULTURAL;
    }

    private static int sizeOf(PlotType type) {
        return switch (type) {
            case RESIDENTIAL -> 10;
            case COMMERCIAL -> 12;
            default -> 14;
        };
    }

    static boolean isCivic(PlotType type) {
        return type == PlotType.PUBLIC || type == PlotType.EDUCATION || type == PlotType.RELIGIOUS;
    }

    // ------------------------------------------------------------------ Kaufen

    /** Dorfbewohner ohne Wohnland kaufen das billigste angebotene, wenn das Geld auch noch für ein Haus reicht. */
    private void buyLand(PolityView town, Day day) {
        WorldQueries world = app.queries();
        List<Plot> offers = world.land(town.id()).plots().stream()
                .filter(plot -> plot.type() == PlotType.RESIDENTIAL && plot.isForSale()
                        && plot.holder().equals(town.asActor()) && site.test(plot.area()))
                .sorted(Comparator.comparing((Plot plot) -> plot.askingPrice().orElseThrow().amount())
                        .thenComparing(plot -> plot.id().value())).toList();
        if (offers.isEmpty()) {
            return;
        }
        for (Membership member : town.members()) {
            if (!(member.member() instanceof PersonRef villager)
                    || VillagerAmbitions.roll(villager, day, 0x99) >= BUY_CHANCE
                    || !world.land(town.id()).plotsHeldBy(villager).stream()
                    .noneMatch(plot -> plot.type() == PlotType.RESIDENTIAL)) {
                continue;
            }
            long cash = world.ledger().balanceOf(villager, town.currency()).amount();
            for (Plot offer : offers) {
                Optional<Blueprint> cheapest = library.fittingFor(PlotType.RESIDENTIAL, offer.area().width(),
                        offer.area().depth()).stream().findFirst();
                if (cheapest.isEmpty() || offer.askingPrice().orElseThrow().amount()
                        + ConstructionService.priceOf(cheapest.get()) * 3 / 2 > cash) {
                    continue;
                }
                try {
                    app.buyPlot().execute(new BuyPlot.Command(town.id(), offer.id(), villager, day));
                    return; // ein Kauf je Gemeinde und Tag
                } catch (DomainException | IllegalStateException ignored) {
                    // schon verkauft oder zu wenig Geld: nächstes Angebot
                }
            }
        }
    }
}
