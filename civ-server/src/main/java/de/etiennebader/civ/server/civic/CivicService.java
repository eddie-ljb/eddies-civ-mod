package de.etiennebader.civ.server.civic;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.civic.Anthem;
import de.etiennebader.civ.domain.civic.Area;
import de.etiennebader.civ.domain.civic.Constituency;
import de.etiennebader.civ.domain.civic.CustomImage;
import de.etiennebader.civ.domain.civic.Flag;
import de.etiennebader.civ.domain.civic.ImageKind;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.plot.PlotService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Wahlkreise zuschneiden, Flagge und Hymne setzen und anzeigen.
 */
public final class CivicService {

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final Texts texts;
    private final Announcements announcements;
    private final PlotService plots;

    public CivicService(CivApplication app, Calendar calendar, Texts texts, Announcements announcements,
                        PlotService plots) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.plots = Objects.requireNonNull(plots, "plots");
    }

    // ---- Wahlkreise ----

    /** {@code wahlkreis anlegen <Name>} */
    public Reply define(Caller caller, PolityView polity, String name) {
        return attempt(() -> {
            Constituency created = app.civic().define(caller.player(), polity.id(), name, calendar.today());
            return Reply.success(Message.of(MessageKey.CONSTITUENCY_DEFINED, created.name(), polity.name().value()));
        });
    }

    /** {@code wahlkreis gebiet <Name>}: das mit dem Grundstücksstab markierte Rechteck hinzufügen. */
    public Reply addArea(Caller caller, PolityView polity, String name) {
        PlotService.Selection selection = plots.selection(caller.player());
        if (selection.first().isEmpty() || selection.second().isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CONSTITUENCY_NEEDS_SELECTION));
        }
        return attempt(() -> {
            Area area = Area.between(selection.first().get(), selection.second().get());
            Constituency updated = app.civic().addArea(caller.player(), polity.id(), name, area, calendar.today());
            return Reply.success(Message.of(MessageKey.CONSTITUENCY_AREA_ADDED, updated.name(), area.blocks(),
                    updated.areas().size()));
        });
    }

    /** {@code wahlkreis gemeinde <Name> <Gemeinde>} */
    public Reply addTown(Caller caller, PolityView polity, String name, String townName) {
        Optional<PolityView> town = queries.polityNamed(townName.strip());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, townName.strip()));
        }
        return attempt(() -> {
            Constituency updated = app.civic().addTown(caller.player(), polity.id(), name, town.get().id(),
                    calendar.today());
            return Reply.success(Message.of(MessageKey.CONSTITUENCY_TOWN_ADDED, town.get().name().value(),
                    updated.name()));
        });
    }

    /** {@code wahlkreis gebiet-entfernen <Name>}: das mit dem Grundstücksstab markierte Rechteck herausnehmen. */
    public Reply removeArea(Caller caller, PolityView polity, String name) {
        PlotService.Selection selection = plots.selection(caller.player());
        if (selection.first().isEmpty() || selection.second().isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CONSTITUENCY_NEEDS_SELECTION));
        }
        return attempt(() -> {
            Area area = Area.between(selection.first().get(), selection.second().get());
            Constituency updated = app.civic().removeArea(caller.player(), polity.id(), name, area, calendar.today());
            return Reply.success(Message.of(MessageKey.CONSTITUENCY_AREA_REMOVED, area.blocks(), updated.name(),
                    updated.areas().size()));
        });
    }

    /** {@code wahlkreis gemeinde-entfernen <Name> <Gemeinde>} */
    public Reply removeTown(Caller caller, PolityView polity, String name, String townName) {
        Optional<PolityView> town = queries.polityNamed(townName.strip());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, townName.strip()));
        }
        return attempt(() -> {
            Constituency updated = app.civic().removeTown(caller.player(), polity.id(), name, town.get().id(),
                    calendar.today());
            return Reply.success(Message.of(MessageKey.CONSTITUENCY_TOWN_REMOVED, town.get().name().value(),
                    updated.name()));
        });
    }

    public Reply clear(Caller caller, PolityView polity, String name) {
        return attempt(() -> {
            Constituency updated = app.civic().clear(caller.player(), polity.id(), name, calendar.today());
            return Reply.success(Message.of(MessageKey.CONSTITUENCY_CLEARED, updated.name()));
        });
    }

    public Reply remove(Caller caller, PolityView polity, String name) {
        return attempt(() -> {
            app.civic().remove(caller.player(), polity.id(), name, calendar.today());
            return Reply.success(Message.of(MessageKey.CONSTITUENCY_REMOVED, name.strip()));
        });
    }

    /** {@code wahlkreis liste}: Wahlkreise mit Gebieten, Gemeinden und Zahl der Wähler. */
    public Reply list(Caller caller, PolityView polity) {
        List<Constituency> own = queries.civic().constituenciesOf(polity.id());
        if (own.isEmpty()) {
            return Reply.success(Message.of(MessageKey.CONSTITUENCY_NONE, polity.name().value()));
        }
        Map<PolityId, Long> voters = votersPerDistrict(polity);
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.CONSTITUENCY_HEADER, polity.name().value(), own.size()));
        for (Constituency constituency : own) {
            lines.add(Message.of(MessageKey.CONSTITUENCY_ENTRY, constituency.name(), constituency.areas().size(),
                    constituency.areas().stream().mapToLong(Area::blocks).sum(),
                    constituency.towns().isEmpty() ? "-" : String.join(", ", constituency.towns().stream()
                            .map(texts::polityName).toList()),
                    voters.getOrDefault(constituency.id().asDistrict(), 0L)));
        }
        long outside = voters.entrySet().stream()
                .filter(entry -> queries.civic().byDistrict(entry.getKey()).isEmpty())
                .mapToLong(Map.Entry::getValue).sum();
        if (outside > 0) {
            lines.add(Message.of(MessageKey.CONSTITUENCY_OUTSIDE, outside));
        }
        return Reply.success(lines);
    }

    /** Wähler je Bezirk nach der Zuordnung, die auch die Auszählung nutzt. */
    public Map<PolityId, Long> votersPerDistrict(PolityView polity) {
        Function<ActorRef, Optional<PolityId>> districtOf = app.runElections().districtOf(polity);
        Map<PolityId, Long> counts = new LinkedHashMap<>();
        for (Membership membership : polity.members()) {
            districtOf.apply(membership.member())
                    .ifPresent(district -> counts.merge(district, 1L, Long::sum));
        }
        return counts;
    }

    // ---- Hoheitszeichen ----

    public Reply setFlag(Caller caller, PolityView polity, Optional<Flag> flag) {
        return attempt(() -> {
            app.civic().setFlag(caller.player(), polity.id(), flag, calendar.today());
            if (flag.isPresent()) {
                announcements.add(Announcement.toTown(polity.id(), Message.of(MessageKey.FLAG_SET,
                        polity.name().value(), caller.name())));
            }
            return Reply.success(Message.of(flag.isPresent() ? MessageKey.FLAG_SET : MessageKey.FLAG_REMOVED,
                    polity.name().value(), caller.name()));
        });
    }

    /** {@code hymne setzen <Titel> | [Instrument |] <Noten>} */
    public Reply setAnthem(Caller caller, PolityView polity, String input) {
        String[] parts = input.split("\\|");
        if (parts.length < 2) {
            return Reply.failure(Message.of(MessageKey.ANTHEM_FORMAT));
        }
        String title = parts[0].strip();
        String instrument = parts.length >= 3 ? parts[1].strip() : "harp";
        String notes = parts[parts.length - 1].strip();
        Anthem anthem;
        try {
            anthem = new Anthem(title, instrument, notes);
        } catch (IllegalArgumentException e) {
            return Reply.failure(Message.of(MessageKey.ANTHEM_INVALID, e.getMessage()));
        }
        return attempt(() -> {
            app.civic().setAnthem(caller.player(), polity.id(), Optional.of(anthem), calendar.today());
            announcements.add(Announcement.toTown(polity.id(), Message.of(MessageKey.ANTHEM_SET,
                    polity.name().value(), anthem.title())));
            return Reply.success(Message.of(MessageKey.ANTHEM_SET, polity.name().value(), anthem.title()));
        });
    }

    public Reply removeAnthem(Caller caller, PolityView polity) {
        return attempt(() -> {
            app.civic().setAnthem(caller.player(), polity.id(), Optional.empty(), calendar.today());
            return Reply.success(Message.of(MessageKey.ANTHEM_REMOVED, polity.name().value()));
        });
    }

    public Optional<Flag> flagOf(PolityId polity) {
        return queries.civic().flagOf(polity);
    }

    public Optional<Anthem> anthemOf(PolityId polity) {
        return queries.civic().anthemOf(polity);
    }

    // ---- Eigene Bilder (Wappen, Wahlplakat, Leuchtreklame) ----

    /**
     * Hinterlegt ein per Webseite hochgeladenes Bild. Verdrängte Bilder (bei einem Wappen-Wechsel das alte)
     * übergibt {@code onDisplaced} dem Aufrufer, damit er deren Dateien von der Platte löscht.
     */
    public Reply uploadImage(Caller caller, PolityView polity, ImageKind kind, String hash, int width, int height,
                             java.util.function.Consumer<CustomImage> onDisplaced) {
        return attempt(() -> {
            app.civic().uploadImage(caller.player(), polity.id(), kind, hash, width, height, calendar.today(),
                    onDisplaced);
            announcements.add(Announcement.toTown(polity.id(), Message.of(MessageKey.IMAGE_SET,
                    polity.name().value(), kind.name().toLowerCase(java.util.Locale.ROOT))));
            return Reply.success(Message.of(MessageKey.IMAGE_SET, polity.name().value(),
                    kind.name().toLowerCase(java.util.Locale.ROOT)));
        });
    }

    /** @return das entfernte Bild, dessen Datei der Aufrufer von der Platte löscht; leer ohne Berechtigung/Fund */
    public Optional<CustomImage> removeImage(Caller caller, String id) {
        try {
            return app.civic().removeImage(caller.player(), id, calendar.today());
        } catch (de.etiennebader.civ.domain.shared.DomainException | IllegalArgumentException
                | IllegalStateException e) {
            return Optional.empty();
        }
    }

    public List<CustomImage> imagesOf(PolityId polity) {
        return queries.civic().imagesOf(polity);
    }

    public List<CustomImage> imagesOf(PolityId polity, ImageKind kind) {
        return queries.civic().imagesOf(polity, kind);
    }

    public Optional<CustomImage> image(String id) {
        return queries.civic().image(id);
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Reply.failure(Message.of(MessageKey.CIVIC_REFUSED, e.getMessage()));
        }
    }
}
