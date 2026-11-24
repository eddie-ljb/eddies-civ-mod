package de.etiennebader.civ.server.construction;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.construction.Blueprint;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.production.Branch;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Bauen im Auftrag (Issue #19).
 *
 * <ul>
 *     <li>Der Inhaber eines Grundstücks beauftragt eine <b>Baufirma</b> (Branche {@link Branch#CONSTRUCTION}) mit
 *         einem Bauplan. Die Firma wird nach Fortschritt bezahlt, vom Bauherrn an die Firmenkasse; darin stecken
 *         Material und Löhne der Bauarbeiter. Fehlt dem Bauherrn das Geld, ruht die Baustelle.</li>
 *     <li>Verlangt das Gesetz {@link StandardParameters#BUILD_PERMIT_REQUIRED} eine Genehmigung, entscheidet das
 *         Bauamt (Planer, sonst Bürgermeister). Die Gebühr {@link StandardParameters#BUILD_PERMIT_FEE} fließt dabei
 *         in die Gemeindekasse.</li>
 *     <li>Die Bauarbeiter sind die Angestellten der Firma: Je Arbeiter entstehen {@value #BLOCKS_PER_WORKER_DAY}
 *         Blöcke am Tag, mindestens der Firmenchef allein. Das Spiel holt sich mit {@link #advance} die Blöcke,
 *         die jetzt zu setzen sind, und baut so sichtbar Stück für Stück.</li>
 *     <li>Dorfbewohner mit Wohngrundstück beauftragen von selbst eine Baufirma ihrer Gemeinde
 *         ({@link #villagersOrder}); ein Dorfbewohner als Bauamt genehmigt von selbst.</li>
 * </ul>
 */
public final class ConstructionService {

    public static final int BLOCKS_PER_WORKER_DAY = 120;
    /** Preis je Block in kleinster Währungseinheit, inklusive Material und Lohn. */
    public static final long PRICE_PER_BLOCK = 25;
    /** Anteil des Preises, der bei eigenem Material für Arbeit bleibt, in Prozent. */
    static final int LABOUR_PERCENT = 40;
    /** Ticks, nach denen ein blockiertes Baufeld erneut geprüft wird. */
    static final long RECHECK_TICKS = 600;
    /** Höchstens so viele Blöcke je Aufruf von {@link #advance} und Baustelle. */
    static final int MAX_BLOCKS_PER_CALL = 200;
    /** Tageswahrscheinlichkeit, dass ein Dorfbewohner einen Bau in Auftrag gibt. */
    static final double VILLAGER_ORDERS = 0.2;
    /** Tageswahrscheinlichkeit, dass die Leitung einer Gemeinde, Firma, Zeitung oder Partei einen Bau in Auftrag gibt. */
    static final double ORGANISATION_ORDERS = 0.35;

    /**
     * Zu setzende Blöcke einer Baustelle. Die Blöcke liegen bereits gedreht relativ zur Ecke {@code origin}; das Spiel
     * dreht nur noch die Blockzustände um {@code rotation} Vierteldrehungen.
     *
     * @param workers die Bauarbeiter (Angestellte der Firma), die sichtbar zur Baustelle gehen sollen
     */
    public record Build(UUID project, BlockPosition origin, int rotation, List<Blueprint.Placement> blocks,
                        List<UUID> workers, boolean first, int footprintX, int footprintZ, int sizeY) {
    }

    /**
     * Eine Baustelle, die abgesperrt wird.
     *
     * @param min    Ecke des Baufelds (kleinste Koordinaten, Höhe der untersten Ebene)
     * @param limit  Grundstück; die Absperrung bleibt darin
     * @param active ob noch gebaut wird (Absperrung aufstellen) oder fertig ist (Absperrung abbauen)
     */
    public record Site(UUID project, BlockPosition min, int sizeX, int sizeZ, de.etiennebader.civ.domain.land.Area limit,
                       boolean active) {
    }

    /** Drehung "automatisch": die Tür zeigt zur Straße. */
    public static final int AUTO = -1;

    /** Was das Spiel über das Gelände einer Baustelle weiß. */
    public interface Terrain {

        boolean isLoaded(BlockPosition position);

        /** Höchste Geländehöhe (erste freie Ebene über dem Boden) im Rechteck; die unterste Ebene des Plans liegt dort. */
        int groundLevel(BlockPosition min, int sizeX, int sizeZ);

        /**
         * Höhe der untersten Planebene, sodass die Haustür auf Straßenhöhe liegt: Der Boden vor der Tür ist der Maßstab
         * (Oberfläche dort minus Höhe der Tür im Plan). Ohne Tür zählt die Mitte des Baufelds.
         *
         * @param doorFront Fläche unmittelbar vor der Tür (Höhe unwichtig); leer ohne Tür
         * @param doorHeight Höhe der unteren Türhälfte im Plan über der untersten Ebene
         */
        default int floorLevel(BlockPosition corner, int sizeX, int sizeZ, Optional<BlockPosition> doorFront,
                               int doorHeight) {
            return groundLevel(corner, sizeX, sizeZ);
        }

        /** Ob im Quader nur Luft und Natur (Pflanzen, Laub, Holz) stehen, also nichts Gebautes überschrieben würde. */
        boolean isClear(BlockPosition min, int sizeX, int sizeY, int sizeZ);

        /** Der Blockname an der Stelle (z. B. {@code minecraft:oak_planks}); {@code null}, wenn nicht geladen. */
        default String blockName(BlockPosition position) {
            return null;
        }
    }

    /** Ob auf einer Fläche schon etwas gebaut wurde, mit oder ohne Bauauftrag. */
    @FunctionalInterface
    public interface SiteSurvey {

        /** @return ob dort Gebautes steht; leer, wenn das Gelände nicht geladen ist und man es noch nicht weiß */
        Optional<Boolean> isBuiltUp(de.etiennebader.civ.domain.land.Area area);
    }

    private final CivApplication app;
    private final WorldQueries queries;
    private final de.etiennebader.civ.application.day.Calendar calendar;
    private final Texts texts;
    private final BlueprintLibrary library;
    private final ProjectStore store;
    private final Map<UUID, ConstructionProject> projects = new java.util.LinkedHashMap<>();
    private final Map<UUID, Double> carry = new HashMap<>();
    private final Map<UUID, Long> blockedUntil = new HashMap<>();
    private final Map<UUID, Long> blockedSince = new HashMap<>();
    /** Grundstücke, deren Baufeld so lange blockiert war, dass der Auftrag aufgegeben wurde; bis zum Neustart. */
    private final java.util.Set<PlotId> abandoned = new java.util.HashSet<>();
    /** Nach so vielen Tagen blockiertem Baufeld wird ein Auftrag ohne Baubeginn aufgegeben. */
    static final int ABANDON_DAYS = 3;
    private final de.etiennebader.civ.server.Announcements announcements;
    private final TownPlanning planning;
    private SiteSurvey survey = area -> Optional.of(false);
    private final Map<de.etiennebader.civ.domain.land.Area, Optional<Boolean>> surveyed = new HashMap<>();
    private java.util.function.Function<String, Optional<PlayerRef>> playerLookup = name -> Optional.empty();
    private final Map<UUID, Long> reviewSince = new HashMap<>();
    /** Anteil des Preises, den eine Baufirma behält, wenn sie einen Auftrag an einen Spieler weitergibt. */
    static final int COMMISSION_PERCENT = 10;
    /** Ab diesem Anteil passender Blöcke gilt ein Spieler-Bau als fertig. */
    static final int DONE_PERCENT = 95;
    /** Nach so vielen Tagen ohne Antwort verfällt eine Anfrage an Firma oder Spieler. */
    static final int REVIEW_DAYS = 3;
    private boolean interimGovernment = true;
    private long clock;
    private final long ticksPerDay;
    private int lastNumber;

    public ConstructionService(CivApplication app, de.etiennebader.civ.application.day.Calendar calendar, Texts texts,
                               long ticksPerDay, BlueprintLibrary library, Optional<java.nio.file.Path> file,
                               de.etiennebader.civ.server.Announcements announcements) {
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.ticksPerDay = ticksPerDay;
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.library = Objects.requireNonNull(library, "library");
        this.store = new ProjectStore(file);
        this.planning = new TownPlanning(app, library, announcements, texts);
        this.planning.authority(this::authority);
        this.planning.site(this::siteFree);
        for (ConstructionProject project : store.load()) {
            projects.put(project.id(), project);
            lastNumber = Math.max(lastNumber, project.number());
        }
    }

    /** Wegebau für die Straßen, die das Bauamt gründet. */
    public synchronized void roads(de.etiennebader.civ.server.road.RoadService roads) {
        this.planning.roads(roads);
    }

    public BlueprintLibrary library() {
        return library;
    }

    public synchronized List<ConstructionProject> projects() {
        return new ArrayList<>(projects.values());
    }

    /**
     * Wer prüfen kann, ob auf einer Fläche schon gebaut wurde; das Spiel setzt ihn. Ohne ihn gilt jede Fläche als frei.
     */
    public synchronized void siteSurvey(SiteSurvey survey) {
        this.survey = Objects.requireNonNull(survey, "survey");
    }

    /** Wie Spieler heißen, wenn man sie als Auftragnehmer nennt. */
    public synchronized void playerLookup(java.util.function.Function<String, Optional<PlayerRef>> lookup) {
        this.playerLookup = Objects.requireNonNull(lookup, "lookup");
    }

    /**
     * Ob die Fläche sicher frei von Gebautem ist. Selbstständig handelnde Dorfbewohner und Organisationen bestellen nur
     * dort, sonst würden sie vorhandene Gebäude überbauen, auch wenn nie jemand einen Bauauftrag dafür gestellt hat.
     * Ist das Gelände nicht geladen, bleibt es offen und der Auftrag wartet.
     */
    private boolean siteFree(de.etiennebader.civ.domain.land.Area area) {
        return surveyed.computeIfAbsent(area, survey::isBuiltUp).map(built -> !built).orElse(false);
    }

    // ------------------------------------------------------------------ Befehle

    /** Beauftragt die Baufirma, auf dem Grundstück unter dem Spieler zu bauen. */
    public synchronized Reply order(Caller caller, String planName, String firmName) {
        return order(caller, planName, firmName, false, AUTO);
    }

    /**
     * Wie {@link #order(Caller, String, String)}, aber mit Wahl des Materials und der Drehung.
     *
     * @param selfSupplied ob der Bauherr das Material selbst liefert ({@link #deliver}); dann kostet nur die Arbeit
     * @param degrees      Drehung des Plans: 0, 90, 180 oder 270 Grad im Uhrzeigersinn
     */
    public synchronized Reply order(Caller caller, String planName, String firmName, boolean selfSupplied,
                                    int degrees) {
        return order(caller, planName, firmName, selfSupplied, degrees, "");
    }

    /**
     * Wie {@link #order(Caller, String, String, boolean, int)}; nennt der Bauherr einen Spieler, soll dieser selbst
     * bauen. Die Baufirma muss zustimmen ({@link #answerFirm}), danach der Spieler ({@link #answerJob}); bezahlt wird er
     * nach verifiziertem Baufortschritt, die Firma behält {@value #COMMISSION_PERCENT} % Provision.
     */
    public synchronized Reply order(Caller caller, String planName, String firmName, boolean selfSupplied,
                                    int degrees, String contractorName) {
        Optional<PlayerRef> contractor = Optional.empty();
        if (!contractorName.isBlank()) {
            contractor = playerLookup.apply(contractorName.strip());
            if (contractor.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, contractorName.strip()));
            }
            selfSupplied = false;
        }
        if (degrees != AUTO && (degrees < 0 || degrees % 90 != 0 || degrees > 270)) {
            return Reply.failure(Message.of(MessageKey.BUILD_BAD_ROTATION));
        }
        Optional<Blueprint> plan = library.find(planName);
        if (plan.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.BUILD_UNKNOWN_PLAN, planName.strip()));
        }
        Optional<PolityId> town = queries.territory().sovereignOf(caller.chunk());
        Optional<Plot> plot = town.flatMap(id -> queries.land(id)
                .plotAt(caller.position().dimension(), caller.position().x(), caller.position().z()));
        if (plot.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.BUILD_NO_PLOT));
        }
        Optional<PolityView> firm = queries.companies().stream()
                .filter(company -> company.name().value().equalsIgnoreCase(firmName.strip())).findFirst();
        if (firm.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.BUILD_UNKNOWN_FIRM, firmName.strip()));
        }
        try {
            ConstructionProject project = place(caller.player(), town.get(), plot.get(), plan.get(), firm.get(),
                    calendar.today(), selfSupplied, degrees == AUTO ? AUTO : degrees / 90);
            if (contractor.isPresent()) {
                project = startReview(project, contractor.get(), ConstructionProject.Contractor.FIRM_REVIEW, firm.get());
            }
            Message price = texts.money(Money.of(project.price(), firm.get().currency()));
            return Reply.success(Message.of(project.status() == ConstructionProject.Status.AWAITING_PERMIT
                            ? MessageKey.BUILD_ORDERED_PERMIT : MessageKey.BUILD_ORDERED, project.number(),
                    plan.get().name(), firm.get().name().value(), price));
        } catch (BuildRefused refused) {
            return Reply.failure(refused.reply);
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    private ConstructionProject startReview(ConstructionProject project, PlayerRef contractor,
                                            ConstructionProject.Contractor state, PolityView firm) {
        ConstructionProject updated = project.withContractor(Optional.of(contractor), state);
        put(updated);
        reviewSince.put(updated.id(), clock);
        if (state == ConstructionProject.Contractor.FIRM_REVIEW) {
            firm.mayor().filter(PlayerRef.class::isInstance).map(PlayerRef.class::cast).ifPresent(head ->
                    announcements.add(de.etiennebader.civ.server.Announcement.toPlayer(head, Message.of(
                            MessageKey.BUILD_NOTICE_FIRM_REVIEW, updated.number(), texts.actor(updated.client()),
                            updated.blueprint(), texts.actor(contractor)))));
        } else {
            announcements.add(de.etiennebader.civ.server.Announcement.toPlayer(contractor, Message.of(
                    MessageKey.BUILD_NOTICE_PLAYER_REVIEW, updated.number(), firm.name().value(),
                    updated.blueprint())));
        }
        return updated;
    }

    private boolean isHead(PlayerRef player, ConstructionProject project) {
        return queries.polity(project.builder()).map(firm -> firm.holds(player, Office.MAYOR)).orElse(false);
    }

    /** Die Baufirma gibt einen Auftrag, der noch nicht begonnen hat, an einen Spieler weiter, der selbst baut. */
    public synchronized Reply subcontract(Caller caller, int number, String playerName) {
        Optional<ConstructionProject> project = byNumber(number).filter(found -> found.status().isActive());
        if (project.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.BUILD_UNKNOWN_PROJECT, number));
        }
        if (!isHead(caller.player(), project.get())) {
            return Reply.failure(Message.of(MessageKey.BUILD_NOT_FIRM_HEAD));
        }
        if (project.get().placed() > 0 || project.get().contractorState() != ConstructionProject.Contractor.NONE) {
            return Reply.failure(Message.of(MessageKey.BUILD_NOT_REASSIGNABLE, number));
        }
        Optional<PlayerRef> contractor = playerLookup.apply(playerName.strip());
        if (contractor.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, playerName.strip()));
        }
        PolityView firm = queries.polity(project.get().builder()).orElseThrow();
        ConstructionProject.Contractor next = contractor.get().equals(caller.player())
                ? ConstructionProject.Contractor.ACTIVE : ConstructionProject.Contractor.PLAYER_REVIEW;
        if (next == ConstructionProject.Contractor.ACTIVE) {
            put(project.get().withContractor(contractor, next));
        } else {
            startReview(project.get(), contractor.get(), next, firm);
        }
        return Reply.success(Message.of(MessageKey.BUILD_CONTRACTOR_OFFERED, number, texts.actor(contractor.get())));
    }

    /** Die Baufirma stimmt einem vom Bauherrn gewünschten Spieler-Bau zu oder lehnt ihn ab. */
    public synchronized Reply answerFirm(Caller caller, int number, boolean accept) {
        Optional<ConstructionProject> project = byNumber(number).filter(found -> found.status().isActive()
                && found.contractorState() == ConstructionProject.Contractor.FIRM_REVIEW);
        if (project.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.BUILD_UNKNOWN_PROJECT, number));
        }
        if (!isHead(caller.player(), project.get())) {
            return Reply.failure(Message.of(MessageKey.BUILD_NOT_FIRM_HEAD));
        }
        return answerFirm(project.get(), accept);
    }

    private Reply answerFirm(ConstructionProject project, boolean accept) {
        reviewSince.remove(project.id());
        if (!accept) {
            put(project.withStatus(ConstructionProject.Status.CANCELLED));
            notifyClient(project, MessageKey.BUILD_FIRM_DECLINED);
            return Reply.success(Message.of(MessageKey.BUILD_FIRM_DECLINED, project.number()));
        }
        PlayerRef contractor = project.contractor().orElseThrow();
        PolityView firm = queries.polity(project.builder()).orElseThrow();
        if (isHead(contractor, project)) {
            put(project.withContractor(project.contractor(), ConstructionProject.Contractor.ACTIVE));
        } else {
            startReview(project, contractor, ConstructionProject.Contractor.PLAYER_REVIEW, firm);
        }
        notifyClient(project, MessageKey.BUILD_FIRM_ACCEPTED);
        return Reply.success(Message.of(MessageKey.BUILD_FIRM_ACCEPTED, project.number()));
    }

    /** Der vorgesehene Spieler nimmt den Bau an oder lehnt ihn ab; dann baut wieder die Firma. */
    public synchronized Reply answerJob(Caller caller, int number, boolean accept) {
        Optional<ConstructionProject> project = byNumber(number).filter(found -> found.status().isActive()
                && found.contractorState() == ConstructionProject.Contractor.PLAYER_REVIEW
                && found.contractor().filter(caller.player()::equals).isPresent());
        if (project.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.BUILD_NOT_CONTRACTOR, number));
        }
        reviewSince.remove(project.get().id());
        put(accept ? project.get().withContractor(project.get().contractor(), ConstructionProject.Contractor.ACTIVE)
                : project.get().withContractor(Optional.empty(), ConstructionProject.Contractor.NONE));
        return Reply.success(Message.of(accept ? MessageKey.BUILD_JOB_ACCEPTED : MessageKey.BUILD_JOB_DECLINED,
                number));
    }

    /** Wo und was ein Spieler als Auftragnehmer bauen soll: Ecke, Maße, Drehung und Baustoffe. */
    public synchronized Reply blueprintInfo(Caller caller, int number) {
        Optional<ConstructionProject> project = byNumber(number).filter(found -> found.status().isActive());
        if (project.isEmpty() || project.get().contractor().isEmpty()
                || !(project.get().contractor().filter(caller.player()::equals).isPresent()
                || controls(caller.player(), project.get()) || isHead(caller.player(), project.get()))) {
            return Reply.failure(Message.of(MessageKey.BUILD_UNKNOWN_PROJECT, number));
        }
        ConstructionProject found = project.get();
        Blueprint plan = library.find(found.blueprint()).orElseThrow();
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.BUILD_CONTRACT_SITE, number, plan.name(), found.originX(), found.originZ(),
                plan.footprintX(found.rotation()), plan.footprintZ(found.rotation()), plan.sizeY(),
                found.rotation() * 90));
        plan.materials().forEach((item, count) -> lines.add(Message.of(MessageKey.BUILD_MATERIAL_LINE, item, count)));
        return Reply.success(lines);
    }

    private void notifyClient(ConstructionProject project, MessageKey key) {
        if (project.client() instanceof PlayerRef player) {
            announcements.add(de.etiennebader.civ.server.Announcement.toPlayer(player,
                    Message.of(key, project.number())));
        }
    }

    /** Wie viele Blöcke des Plans in der Welt schon stehen; zählt nur, wo der Blockname stimmt. */
    private static int matching(ConstructionProject project, Blueprint plan, Terrain terrain) {
        int origin = project.originY().orElseThrow();
        int matched = 0;
        for (Blueprint.Placement placement : plan.placements()) {
            Blueprint.Placement turned = plan.rotated(placement, project.rotation());
            String found = terrain.blockName(new BlockPosition(project.dimension(), project.originX() + turned.x(),
                    origin + turned.y(), project.originZ() + turned.z()));
            if (found != null && found.equals(Blueprint.baseName(placement.state()))) {
                matched++;
            }
        }
        return matched;
    }

    /** Das Bauamt genehmigt einen Antrag; die Gebühr zahlt der Bauherr an die Gemeindekasse. */
    public synchronized Reply approve(Caller caller, int number) {
        return decide(caller.player(), number, true);
    }

    public synchronized Reply reject(Caller caller, int number) {
        return decide(caller.player(), number, false);
    }

    /** Der Bauherr bricht seinen Auftrag ab; schon gebaute Blöcke bleiben stehen. */
    public synchronized Reply cancel(Caller caller, int number) {
        Optional<ConstructionProject> project = byNumber(number);
        if (project.isEmpty() || !project.get().status().isActive()) {
            return Reply.failure(Message.of(MessageKey.BUILD_UNKNOWN_PROJECT, number));
        }
        if (!controls(caller.player(), project.get())) {
            return Reply.failure(Message.of(MessageKey.BUILD_NOT_CLIENT));
        }
        put(project.get().withStatus(ConstructionProject.Status.CANCELLED));
        return Reply.success(Message.of(MessageKey.BUILD_CANCELLED, number));
    }

    /** Aufträge des Spielers als Bauherr, dazu offene Anträge, die er als Bauamt entscheiden darf. */
    public synchronized Reply list(Caller caller) {
        List<Message> lines = new ArrayList<>();
        for (ConstructionProject project : projects.values()) {
            boolean mine = controls(caller.player(), project);
            boolean decides = project.status() == ConstructionProject.Status.AWAITING_PERMIT
                    && mayDecide(caller.player(), project.town());
            if (mine || decides) {
                lines.add(Message.of(MessageKey.BUILD_LINE, project.number(), project.blueprint(),
                        statusText(project.status()), project.placed(), project.total(),
                        texts.actor(project.client())));
                project.contractor().ifPresent(contractor -> lines.add(Message.of(MessageKey.BUILD_LINE_CONTRACTOR,
                        project.number(), texts.actor(contractor), Message.of(switch (project.contractorState()) {
                            case NONE -> MessageKey.BUILD_CONTRACTOR_STATE_NONE;
                            case FIRM_REVIEW -> MessageKey.BUILD_CONTRACTOR_STATE_FIRM;
                            case PLAYER_REVIEW -> MessageKey.BUILD_CONTRACTOR_STATE_PLAYER;
                            case ACTIVE -> MessageKey.BUILD_CONTRACTOR_STATE_ACTIVE;
                        }))));
            }
        }
        return lines.isEmpty() ? Reply.success(Message.of(MessageKey.BUILD_NONE)) : Reply.success(lines);
    }

    /** Alle Baupläne mit Größe und Preis. */
    public Reply plans() {
        List<Message> lines = new ArrayList<>();
        for (Blueprint plan : library.all()) {
            lines.add(Message.of(MessageKey.BUILD_PLAN_LINE, plan.name(), plan.style(), plan.sizeX(), plan.sizeY(),
                    plan.sizeZ(), plan.blockCount(), plan.units(), priceOf(plan) / 100.0 + ""));
        }
        return Reply.success(lines);
    }

    /** Nimmt eine hochgeladene Bauplandatei auf (Litematica oder Sponge Schematic). */
    public Reply importPlan(String fileName, byte[] bytes) {
        try {
            Blueprint plan = library.importFile(fileName, bytes);
            return Reply.success(Message.of(MessageKey.BUILD_PLAN_IMPORTED, plan.name(), plan.blockCount()));
        } catch (SchematicReader.InvalidSchematicException e) {
            return Reply.failure(Message.of(MessageKey.BUILD_PLAN_INVALID, String.valueOf(e.getMessage())));
        }
    }

    // ------------------------------------------------------------------ Auftrag

    /** Rückmeldung bei abgelehntem Auftrag. */
    private static final class BuildRefused extends RuntimeException {
        private final transient Message reply;

        BuildRefused(Message reply) {
            super(null, null, false, false);
            this.reply = reply;
        }
    }

    /**
     * Legt einen Auftrag an.
     *
     * @throws BuildRefused wenn Inhaber, Firma oder Größe nicht stimmen
     */
    private ConstructionProject place(ActorRef client, PolityId townId, Plot plot, Blueprint plan, PolityView firm,
                                      Day day, boolean selfSupplied, int rotation) {
        PolityView town = queries.polity(townId).orElseThrow();
        final ActorRef asked = client;
        boolean holder = plot.holder().equals(asked);
        boolean townPlot = plot.holder().equals(town.asActor()) && mayDecide(asked, townId);
        boolean organisationPlot = plot.holder() instanceof PolityRef org
                && queries.polity(org.id()).map(found -> found.holds(asked, Office.MAYOR)).orElse(false);
        if (!holder && !townPlot && !organisationPlot) {
            throw new BuildRefused(Message.of(MessageKey.BUILD_NOT_HOLDER));
        }
        // Auf Grundstücken einer Gemeinde, Firma oder anderen Organisation baut die Organisation: Ihre Kasse zahlt.
        if (!holder) {
            client = plot.holder();
        }
        if (queries.production().enterpriseOf(firm.id()).map(e -> e.branch() != Branch.CONSTRUCTION).orElse(true)) {
            throw new BuildRefused(Message.of(MessageKey.BUILD_NOT_CONSTRUCTION, firm.name().value()));
        }
        if (rotation == AUTO) {
            rotation = facingStreet(town, plot, plan);
        }
        int footprintX = plan.footprintX(rotation);
        int footprintZ = plan.footprintZ(rotation);
        if (footprintX > plot.area().width() || footprintZ > plot.area().depth()) {
            throw new BuildRefused(Message.of(MessageKey.BUILD_TOO_BIG, plan.name(), footprintX, footprintZ,
                    plot.area().width(), plot.area().depth()));
        }
        if (projects.values().stream().anyMatch(p -> p.plot().equals(plot.id()) && p.status().isActive())) {
            throw new BuildRefused(Message.of(MessageKey.BUILD_ALREADY));
        }
        boolean permit = queries.effectiveLaws(town).valueOf(StandardParameters.BUILD_PERMIT_REQUIRED);
        int originX = plot.area().minX() + (int) ((plot.area().width() - footprintX) / 2);
        int originZ = plot.area().minZ() + (int) ((plot.area().depth() - footprintZ) / 2);
        long price = selfSupplied ? priceOf(plan) * LABOUR_PERCENT / 100 : priceOf(plan);
        ConstructionProject project = new ConstructionProject(UUID.randomUUID(), ++lastNumber, townId, plot.id(),
                client, firm.id(), plan.name(), plot.area().dimension(), originX, OptionalInt.empty(),
                originZ, permit ? ConstructionProject.Status.AWAITING_PERMIT
                : ConstructionProject.Status.BUILDING, 0, plan.blockCount(), price, 0, day, rotation, selfSupplied,
                Map.of());
        put(project);
        return project;
    }

    /**
     * Die Drehung, bei der die Haustür zur nächsten Straße zeigt. Gibt es keine Straße, schaut das Haus zum Gemeindehaus.
     * Passt der Plan so nicht aufs Grundstück, wird die andere Achse versucht.
     */
    static int facingStreet(PolityView town, Plot plot, Blueprint plan) {
        int wanted = directionToStreet(town, plot.area());
        int doorSide = plan.door().map(Blueprint.Door::outward).orElse(0);
        int rotation = ((wanted - doorSide) % 4 + 4) % 4;
        for (int attempt : new int[] {rotation, (rotation + 1) % 4, (rotation + 3) % 4, (rotation + 2) % 4}) {
            if (plan.footprintX(attempt) <= plot.area().width() && plan.footprintZ(attempt) <= plot.area().depth()) {
                return attempt;
            }
        }
        return rotation;
    }

    /** Himmelsrichtung (0 Nord, 1 Ost, 2 Süd, 3 West), in der die nächste Straße (sonst das Gemeindehaus) liegt. */
    static int directionToStreet(PolityView town, de.etiennebader.civ.domain.land.Area area) {
        double cx = (area.minX() + area.maxX()) / 2.0;
        double cz = (area.minZ() + area.maxZ()) / 2.0;
        double tx = cx;
        double tz = cz + 1;
        double best = Double.MAX_VALUE;
        for (de.etiennebader.civ.domain.polity.Street street : town.streets()) {
            de.etiennebader.civ.domain.land.Area sa = street.area();
            if (!sa.dimension().equals(area.dimension())) {
                continue;
            }
            double px = Math.max(sa.minX(), Math.min(sa.maxX(), cx));
            double pz = Math.max(sa.minZ(), Math.min(sa.maxZ(), cz));
            double distance = Math.abs(px - cx) + Math.abs(pz - cz);
            if (distance < best) {
                best = distance;
                tx = px;
                tz = pz;
            }
        }
        if (best == Double.MAX_VALUE && town.seat().isPresent()) {
            tx = town.seat().get().x();
            tz = town.seat().get().z();
        }
        double dx = tx - cx;
        double dz = tz - cz;
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz < 0 ? 0 : 2;
        }
        return dx > 0 ? 1 : 3;
    }

    private static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** Die Fläche vor der Haustür (außerhalb der Wand) in Weltkoordinaten. */
    private static Optional<BlockPosition> doorFront(ConstructionProject project, Blueprint plan) {
        return plan.door().map(door -> {
            Blueprint.Placement turned = plan.rotated(new Blueprint.Placement(door.x(), door.y(), door.z(), ""),
                    project.rotation());
            int[] step = STEP[(door.outward() + project.rotation()) % 4];
            return new BlockPosition(project.dimension(), project.originX() + turned.x() + step[0], 0,
                    project.originZ() + turned.z() + step[1]);
        });
    }

    /** Die Absperrungen, die gerade stehen sollen oder abzubauen sind. */
    public synchronized List<Site> sites() {
        List<Site> sites = new ArrayList<>();
        for (ConstructionProject project : projects.values()) {
            if (project.originY().isEmpty()) {
                continue;
            }
            boolean active = project.status() == ConstructionProject.Status.BUILDING
                    && project.contractorState() != ConstructionProject.Contractor.ACTIVE;
            if (!active && project.status().isActive()) {
                continue;
            }
            Optional<Blueprint> plan = library.find(project.blueprint());
            Optional<de.etiennebader.civ.domain.land.Area> limit = queries.land(project.town()).plot(project.plot())
                    .map(Plot::area);
            if (plan.isEmpty() || limit.isEmpty()) {
                continue;
            }
            sites.add(new Site(project.id(), new BlockPosition(project.dimension(), project.originX(),
                    project.originY().getAsInt(), project.originZ()), plan.get().footprintX(project.rotation()),
                    plan.get().footprintZ(project.rotation()), limit.get(), active));
        }
        return sites;
    }

    static long priceOf(Blueprint plan) {
        return plan.blockCount() * PRICE_PER_BLOCK;
    }

    private Reply decide(PlayerRef player, int number, boolean approve) {
        Optional<ConstructionProject> project = byNumber(number)
                .filter(found -> found.status() == ConstructionProject.Status.AWAITING_PERMIT);
        if (project.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.BUILD_UNKNOWN_PROJECT, number));
        }
        if (!mayDecide(player, project.get().town())) {
            return Reply.failure(Message.of(MessageKey.BUILD_NOT_AUTHORITY));
        }
        try {
            return Reply.success(decide(project.get(), approve));
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    /** Genehmigt oder lehnt ab; bei Genehmigung zahlt der Bauherr die Gebühr. */
    private Message decide(ConstructionProject project, boolean approve) {
        if (!approve) {
            put(project.withStatus(ConstructionProject.Status.REJECTED));
            return Message.of(MessageKey.BUILD_REJECTED, project.number());
        }
        PolityView town = queries.polity(project.town()).orElseThrow();
        long fee = queries.effectiveLaws(town).valueOf(StandardParameters.BUILD_PERMIT_FEE);
        if (fee > 0 && !project.client().equals(town.asActor())) {
            app.transferFunds().execute(new TransferFunds.Command(project.client(), town.asActor(),
                    Money.of(fee, town.currency()), TransferReason.PERMIT_FEE, calendar.today()));
        }
        put(project.withStatus(ConstructionProject.Status.BUILDING));
        return Message.of(MessageKey.BUILD_APPROVED, project.number());
    }

    /** Das Bauamt: Planer, sonst Bürgermeister. */
    private boolean mayDecide(ActorRef actor, PolityId townId) {
        return queries.polity(townId).map(town -> town.holds(actor, Office.PLANNER)
                || (town.officeTerm(Office.PLANNER).isEmpty() && town.holds(actor, Office.MAYOR))).orElse(false);
    }

    // ------------------------------------------------------------------ Baufortschritt

    /**
     * Gibt die Blöcke zurück, die jetzt zu setzen sind. Das Spiel setzt sie und ruft dies regelmäßig auf.
     *
     * <p>Eine Baustelle ruht, solange ihr Gelände nicht geladen ist, solange das Baufeld nicht frei ist (der Bauherr
     * erfährt davon), solange bei eigenem Material Baustoffe fehlen oder dem Bauherrn das Geld fehlt.
     *
     * @param ticks vergangene Spielticks seit dem letzten Aufruf
     */
    public synchronized List<Build> advance(long ticks, Terrain terrain) {
        clock += ticks;
        List<Build> builds = new ArrayList<>();
        long perDay = Math.max(1, this.ticksPerDay);
        for (ConstructionProject project : new ArrayList<>(projects.values())) {
            if (project.status() != ConstructionProject.Status.BUILDING) {
                continue;
            }
            Optional<PolityView> firm = queries.polity(project.builder());
            Optional<Blueprint> plan = library.find(project.blueprint());
            if (firm.isEmpty() || plan.isEmpty()) {
                put(project.withStatus(ConstructionProject.Status.CANCELLED));
                continue;
            }
            if (expireReview(project, perDay)) {
                continue;
            }
            if (project.contractorState() == ConstructionProject.Contractor.FIRM_REVIEW
                    || project.contractorState() == ConstructionProject.Contractor.PLAYER_REVIEW) {
                continue; // wartet auf die Zustimmung der Firma oder des Spielers
            }
            int fx = plan.get().footprintX(project.rotation());
            int fz = plan.get().footprintZ(project.rotation());
            BlockPosition corner = new BlockPosition(project.dimension(), project.originX(), 0, project.originZ());
            BlockPosition far = new BlockPosition(project.dimension(), project.originX() + fx - 1, 0,
                    project.originZ() + fz - 1);
            if (!terrain.isLoaded(corner) || !terrain.isLoaded(far)) {
                continue;
            }
            ConstructionProject current = project;
            if (current.placed() == 0 && blockedUntil.getOrDefault(current.id(), 0L) <= clock) {
                Optional<BlockPosition> front = doorFront(project, plan.get());
                if (front.isPresent() && !terrain.isLoaded(front.get())) {
                    continue;
                }
                int y = terrain.floorLevel(corner, fx, fz, front, plan.get().door().map(Blueprint.Door::y).orElse(0));
                BlockPosition min = new BlockPosition(project.dimension(), project.originX(), y, project.originZ());
                if (!terrain.isClear(min, fx, plan.get().sizeY(), fz)) {
                    long since = blockedSince.computeIfAbsent(current.id(), id -> clock);
                    if (clock - since >= ABANDON_DAYS * perDay) {
                        abandon(current);
                        continue;
                    }
                    blockedUntil.put(current.id(), clock + RECHECK_TICKS);
                    if (current.client() instanceof PlayerRef player) {
                        announcements.add(de.etiennebader.civ.server.Announcement.toPlayer(player,
                                Message.of(MessageKey.BUILD_SITE_BLOCKED, current.number())));
                    }
                    continue;
                }
                blockedUntil.remove(current.id());
                blockedSince.remove(current.id());
                current = current.withOriginY(y);
                put(current);
            }
            if (current.originY().isEmpty() || blockedUntil.containsKey(current.id())) {
                continue;
            }
            if (current.contractorState() == ConstructionProject.Contractor.ACTIVE) {
                verifyContractor(current, firm.get(), plan.get(), terrain);
                continue;
            }
            double budget = carry.getOrDefault(current.id(), 0.0)
                    + (double) Math.max(1, workers(firm.get()).size()) * BLOCKS_PER_WORKER_DAY * ticks / perDay;
            int wanted = (int) Math.min(Math.min(budget, MAX_BLOCKS_PER_CALL), current.total() - current.placed());
            if (wanted <= 0) {
                carry.put(current.id(), budget);
                continue;
            }
            List<Blueprint.Placement> next = plan.get().placements(current.placed(), wanted);
            Map<String, Integer> depot = new java.util.TreeMap<>(current.depot());
            int count = current.selfSupplied() ? coveredBy(next, depot) : next.size();
            if (count <= 0) {
                carry.put(current.id(), Math.min(budget, MAX_BLOCKS_PER_CALL));
                continue;
            }
            long due = current.price() * (current.placed() + count) / current.total() - current.paid();
            if (due > 0 && !pay(current, firm.get(), due)) {
                carry.put(current.id(), Math.min(budget, MAX_BLOCKS_PER_CALL));
                continue;
            }
            List<Blueprint.Placement> blocks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                blocks.add(plan.get().rotated(next.get(i), project.rotation()));
            }
            carry.put(current.id(), budget - count);
            put(current.progressed(blocks.size(), Math.max(0, due), depot));
            builds.add(new Build(current.id(), new BlockPosition(current.dimension(), current.originX(),
                    current.originY().orElseThrow(), current.originZ()), current.rotation(), blocks,
                    workers(firm.get()).stream().map(ActorRef::uuid).toList(), current.placed() == 0, fx, fz,
                    plan.get().sizeY()));
        }
        return builds;
    }

    /**
     * Ein Spieler baut selbst: Was von seinem Bau dem Plan entspricht, gilt als erledigt und wird bezahlt. Ab
     * {@value #DONE_PERCENT} % passender Blöcke ist der Auftrag fertig.
     */
    private void verifyContractor(ConstructionProject project, PolityView firm, Blueprint plan, Terrain terrain) {
        int matched = matching(project, plan, terrain);
        int target = matched * 100 >= project.total() * DONE_PERCENT ? project.total() : Math.min(matched, project.total());
        if (target <= project.placed()) {
            return;
        }
        long due = project.price() * target / project.total() - project.paid();
        if (due > 0 && !payContractor(project, firm, due)) {
            return;
        }
        put(project.verified(target - project.placed(), Math.max(0, due)));
    }

    /** Der Bauherr zahlt den Spieler, abzüglich der Provision der Firma, die sie behält. */
    private boolean payContractor(ConstructionProject project, PolityView firm, long amount) {
        long commission = amount * COMMISSION_PERCENT / 100;
        long wage = amount - commission;
        if (queries.ledger().balanceOf(project.client(), firm.currency()).amount() < amount) {
            return false;
        }
        try {
            if (wage > 0) {
                app.transferFunds().execute(new TransferFunds.Command(project.client(), project.contractor().orElseThrow(),
                        Money.of(wage, firm.currency()), TransferReason.CONSTRUCTION, calendar.today()));
            }
            if (commission > 0) {
                app.transferFunds().execute(new TransferFunds.Command(project.client(), firm.asActor(),
                        Money.of(commission, firm.currency()), TransferReason.CONSTRUCTION, calendar.today()));
            }
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    /** Anfragen an Firma oder Spieler verfallen nach {@value #REVIEW_DAYS} Tagen. */
    private boolean expireReview(ConstructionProject project, long perDay) {
        ConstructionProject.Contractor state = project.contractorState();
        if (state != ConstructionProject.Contractor.FIRM_REVIEW
                && state != ConstructionProject.Contractor.PLAYER_REVIEW) {
            return false;
        }
        long since = reviewSince.computeIfAbsent(project.id(), id -> clock);
        if (clock - since < REVIEW_DAYS * perDay) {
            return false;
        }
        reviewSince.remove(project.id());
        if (state == ConstructionProject.Contractor.FIRM_REVIEW) {
            put(project.withStatus(ConstructionProject.Status.CANCELLED));
            notifyClient(project, MessageKey.BUILD_REVIEW_EXPIRED);
        } else {
            put(project.withContractor(Optional.empty(), ConstructionProject.Contractor.NONE));
        }
        return true;
    }

    /** Das Baufeld blieb zu lange blockiert: Der Auftrag endet, der Bauherr (bei Organisationen deren Leitung) erfährt es. */
    private void abandon(ConstructionProject project) {
        put(project.withStatus(ConstructionProject.Status.CANCELLED));
        blockedSince.remove(project.id());
        blockedUntil.remove(project.id());
        abandoned.add(project.plot());
        ActorRef who = project.client() instanceof PolityRef org
                ? queries.polity(org.id()).flatMap(PolityView::mayor).orElse(null) : project.client();
        if (who instanceof PlayerRef player) {
            announcements.add(de.etiennebader.civ.server.Announcement.toPlayer(player,
                    Message.of(MessageKey.BUILD_SITE_ABANDONED, project.number(), ABANDON_DAYS)));
        }
    }

    /** Wie viele der nächsten Blöcke aus dem Lager gedeckt sind; verbraucht die Baustoffe im {@code depot}. */
    private static int coveredBy(List<Blueprint.Placement> next, Map<String, Integer> depot) {
        int covered = 0;
        for (Blueprint.Placement placement : next) {
            if (Blueprint.needsItem(placement.state())) {
                String item = Blueprint.baseName(placement.state());
                int have = depot.getOrDefault(item, 0);
                if (have <= 0) {
                    break;
                }
                depot.put(item, have - 1);
            }
            covered++;
        }
        return covered;
    }

    /**
     * Der Bauherr liefert Baustoffe für einen Auftrag mit eigenem Material.
     *
     * @return wie viele Stück das Lager aufgenommen hat; überzählige gibt das Spiel zurück
     */
    public synchronized int deliver(PlayerRef player, int number, String item, int count) {
        Optional<ConstructionProject> project = byNumber(number)
                .filter(found -> found.status().isActive() && found.selfSupplied()
                        && found.client().equals(player));
        Optional<Blueprint> plan = project.flatMap(found -> library.find(found.blueprint()));
        if (plan.isEmpty() || count <= 0) {
            return 0;
        }
        int needed = plan.get().materials().getOrDefault(item, 0);
        int open = needed - usedOf(project.get(), plan.get(), item) - project.get().depot().getOrDefault(item, 0);
        int taken = Math.max(0, Math.min(count, open));
        if (taken > 0) {
            Map<String, Integer> depot = new java.util.TreeMap<>(project.get().depot());
            depot.merge(item, taken, Integer::sum);
            put(project.get().withDepot(depot));
        }
        return taken;
    }

    /** Wie viele Stück einer Sorte für die schon gesetzten Blöcke verbraucht wurden. */
    private static int usedOf(ConstructionProject project, Blueprint plan, String item) {
        int used = 0;
        for (Blueprint.Placement placement : plan.placements(0, project.placed())) {
            if (Blueprint.needsItem(placement.state()) && Blueprint.baseName(placement.state()).equals(item)) {
                used++;
            }
        }
        return used;
    }

    /** Was für einen Auftrag mit eigenem Material noch fehlt: Blockname mit Anzahl. */
    public synchronized Map<String, Integer> missing(int number) {
        Map<String, Integer> missing = new java.util.TreeMap<>();
        byNumber(number).filter(found -> found.status().isActive() && found.selfSupplied()).ifPresent(project ->
                library.find(project.blueprint()).ifPresent(plan -> plan.materials().forEach((item, total) -> {
                    int open = total - usedOf(project, plan, item) - project.depot().getOrDefault(item, 0);
                    if (open > 0) {
                        missing.put(item, open);
                    }
                })));
        return missing;
    }

    /** Nummern der aktiven Aufträge dieses Spielers mit eigenem Material. */
    public synchronized List<Integer> selfSuppliedOf(PlayerRef player) {
        return projects.values().stream().filter(project -> project.status().isActive() && project.selfSupplied()
                && project.client().equals(player)).map(ConstructionProject::number).toList();
    }

    /** Textzeilen zu dem, was noch fehlt. */
    public synchronized Reply materials(Caller caller, int number) {
        Optional<ConstructionProject> project = byNumber(number)
                .filter(found -> found.client().equals(caller.player()) && found.status().isActive());
        if (project.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.BUILD_UNKNOWN_PROJECT, number));
        }
        if (!project.get().selfSupplied()) {
            return Reply.failure(Message.of(MessageKey.BUILD_FIRM_SUPPLIES, number));
        }
        List<Message> lines = new ArrayList<>();
        missing(number).forEach((item, open) -> lines.add(Message.of(MessageKey.BUILD_MATERIAL_LINE, item, open)));
        return lines.isEmpty() ? Reply.success(Message.of(MessageKey.BUILD_MATERIAL_COMPLETE, number))
                : Reply.success(lines);
    }

    private boolean pay(ConstructionProject project, PolityView firm, long amount) {
        try {
            app.transferFunds().execute(new TransferFunds.Command(project.client(), firm.asActor(),
                    Money.of(amount, firm.currency()), TransferReason.CONSTRUCTION, calendar.today()));
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    /** Beschäftigte der Firma (Bauarbeiter); der Chef arbeitet immer mit, ist aber nicht darunter, wenn er allein ist. */
    private List<ActorRef> workers(PolityView firm) {
        List<ActorRef> employees = queries.contracts().active().stream()
                .filter(contract -> contract.kind() == ContractKind.EMPLOYMENT
                        && contract.offeror().equals(firm.asActor()))
                .map(de.etiennebader.civ.domain.contract.Contract::payee).toList();
        return employees.isEmpty() ? List.of() : employees;
    }

    // ------------------------------------------------------------------ Dorfbewohner

    /** Wer einen Auftrag steuern darf: der Bauherr, bei einer Organisation als Bauherr deren Leitung. */
    private boolean controls(PlayerRef player, ConstructionProject project) {
        if (project.client().equals(player)) {
            return true;
        }
        return project.client() instanceof PolityRef org
                && (queries.polity(org.id()).map(found -> found.holds(player, Office.MAYOR)).orElse(false)
                || mayDecide(player, project.town()) && org.id().equals(project.town()));
    }

    /**
     * Tagesabschluss. Wer das Sagen hat, beauftragt Baufirmen, und zwar ohne Spieler:
     * <ul>
     *     <li>Dorfbewohner als Bauamt (Planer, sonst Bürgermeister) genehmigen offene Anträge.</li>
     *     <li>Dorfbewohner als Bürgermeister oder Bauamt schneiden neue Grundstücke zu und schreiben sie zum Verkauf
     *         aus ({@link TownPlanning}), und sie lassen kommunale Bauten (Schule, Gotteshaus, Rathaus, Plätze …) auf
     *         Grundstücken der Gemeinde errichten, bezahlt aus der Gemeindekasse.</li>
     *     <li>Dorfbewohner, die eine Firma, Zeitung oder Partei leiten, lassen auf deren Grundstücken bauen, bezahlt aus
     *         der Kasse der Organisation.</li>
     *     <li>Dorfbewohner kaufen angebotenes Wohnland und lassen darauf bauen, bezahlt aus eigener Tasche.</li>
     * </ul>
     * Welche Baufirma den Auftrag erhält: bevorzugt eine aus der eigenen Gemeinde, sonst die nächste beliebige.
     *
     * @return Zahl der neuen Aufträge
     */
    public synchronized int villagersOrder(Day day) {
        Objects.requireNonNull(day, "day");
        surveyed.clear();
        approvePermits();
        for (ConstructionProject project : new ArrayList<>(projects.values())) {
            if (project.status().isActive() && project.contractorState() == ConstructionProject.Contractor.FIRM_REVIEW
                    && queries.polity(project.builder()).flatMap(PolityView::mayor)
                    .filter(PersonRef.class::isInstance).isPresent()) {
                answerFirm(project, true); // eine Baufirma unter Dorfbewohner-Leitung stimmt zu
            }
        }
        planning.endOfDay(day);
        int orders = 0;
        List<PolityView> towns = queries.polities().stream().filter(p -> p.kind() == PolityKind.TERRITORIAL)
                .sorted(Comparator.comparing(p -> p.id().value())).toList();
        for (PolityView town : towns) {
            Optional<PolityView> firm = firmFor(town);
            if (firm.isEmpty()) {
                continue;
            }
            for (Membership member : town.members()) {
                if (member.member() instanceof PersonRef villager
                        && VillagerAmbitions.roll(villager, day, 0x92) < VILLAGER_ORDERS
                        && orderFor(villager, town, firm.get(), day)) {
                    orders++;
                }
            }
        }
        for (PolityView owner : queries.polities().stream().filter(p -> p.kind() == PolityKind.TERRITORIAL
                        || p.kind() == PolityKind.COMPANY || p.kind() == PolityKind.NEWSPAPER
                        || p.kind() == PolityKind.PARTY || p.kind() == PolityKind.FEDERATION)
                .sorted(Comparator.comparing(p -> p.id().value())).toList()) {
            if (head(owner).filter(h -> VillagerAmbitions.roll(h, day, 0x94) < ORGANISATION_ORDERS).isPresent()
                    && orderForOrganisation(owner, towns, day)) {
                orders++;
            }
        }
        return orders;
    }

    private void approvePermits() {
        for (ConstructionProject project : new ArrayList<>(projects.values())) {
            if (project.status() == ConstructionProject.Status.AWAITING_PERMIT
                    && authority(project.town()).filter(PersonRef.class::isInstance).isPresent()) {
                try {
                    decide(project, true);
                } catch (DomainException ignored) {
                    // der Bauherr kann die Gebühr nicht zahlen: bleibt liegen
                }
            }
        }
    }

    /** Die Person, die eine Polity führt, wenn es ein Dorfbewohner ist: bei Gemeinden das Bauamt. */
    private Optional<PersonRef> head(PolityView owner) {
        Optional<ActorRef> holder = owner.kind() == PolityKind.TERRITORIAL ? authority(owner.id())
                : owner.officeTerm(Office.MAYOR).map(OfficeTerm::holder);
        return holder.filter(PersonRef.class::isInstance).map(PersonRef.class::cast);
    }

    private boolean isBuilder(PolityView company) {
        return queries.production().enterpriseOf(company.id()).filter(e -> e.branch() == Branch.CONSTRUCTION)
                .isPresent();
    }

    /** Die Baufirma für Aufträge einer Gemeinde: eine aus der Gemeinde, sonst die mit der kleinsten Kennung. */
    private Optional<PolityView> firmFor(PolityView town) {
        List<PolityView> builders = queries.companies().stream().filter(this::isBuilder)
                .sorted(Comparator.comparing(company -> company.id().value())).toList();
        return builders.stream().filter(company -> sameTown(company, town)).findFirst()
                .or(() -> builders.stream().findFirst());
    }

    private boolean sameTown(PolityView company, PolityView town) {
        return company.officeTerm(Office.MAYOR).map(OfficeTerm::holder)
                .flatMap(queries::municipalityOf).map(PolityView::id).filter(town.id()::equals).isPresent();
    }

    /** Ein Dorfbewohner lässt auf einem seiner Grundstücke ein Gebäude bauen, das zur Grundstücksart passt. */
    private boolean orderFor(PersonRef villager, PolityView town, PolityView firm, Day day) {
        Personality personality = Personality.of(villager);
        List<Plot> empty = queries.land(town.id()).plotsHeldBy(villager).stream()
                .filter(plot -> plot.type() != PlotType.EMBASSY)
                .filter(plot -> projects.values().stream().noneMatch(p -> p.plot().equals(plot.id())) && !abandoned.contains(plot.id()))
                .filter(plot -> siteFree(plot.area()))
                .sorted(Comparator.<Plot, Boolean>comparing(plot -> plot.type() != PlotType.RESIDENTIAL)
                        .thenComparing(plot -> plot.id().value())).toList();
        if (empty.isEmpty()) {
            return false;
        }
        Plot plot = empty.getFirst();
        long cash = queries.ledger().balanceOf(villager, town.currency()).amount();
        List<Blueprint> fitting = library.fittingFor(plot.type(), plot.area().width(), plot.area().depth()).stream()
                .filter(plan -> priceOf(plan) * 3 / 2 <= cash).toList();
        Optional<Blueprint> affordable;
        if (plot.type() == PlotType.RESIDENTIAL) {
            Comparator<Blueprint> biggest = Comparator.comparingInt(Blueprint::blockCount);
            affordable = (personality.thrift() >= 40
                    ? fitting.stream().filter(plan -> plan.units() > 1).max(biggest)
                    : fitting.stream().filter(plan -> plan.units() == 1).max(biggest))
                    .or(() -> fitting.stream().max(biggest));
        } else {
            affordable = pick(fitting, villager, day);
        }
        if (affordable.isEmpty()) {
            return false;
        }
        try {
            place(villager, town.id(), plot, affordable.get(), firm, day, false, AUTO);
            return true;
        } catch (BuildRefused | DomainException e) {
            return false;
        }
    }

    /**
     * Die Leitung einer Gemeinde, Firma, Zeitung oder Partei lässt ein Gebäude auf einem Grundstück der Organisation
     * bauen. Gemeinden bauen nur kommunale Arten (öffentlich, Schule, Gotteshaus), Firmen auf allen ihren Grundstücken.
     * Der Auftrag läuft auf die Organisation, bezahlt wird aus ihrer Kasse.
     */
    private boolean orderForOrganisation(PolityView owner, List<PolityView> towns, Day day) {
        Optional<PersonRef> head = head(owner);
        if (head.isEmpty()) {
            return false;
        }
        for (PolityView town : towns) {
            List<Plot> candidates = queries.land(town.id()).plotsHeldBy(owner.asActor()).stream()
                    .filter(plot -> plot.type() != PlotType.EMBASSY)
                    .filter(plot -> owner.kind() != PolityKind.TERRITORIAL || isCivic(plot.type()))
                    .filter(plot -> !plot.isForSale())
                    .filter(plot -> siteFree(plot.area()))
                    .filter(plot -> projects.values().stream().noneMatch(p -> p.plot().equals(plot.id())) && !abandoned.contains(plot.id()))
                    .sorted(Comparator.comparing(plot -> plot.id().value())).toList();
            if (candidates.isEmpty()) {
                continue;
            }
            Optional<PolityView> firm = firmFor(town);
            if (firm.isEmpty() || firm.get().equals(owner)) {
                continue;
            }
            Plot plot = candidates.getFirst();
            long cash = queries.ledger().balanceOf(owner.asActor(), owner.currency()).amount();
            Optional<Blueprint> plan = pick(library.fittingFor(plot.type(), plot.area().width(), plot.area().depth()),
                    head.get(), day).filter(found -> priceOf(found) * 2 <= cash);
            if (plan.isEmpty()) {
                continue;
            }
            try {
                place(owner.asActor(), town.id(), plot, plan.get(), firm.get(), day, false, AUTO);
                if (owner.kind() == PolityKind.TERRITORIAL) {
                    announcements.add(de.etiennebader.civ.server.Announcement.toTown(town.id(),
                            Message.of(MessageKey.MUNICIPAL_BUILD_ORDERED, plan.get().name(), firm.get().name().value(),
                                    town.name().value())));
                }
                return true;
            } catch (BuildRefused | DomainException e) {
                // nicht genehmigt, zu klein, kein Geld: morgen vielleicht
            }
        }
        return false;
    }

    private static boolean isCivic(PlotType type) {
        return type == PlotType.PUBLIC || type == PlotType.EDUCATION || type == PlotType.RELIGIOUS;
    }

    /** Wählt unter passenden Plänen einen, fest je Person und Tag; größere Pläne sind kein Muss. */
    private static Optional<Blueprint> pick(List<Blueprint> plans, PersonRef who, Day day) {
        if (plans.isEmpty()) {
            return Optional.empty();
        }
        List<Blueprint> sorted = plans.stream().sorted(Comparator.comparing(Blueprint::name)).toList();
        return Optional.of(sorted.get((int) (VillagerAmbitions.roll(who, day, 0x95) * sorted.size())));
    }

    /**
     * Das Bauamt: Planer, sonst Bürgermeister; ohne Amtsinhaber regiert kommissarisch ein Dorfbewohner, solange
     * {@link #interimGovernment} eingeschaltet ist.
     */
    private Optional<ActorRef> authority(PolityId townId) {
        return queries.polity(townId).flatMap(town -> town.officeTerm(Office.PLANNER)
                .or(() -> town.officeTerm(Office.MAYOR)).map(OfficeTerm::holder)
                .or(() -> interimGovernment ? town.mayor(true) : Optional.empty()));
    }

    /** Ob ein Dorfbewohner kommissarisch als Bauamt handelt, wenn die Gemeinde keinen Amtsinhaber hat. */
    public synchronized void interimGovernment(boolean enabled) {
        this.interimGovernment = enabled;
    }

    // ------------------------------------------------------------------ Hilfen

    private Optional<ConstructionProject> byNumber(int number) {
        return projects.values().stream().filter(project -> project.number() == number).findFirst();
    }

    private void put(ConstructionProject project) {
        projects.put(project.id(), project);
        store.save(new ArrayList<>(projects.values()));
    }

    private static Message statusText(ConstructionProject.Status status) {
        return Message.of(switch (status) {
            case AWAITING_PERMIT -> MessageKey.BUILD_STATUS_AWAITING_PERMIT;
            case BUILDING -> MessageKey.BUILD_STATUS_BUILDING;
            case DONE -> MessageKey.BUILD_STATUS_DONE;
            case REJECTED -> MessageKey.BUILD_STATUS_REJECTED;
            case CANCELLED -> MessageKey.BUILD_STATUS_CANCELLED;
        });
    }
}
