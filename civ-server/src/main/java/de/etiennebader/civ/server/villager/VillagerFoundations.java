package de.etiennebader.civ.server.villager;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.TerritoryView;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.founding.FoundingFees;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Ehrgeizige Dorfbewohner gründen neue Gemeinden: Wer in einer großen Gemeinde lebt, zieht dorthin, wo er zuletzt
 * gesehen wurde, und gründet dort eine eigene Gemeinde mit sich als Bürgermeister.
 *
 * <ul>
 *   <li><b>Wer:</b> ehrgeizige Dorfbewohner ({@link VillagerAmbitions#isAmbitious}), die keine Ämter halten und in einer
 *       Gemeinde mit mindestens {@value #MIN_RESIDENTS} Dorfbewohnern leben, damit keine Gemeinde ausblutet.</li>
 *   <li><b>Wie oft:</b> höchstens eine Gründung je Tag, mit {@value #FOUND_CHANCE} Tageswahrscheinlichkeit (gewichtet
 *       mit dem Ehrgeiz), und nur solange es weniger als eine Gemeinde je {@value #VILLAGERS_PER_TOWN} Dorfbewohner gibt.</li>
 *   <li><b>Wo:</b> am letzten Aufenthaltsort, sofern der Chunk dort samt Umgebung frei ist. Wohnt der Dorfbewohner
 *       (wie fast immer) mitten in einer fremden Gemeinde, sucht er den nächstgelegenen freien Ort im Umkreis von
 *       {@value #SITE_RADIUS} Chunks. Gibt es keinen, oder gelten die Claim-Regeln nicht, bleibt er in seiner
 *       Gemeinde.</li>
 *   <li><b>Umzug:</b> Gründer und Gefolge werden zum neuen Gemeindehaus gebracht ({@code relocate}); sonst würde sie
 *       die Gemeindezuordnung beim nächsten Rundgang zurück in die alte Gemeinde ziehen.</li>
 *   <li><b>Gebühr:</b> Siedler zahlen nur {@value #SETTLER_FEE_PERCENT} % der Gründungsgebühr. Sind sie selbst nicht
 *       flüssig (meist), übernimmt die Heimatgemeinde die Gebühr aus ihrer Kasse, solange danach noch das
 *       {@value #TREASURY_RESERVE_FACTOR}fache der Gebühr als Reserve bleibt.</li>
 *   <li><b>Gefolge:</b> Bis zu {@value #MAX_FOLLOWERS} Mitbürger, die zuletzt am nächsten am neuen Ort gesehen wurden,
 *       ziehen mit; mindestens {@value #MIN_LEFT_BEHIND} Dorfbewohner bleiben zurück.</li>
 * </ul>
 *
 * <p>Der Zufall ist fest je Dorfbewohner und Tag.
 */
public final class VillagerFoundations {

    static final double FOUND_CHANCE = 0.04;
    static final int MIN_RESIDENTS = 4;
    static final int VILLAGERS_PER_TOWN = 6;
    /** Höchstens so viele Mitbürger ziehen mit dem Gründer. */
    static final int MAX_FOLLOWERS = 3;
    /** So viele Dorfbewohner bleiben mindestens in der alten Gemeinde zurück. */
    static final int MIN_LEFT_BEHIND = 2;
    /** Umkreis in Chunks, in dem ein freier Ort für die neue Gemeinde gesucht wird. */
    static final int SITE_RADIUS = 16;
    /** Freier Rand in Chunks rund um das neue Gemeindegebiet, damit Gemeinden nicht sofort zusammenwachsen. */
    static final int SITE_GAP = 2;
    /** Anteil der Gründungsgebühr, den ein Dorfbewohner als Siedler zahlt. */
    static final int SETTLER_FEE_PERCENT = 20;

    private java.util.function.Predicate<ChunkCoord> habitable = chunk -> true;

    /** Welche Chunks als Ort einer neuen Gemeinde taugen (Land statt Wasser); ohne Angabe jeder freie. */
    public void habitable(java.util.function.Predicate<ChunkCoord> habitable) {
        this.habitable = Objects.requireNonNull(habitable, "habitable");
    }

    private java.util.function.BiConsumer<PolityId, PolityId> founded = (home, created) -> { };

    /** Wer erfahren soll, dass aus einer Heimatgemeinde heraus eine neue entstand (Kultur und Rasse erben). */
    public void founded(java.util.function.BiConsumer<PolityId, PolityId> listener) {
        this.founded = Objects.requireNonNull(listener, "listener");
    }
    /** Die Heimatgemeinde zahlt die Gebühr nur, wenn ihre Kasse danach noch so viele Gebühren enthält. */
    static final int TREASURY_RESERVE_FACTOR = 3;

    private final FoundingFees fees;
    private final CivApplication app;
    private final Function<PersonRef, Optional<BlockPosition>> lastSeen;
    private final Announcements announcements;
    private final BiConsumer<PersonRef, BlockPosition> relocate;

    public VillagerFoundations(CivApplication app, Function<PersonRef, Optional<BlockPosition>> lastSeen,
                               Announcements announcements) {
        this(app, lastSeen, announcements, FoundingFees.FREE);
    }

    /** Mit Gründungskosten: der Dorfbewohner zahlt aus seinem eigenen Konto. */
    public VillagerFoundations(CivApplication app, Function<PersonRef, Optional<BlockPosition>> lastSeen,
                               Announcements announcements, FoundingFees fees) {
        this(app, lastSeen, announcements, fees, (villager, seat) -> { });
    }

    /** @param relocate bringt einen Dorfbewohner zum Gemeindehaus der neuen Gemeinde */
    public VillagerFoundations(CivApplication app, Function<PersonRef, Optional<BlockPosition>> lastSeen,
                               Announcements announcements, FoundingFees fees,
                               BiConsumer<PersonRef, BlockPosition> relocate) {
        this.relocate = Objects.requireNonNull(relocate, "relocate");
        this.fees = Objects.requireNonNull(fees, "fees");
        this.app = Objects.requireNonNull(app, "app");
        this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
    }

    /** @return ob heute eine Gemeinde gegründet wurde */
    public boolean endOfDay(Day day) {
        WorldQueries world = app.queries();
        List<PolityView> towns = world.polities().stream().filter(p -> p.kind() == PolityKind.TERRITORIAL)
                .sorted(Comparator.comparing(p -> p.id().value())).toList();
        long villagers = towns.stream().mapToLong(VillagerFoundations::villagerCount).sum();
        if (towns.size() >= Math.max(2, villagers / VILLAGERS_PER_TOWN)) {
            return false;
        }
        int start = towns.isEmpty() ? 0 : (int) Math.floorMod(day.value(), (long) towns.size());
        for (int index = 0; index < towns.size(); index++) {
            PolityView home = towns.get((start + index) % towns.size());
            if (villagerCount(home) < MIN_RESIDENTS) {
                continue;
            }
            for (PersonRef villager : candidates(home)) {
                double drive = VillagerAmbitions.drive(villager) / 100.0;
                if (VillagerAmbitions.roll(villager, day, 0x63) < FOUND_CHANCE * drive && found(home, villager, day)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long villagerCount(PolityView town) {
        return town.members().stream().filter(m -> m.member() instanceof PersonRef).count();
    }

    private static List<PersonRef> candidates(PolityView home) {
        return home.members().stream().map(Membership::member).filter(PersonRef.class::isInstance)
                .map(PersonRef.class::cast).filter(VillagerAmbitions::isAmbitious)
                .filter(villager -> home.mayor().filter(villager::equals).isEmpty())
                .sorted(Comparator.comparing(PersonRef::uuid)).toList();
    }

    private boolean found(PolityView home, PersonRef villager, Day day) {
        Optional<BlockPosition> seen = lastSeen.apply(villager);
        if (seen.isEmpty()) {
            return false;
        }
        Optional<BlockPosition> seat = site(seen.get());
        if (seat.isEmpty()) {
            return false; // weit und breit kein freier Ort
        }
        PolityName name;
        try {
            name = PolityName.of(nameFor(villager, app.queries()));
        } catch (DomainException ignored) {
            return false;
        }
        FoundingFees.Billing billing = fees.billing(app, home.currency());
        long fee = fees.municipality() * SETTLER_FEE_PERCENT / 100;
        // Zahlt der Dorfbewohner selbst, wenn er kann; sonst rüstet die Heimatgemeinde die Siedler aus der Kasse aus,
        // solange danach eine Reserve bleibt. Das Einkommen der Dorfbewohner fließt in die Gemeindekasse, sie selbst
        // sind fast nie flüssig; ohne diese Starthilfe gründeten sie nie eine Gemeinde.
        ActorRef payer = villager;
        if (billing.shortfallBalance(villager, fee) != null) {
            if (billing.shortfallBalance(home.asActor(), fee * TREASURY_RESERVE_FACTOR) != null) {
                return false; // weder der Dorfbewohner noch die Gemeinde können es tragen
            }
            payer = home.asActor();
        }
        try {
            app.leavePolity().execute(new LeavePolity.Command(home.id(), villager, day));
        } catch (DomainException ignored) {
            return false; // z. B. Amtsinhaber, die nicht gehen dürfen
        }
        try {
            billing.chargeAfter(payer, fee, day, () -> {
                app.foundPolity().execute(new FoundPolity.Command(name, villager, home.currency(), seat.get(), day));
                return null;
            });
        } catch (DomainException rejected) {
            rejoin(home, villager, day); // Claim-Regeln oder Namenskollision: der Dorfbewohner bleibt, wo er war
            return false;
        }
        PolityId created = app.queries().polityNamed(name.value()).orElseThrow().id();
        founded.accept(home.id(), created);
        relocate.accept(villager, seat.get());
        int followers = follow(home, created, villager, seat.get(), day);
        announcements.add(Announcement.toAll(Message.of(MessageKey.VILLAGER_TOWN_FOUNDED, name.value(),
                home.name().value(), followers + 1)));
        return true;
    }

    /** Mitbürger, die dem Gründer am nächsten sind, wechseln in die neue Gemeinde; @return wie viele. */
    private int follow(PolityView home, PolityId created, PersonRef founder, BlockPosition seat, Day day) {
        PolityView current = app.queries().polity(home.id()).orElse(home);
        int allowed = Math.min(MAX_FOLLOWERS, (int) villagerCount(current) - MIN_LEFT_BEHIND);
        if (allowed <= 0) {
            return 0;
        }
        List<PersonRef> near = current.members().stream().map(Membership::member)
                .filter(PersonRef.class::isInstance).map(PersonRef.class::cast)
                .filter(v -> !v.equals(founder) && current.mayor().filter(v::equals).isEmpty())
                .filter(v -> lastSeen.apply(v).filter(p -> p.dimension().equals(seat.dimension())).isPresent())
                .sorted(Comparator.comparingDouble((PersonRef v) -> distance(lastSeen.apply(v).orElseThrow(), seat))
                        .thenComparing(PersonRef::uuid))
                .toList();
        int moved = 0;
        for (PersonRef follower : near) {
            if (moved >= allowed) {
                break;
            }
            try {
                app.leavePolity().execute(new LeavePolity.Command(home.id(), follower, day));
            } catch (DomainException ignored) {
                continue;
            }
            try {
                app.joinPolity().execute(new JoinPolity.Command(created, follower, MembershipType.CITIZEN, day));
                relocate.accept(follower, seat);
                moved++;
            } catch (DomainException ignored) {
                rejoin(home, follower, day);
            }
        }
        return moved;
    }

    /**
     * Der Ort für das neue Gemeindehaus: der Aufenthaltsort selbst, wenn dort alles frei ist, sonst der nächstgelegene
     * Chunk im Umkreis, um den herum {@value #SITE_GAP} Chunks unbeansprucht sind.
     */
    Optional<BlockPosition> site(BlockPosition seen) {
        TerritoryView territory = app.queries().territory();
        ChunkCoord origin = seen.chunk();
        if (isFree(territory, origin) && habitable.test(origin)) {
            return Optional.of(seen);
        }
        ChunkCoord best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int dx = -SITE_RADIUS; dx <= SITE_RADIUS; dx++) {
            for (int dz = -SITE_RADIUS; dz <= SITE_RADIUS; dz++) {
                long distance = (long) dx * dx + (long) dz * dz;
                if (distance >= bestDistance) {
                    continue;
                }
                ChunkCoord candidate = new ChunkCoord(origin.dimension(), origin.x() + dx, origin.z() + dz);
                if (isFree(territory, candidate) && habitable.test(candidate)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best).map(chunk -> chunk.center(seen.y()));
    }

    private static boolean isFree(TerritoryView territory, ChunkCoord chunk) {
        for (int dx = -SITE_GAP; dx <= SITE_GAP; dx++) {
            for (int dz = -SITE_GAP; dz <= SITE_GAP; dz++) {
                if (territory.claimAt(new ChunkCoord(chunk.dimension(), chunk.x() + dx, chunk.z() + dz)).isPresent()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double distance(BlockPosition a, BlockPosition b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return dx * dx + dz * dz;
    }

    private void rejoin(PolityView home, PersonRef villager, Day day) {
        try {
            app.joinPolity().execute(new JoinPolity.Command(home.id(), villager, MembershipType.CITIZEN, day));
        } catch (DomainException ignored) {
            // Gemeinde nicht mehr erreichbar: dann ist er eben ohne Gemeinde und gründet am nächsten Tag erneut
        }
    }

    /** Ein freier, richtiger Ortsname (kein Kennzeichen, keine Zahl). */
    static String nameFor(PersonRef villager, de.etiennebader.civ.application.query.WorldQueries world) {
        return de.etiennebader.civ.application.population.FoundingNames.town(villager,
                name -> world.polityNamed(name).isPresent());
    }
}
