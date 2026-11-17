package de.etiennebader.civ.server.command;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.day.DayReport;
import de.etiennebader.civ.application.law.EffectiveLaws;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.territory.ClaimChunk;
import de.etiennebader.civ.application.territory.ReleaseChunk;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.TerritoryView;
import de.etiennebader.civ.server.protection.ProtectionService;
import de.etiennebader.civ.server.vote.VotingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Die Logik hinter den {@code /civ}-Befehlen, ohne Minecraft: nimmt den aufrufenden Spieler
 * entgegen, ruft die Use Cases auf und liefert übersetzbare Antworten.
 *
 * <p>Fachliche Ablehnungen ({@link DomainException}) werden zu Fehlerantworten. Alle Methoden
 * laufen auf dem Spielthread.
 */
public final class CivCommands {

    /** Ticks pro Minute Echtzeit bei 20 Ticks pro Sekunde. */
    private static final long TICKS_PER_MINUTE = 1_200;

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final CurrencyId currency;
    private final Texts texts;
    private final ProtectionService protection;
    private final VotingService voting;
    private final ParameterCatalog laws = ParameterCatalog.standard();

    public CivCommands(CivApplication app, Calendar calendar, CurrencyId currency, Texts texts,
                       ProtectionService protection, VotingService voting) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.voting = Objects.requireNonNull(voting, "voting");
    }

    /**
     * Gründung über ein frisch gesetztes Gemeindehaus: Der Name kommt aus dem im Amboss benannten Block.
     *
     * @param name leer, wenn der Block keinen Namen trägt
     */
    public Reply foundWithTownHall(Caller caller, Optional<String> name, BlockPosition townHall) {
        return name.filter(found -> !found.isBlank())
                .map(found -> found(caller, found, townHall))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.TOWN_HALL_NEEDS_NAME)));
    }

    /** {@code /civ gruenden <Name>} (nur Admins): Gemeinde ohne Gemeindehaus-Block, Sitz ist der Standort des Admins. */
    public Reply found(Caller caller, String name) {
        return found(caller, name, caller.position());
    }

    private Reply found(Caller caller, String name, BlockPosition seat) {
        return attempt(() -> {
            PolityName polityName = PolityName.of(name);
            app.foundPolity().execute(new FoundPolity.Command(polityName, caller.player(), currency, seat,
                    calendar.today()));
            return Reply.success(Message.of(MessageKey.FOUNDED, polityName.value()));
        });
    }

    /** {@code /civ beitreten <Name>} */
    public Reply join(Caller caller, String name) {
        return withPolityNamed(name, polity -> attempt(() -> {
            app.joinPolity().execute(new JoinPolity.Command(polity.id(), caller.player(), MembershipType.CITIZEN,
                    calendar.today()));
            return Reply.success(Message.of(MessageKey.JOINED, polity.name().value()));
        }));
    }

    /** {@code /civ austreten} */
    public Reply leave(Caller caller) {
        return withMunicipality(caller, polity -> attempt(() -> {
            app.leavePolity().execute(new LeavePolity.Command(polity.id(), caller.player(), calendar.today()));
            return Reply.success(Message.of(MessageKey.LEFT, polity.name().value()));
        }));
    }

    /** {@code /civ beanspruchen [aussenposten]}: beansprucht den Chunk, in dem der Spieler steht. */
    public Reply claim(Caller caller, ClaimType type) {
        Objects.requireNonNull(type, "type");
        ChunkCoord chunk = caller.chunk();
        return withMunicipality(caller, polity -> attempt(() -> {
            app.claimChunk().execute(new ClaimChunk.Command(polity.id(), chunk, type, calendar.today()));
            return Reply.success(Message.of(MessageKey.CLAIMED, chunk.x(), chunk.z(), polity.name().value(),
                    texts.claimType(type)));
        }));
    }

    /** {@code /civ freigeben}: gibt den Chunk auf, in dem der Spieler steht. */
    public Reply release(Caller caller) {
        ChunkCoord chunk = caller.chunk();
        return withMunicipality(caller, polity -> attempt(() -> {
            app.releaseChunk().execute(new ReleaseChunk.Command(polity.id(), chunk, calendar.today()));
            return Reply.success(Message.of(MessageKey.RELEASED, chunk.x(), chunk.z(), polity.name().value()));
        }));
    }

    /** {@code /civ} oder {@code /civ info}: eigene Gemeinde, aktueller Chunk, Kontostand und Tag. */
    public Reply info(Caller caller) {
        List<Message> lines = new ArrayList<>();
        Optional<PolityView> municipality = queries.municipalityOf(caller.player());
        lines.add(municipality
                .map(polity -> Message.of(MessageKey.INFO_MUNICIPALITY, polity.name().value()))
                .orElseGet(() -> Message.of(MessageKey.INFO_NO_MUNICIPALITY)));

        ChunkCoord chunk = caller.chunk();
        Optional<Claim> claim = queries.territory().claimAt(chunk);
        lines.add(claim
                .map(found -> Message.of(MessageKey.INFO_CHUNK_CLAIMED, chunk.x(), chunk.z(),
                        texts.polityName(found.polity()), texts.claimType(found.type())))
                .orElseGet(() -> Message.of(MessageKey.INFO_CHUNK_WILDERNESS, chunk.x(), chunk.z())));

        lines.add(Message.of(MessageKey.INFO_BALANCE, texts.money(queries.ledger().balanceOf(caller.player(), currency))));
        lines.add(Message.of(MessageKey.INFO_DAY, calendar.today().value(), minutesUntilEndOfDay()));
        return Reply.success(lines);
    }

    /** {@code /civ gemeinde [Name]}: Steckbrief einer Gemeinde, ohne Namen der eigenen. */
    public Reply describe(Caller caller, Optional<String> name) {
        Function<PolityView, Reply> describe = polity -> Reply.success(describe(polity));
        return name.map(found -> withPolityNamed(found, describe)).orElseGet(() -> withMunicipality(caller, describe));
    }

    /** Rechtsklick auf ein Gemeindehaus: Steckbrief der Gemeinde, deren Sitz es ist. */
    public Reply describeTownHall(BlockPosition townHall) {
        return queries.polityWithSeat(townHall)
                .map(polity -> Reply.success(describe(polity)))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.TOWN_HALL_ORPHANED)));
    }

    /** {@code /civ recht [Gemeinde]}: alle Regelungen mit ihrem geltenden Wert. */
    public Reply laws(Caller caller, Optional<String> name) {
        Function<PolityView, Reply> list = polity -> {
            List<Message> lines = new ArrayList<>();
            lines.add(Message.of(MessageKey.LAWS_HEADER, polity.name().value()));
            EffectiveLaws effective = queries.effectiveLaws(polity);
            for (ParameterKey<?> key : laws.keys()) {
                Optional<PolityView> federal = effective.overriddenBy(key);
                lines.add(federal.isPresent()
                        ? Message.of(MessageKey.LAW_ENTRY_FEDERAL, Message.of(TranslationKeys.law(key)), key.id(),
                                texts.lawValue(effective.valueOf(key)), federal.get().name().value())
                        : Message.of(MessageKey.LAW_ENTRY, Message.of(TranslationKeys.law(key)), key.id(),
                                texts.lawValue(polity.laws().valueOf(key))));
            }
            return Reply.success(lines);
        };
        return name.map(found -> withPolityNamed(found, list)).orElseGet(() -> withMunicipality(caller, list));
    }

    /**
     * {@code /civ recht setzen <Regelung> <Wert>}: ein Bürger ändert eine Regelung seiner Gemeinde.
     * Wahrheitswerte dürfen als ja/nein, an/aus oder true/false angegeben werden.
     */
    public Reply amendLaw(Caller caller, String lawId, String value) {
        Optional<ParameterKey<?>> key = laws.find(lawId.strip());
        if (key.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_LAW, lawId.strip()));
        }
        return withMunicipality(caller, polity -> amendLaw(caller, polity, key.get(), value));
    }

    /**
     * Ändert eine Regelung einer bestimmten Gemeinde oder eines Bundes: per Abstimmung, wenn nötig, sonst als
     * Verordnung der Spitze.
     */
    public Reply amendLaw(Caller caller, PolityView polity, ParameterKey<?> key, String value) {
        return attempt(() -> {
            ParameterValue<?> parsed;
            try {
                parsed = laws.decode(key.id(), normalizeLawValue(value));
            } catch (IllegalArgumentException e) {
                return Reply.failure(Message.of(MessageKey.INVALID_LAW_VALUE, value.strip(), key.id()));
            }
            if (VotingService.votingRequired(polity, key)) {
                return voting.propose(caller, polity, parsed);
            }
            if (!polity.holds(caller.player(), Office.MAYOR)) {
                return Reply.failure(Message.of(MessageKey.ONLY_MAYOR_DECREES, caller.name()));
            }
            app.amendLaw().execute(new AmendLaw.Command(polity.id(), caller.player(), parsed, calendar.today()));
            return Reply.success(Message.of(MessageKey.LAW_AMENDED, polity.name().value(),
                    Message.of(TranslationKeys.law(key)), texts.lawValue(parsed.value())));
        });
    }

    /** {@code /civ admin umgehen} (nur Admins): Gebietsschutz für sich selbst ein- oder ausschalten. */
    public Reply toggleProtectionBypass(Caller caller) {
        boolean bypassing = protection.toggleBypass(caller.player());
        return Reply.success(Message.of(bypassing ? MessageKey.BYPASS_ON : MessageKey.BYPASS_OFF));
    }

    /** Namen der Regelungen für die Vervollständigung im Chat. */
    public List<String> lawIds() {
        return laws.keys().stream().map(ParameterKey::id).toList();
    }

    /** {@code /civ liste}: alle Gemeinden in Gründungsreihenfolge. */
    public Reply list() {
        List<PolityView> polities = queries.polities();
        if (polities.isEmpty()) {
            return Reply.success(Message.of(MessageKey.LIST_EMPTY));
        }
        TerritoryView territory = queries.territory();
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.LIST_HEADER, polities.size()));
        for (PolityView polity : polities) {
            lines.add(Message.of(MessageKey.LIST_ENTRY, polity.name().value(), polity.memberCount(),
                    territory.territoryOf(polity.id()).size()));
        }
        return Reply.success(lines);
    }

    /** {@code /civ hilfe}: Überblick über alle Befehle und Blöcke. */
    public Reply help() {
        return Reply.success(Message.of(MessageKey.HELP_HEADER), Message.of(MessageKey.HELP_TOWN),
                Message.of(MessageKey.HELP_JOIN), Message.of(MessageKey.HELP_LAND), Message.of(MessageKey.HELP_LAWS),
                Message.of(MessageKey.HELP_VOTES), Message.of(MessageKey.HELP_OFFICES), Message.of(MessageKey.HELP_FEDERATION), Message.of(MessageKey.HELP_LEVELS), Message.of(MessageKey.HELP_SHOW), Message.of(MessageKey.HELP_COURT), Message.of(MessageKey.HELP_HANDBOOK), Message.of(MessageKey.HELP_CONSTITUTION), Message.of(MessageKey.HELP_LAW_BOOK), Message.of(MessageKey.HELP_PARTIES), Message.of(MessageKey.HELP_PARLIAMENT), Message.of(MessageKey.HELP_CAMPAIGN), Message.of(MessageKey.HELP_MOOD), Message.of(MessageKey.HELP_PLOTS),
                Message.of(MessageKey.HELP_SHOPS), Message.of(MessageKey.HELP_CIVIL), Message.of(MessageKey.HELP_CONTRACTS),
                Message.of(MessageKey.HELP_MARKET_PRESS),
                Message.of(MessageKey.HELP_BANK), Message.of(MessageKey.HELP_MARKET), Message.of(MessageKey.HELP_INFO));
    }

    /** Begrüßung beim Betreten des Servers. */
    public Message welcome(Caller caller) {
        return queries.municipalityOf(caller.player())
                .map(polity -> Message.of(MessageKey.WELCOME, caller.name(), polity.name().value()))
                .orElseGet(() -> Message.of(MessageKey.WELCOME, caller.name(), Message.of(MessageKey.TERRITORY_WILDERNESS)));
    }

    /** Die Ankündigung an alle Spieler, wenn ein neuer Tag beginnt. */
    public Message newDay(DayReport finished) {
        Objects.requireNonNull(finished, "finished");
        return Message.of(MessageKey.NEW_DAY, finished.day().next().value(),
                texts.money(finished.landTaxCollected(currency)));
    }

    private List<Message> describe(PolityView polity) {
        TerritoryView territory = queries.territory();
        List<String> members = new ArrayList<>();
        for (Membership membership : polity.members()) {
            members.add(texts.actor(membership.member()));
        }
        return List.of(
                Message.of(MessageKey.POLITY_HEADER, polity.name().value()),
                Message.of(MessageKey.POLITY_DETAILS, texts.tier(polity.tier()), polity.foundedOn().value()),
                Message.of(MessageKey.POLITY_MEMBERS, polity.memberCount(), String.join(", ", members)),
                Message.of(MessageKey.POLITY_TERRITORY, territory.territoryOf(polity.id()).size(),
                        territory.countOf(polity.id(), ClaimType.CORE), territory.countOf(polity.id(), ClaimType.OUTPOST)));
    }

    private Reply withMunicipality(Caller caller, Function<PolityView, Reply> action) {
        return queries.municipalityOf(caller.player())
                .map(action)
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)));
    }

    private Reply withPolityNamed(String name, Function<PolityView, Reply> action) {
        return queries.polityNamed(name)
                .map(action)
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, name.strip())));
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    private static String normalizeLawValue(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "ja", "an", "yes", "on" -> "true";
            case "nein", "aus", "no", "off" -> "false";
            case "relativ", "relative", "fptp", "mehrheit_relativ", "einfach" -> "plurality";
            case "stichwahl", "absolut", "absolute" -> "runoff";
            case "saintelague", "sainte-lague", "sainte_laguë", "webster" -> "sainte_lague";
            case "d'hondt", "d_hondt", "hondt" -> "dhondt";
            case "hare", "hare-niemeyer", "niemeyer" -> "hare_niemeyer";
            case "mehrheit", "mehrheitswahl" -> "majority";
            case "wahlkreise", "wahlkreis" -> "districts";
            case "gemischt", "personalisiert", "personalisierte" -> "mixed";
            case "personen", "personenwahl", "personal" -> "person";
            case "liste", "listen", "listenwahl" -> "list";
            default -> value.strip();
        };
    }

    private long minutesUntilEndOfDay() {
        return Math.ceilDiv(calendar.ticksUntilEndOfDay(), TICKS_PER_MINUTE);
    }
}
