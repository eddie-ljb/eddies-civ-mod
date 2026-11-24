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
                    .ifPresent(planner -> plan(town, planner, day));
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
            try {
                PlotId id = app.createPlot().execute(new CreatePlot.Command(town.id(), area, type, day));
                publish(town, id, area, type, day);
                return;
            } catch (DomainException | IllegalArgumentException | IllegalStateException ignored) {
                // überlappt oder liegt nicht ganz im Gebiet: nächster Chunk
            }
        }
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
