package de.etiennebader.civ.server.town;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.ClaimWeighting;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.TerritoryView;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.CivCommands;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.territory.Relation;
import de.etiennebader.civ.server.territory.TerritoryWatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lesemodelle und Aktionen für die Gemeinde-Menüs: Steckbrief, Recht, Karte.
 */
public final class TownService {

    private final WorldQueries queries;
    private final ServerSettings settings;
    private final Texts texts;
    private final CivCommands commands;
    private final TerritoryWatcher territory;
    private final ParameterCatalog laws = ParameterCatalog.standard();

    public TownService(WorldQueries queries, ServerSettings settings, Texts texts, CivCommands commands,
                       TerritoryWatcher territory) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.territory = Objects.requireNonNull(territory, "territory");
    }

    /** Die Gemeinde, der der Spieler angehört. */
    public Optional<TownOverview> ownTown(ActorRef viewer) {
        return queries.municipalityOf(viewer).map(polity -> overview(polity, viewer));
    }

    /** Die Gemeinde, deren Gemeindehaus an dieser Stelle steht. */
    public Optional<TownOverview> townWithSeat(BlockPosition seat, ActorRef viewer) {
        return queries.polityWithSeat(seat).map(polity -> overview(polity, viewer));
    }

    public TownOverview overview(PolityView polity, ActorRef viewer) {
        TerritoryView land = queries.territory();
        List<String> members = new ArrayList<>();
        int villagers = 0;
        for (Membership membership : polity.members()) {
            if (membership.member() instanceof PersonRef) {
                villagers++;
            } else {
                members.add(texts.actor(membership.member()));
            }
        }
        String mayor = TownRoles.mayorOf(polity).map(texts::actor).orElse("-");
        CurrencyId currency = polity.currency();
        return new TownOverview(polity.id(), polity.name().value(), texts.tier(polity.tier()), polity.foundedOn().value(),
                mayor, members, villagers, land.territoryOf(polity.id()).size(), settings.claimLimit(ClaimWeighting.PERSONS_QUARTER.weightOf(polity)),
                land.countOf(polity.id(), ClaimType.CORE), land.countOf(polity.id(), ClaimType.OUTPOST),
                queries.ledger().balanceOf(polity.asActor(), currency), viewerOf(polity, viewer));
    }

    /** Alle Regelungen der Gemeinde mit geltendem Wert. */
    public List<LawSetting> laws(TownOverview town) {
        PolityView polity = queries.polity(town.id()).orElseThrow();
        List<LawSetting> settings = new ArrayList<>();
        for (ParameterKey<?> key : laws.keys()) {
            settings.add(new LawSetting(key.id(), Message.of(TranslationKeys.law(key)), polity.laws().valueOf(key)));
        }
        return settings;
    }

    /** Schaltet eine Ja/Nein-Regelung der eigenen Gemeinde um. */
    public Reply toggleLaw(Caller caller, LawSetting law) {
        if (!law.isSwitch()) {
            throw new IllegalArgumentException("Keine Ja/Nein-Regelung: " + law.id());
        }
        return commands.amendLaw(caller, law.id(), law.enabled() ? "nein" : "ja");
    }

    /** Ändert eine Zahlen-Regelung der eigenen Gemeinde um {@code delta}, nicht unter 0. */
    public Reply adjustLaw(Caller caller, LawSetting law, long delta) {
        long value = Math.max(0, law.number() + delta);
        return commands.amendLaw(caller, law.id(), Long.toString(value));
    }

    /** Karte der Chunks um den Spieler; {@code width} und {@code height} sollten ungerade sein. */
    public ChunkMap map(Caller caller, int width, int height) {
        ChunkCoord center = caller.chunk();
        TerritoryView land = queries.territory();
        List<MapCell> cells = new ArrayList<>(width * height);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                ChunkCoord chunk = new ChunkCoord(center.dimension(), center.x() + column - width / 2,
                        center.z() + row - height / 2);
                Optional<Claim> claim = land.claimAt(chunk);
                Relation relation = territory.relation(caller.player(), claim.map(Claim::polity));
                cells.add(new MapCell(chunk, relation, claim.map(found -> texts.polityName(found.polity())),
                        claim.map(Claim::type), chunk.equals(center)));
            }
        }
        return new ChunkMap(width, height, cells);
    }

    /** Beansprucht einen Chunk aus der Karte für die eigene Gemeinde. */
    public Reply claimFromMap(Caller caller, ChunkCoord chunk, ClaimType type) {
        BlockPosition inChunk = chunk.center(caller.position().y());
        return commands.claim(new Caller(caller.player(), caller.name(), inChunk), type);
    }

    private static Viewer viewerOf(PolityView polity, ActorRef viewer) {
        if (TownRoles.isMayor(polity, viewer)) {
            return Viewer.MAYOR;
        }
        if (TownRoles.isCitizen(polity, viewer)) {
            return Viewer.CITIZEN;
        }
        return polity.isMember(viewer) ? Viewer.RESIDENT : Viewer.OUTSIDER;
    }

    /** Hilfstext für Steckbriefe: Gebiet „3 / 13“. */
    public static Message territorySummary(TownOverview town) {
        return Message.of(MessageKey.TOWN_TERRITORY, town.chunks(), town.claimLimit(), town.coreChunks(), town.outposts());
    }
}
