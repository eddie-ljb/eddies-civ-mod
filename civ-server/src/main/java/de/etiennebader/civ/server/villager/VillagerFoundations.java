package de.etiennebader.civ.server.villager;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.polity.FoundPolity;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.application.query.WorldQueries;
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
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.founding.FoundingFees;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 *   <li><b>Wo:</b> am letzten Aufenthaltsort. Gelten dort die Claim-Regeln nicht (zu nah an fremdem Gebiet, bereits
 *       beansprucht, Limit), bleibt der Dorfbewohner in seiner Gemeinde.</li>
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

    private final FoundingFees fees;
    private final CivApplication app;
    private final Function<PersonRef, Optional<BlockPosition>> lastSeen;
    private final Announcements announcements;

    public VillagerFoundations(CivApplication app, Function<PersonRef, Optional<BlockPosition>> lastSeen,
                               Announcements announcements) {
        this(app, lastSeen, announcements, FoundingFees.FREE);
    }

    /** Mit Gründungskosten: der Dorfbewohner zahlt aus seinem eigenen Konto. */
    public VillagerFoundations(CivApplication app, Function<PersonRef, Optional<BlockPosition>> lastSeen,
                               Announcements announcements, FoundingFees fees) {
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
        Optional<BlockPosition> seat = lastSeen.apply(villager);
        if (seat.isEmpty()) {
            return false;
        }
        PolityName name;
        try {
            name = PolityName.of(nameFor(villager));
        } catch (DomainException ignored) {
            return false;
        }
        FoundingFees.Billing billing = fees.billing(app, home.currency());
        if (billing.shortfallBalance(villager, fees.municipality()) != null) {
            return false; // zu arm für die Gründungsgebühr: bleibt, wo er ist
        }
        try {
            app.leavePolity().execute(new LeavePolity.Command(home.id(), villager, day));
        } catch (DomainException ignored) {
            return false; // z. B. Amtsinhaber, die nicht gehen dürfen
        }
        try {
            billing.chargeAfter(villager, fees.municipality(), day, () -> {
                app.foundPolity().execute(new FoundPolity.Command(name, villager, home.currency(), seat.get(), day));
                return null;
            });
        } catch (DomainException rejected) {
            rejoin(home, villager, day); // Claim-Regeln oder Namenskollision: der Dorfbewohner bleibt, wo er war
            return false;
        }
        PolityId created = app.queries().polityNamed(name.value()).orElseThrow().id();
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
                moved++;
            } catch (DomainException ignored) {
                rejoin(home, follower, day);
            }
        }
        return moved;
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

    /** Kurzes, eindeutiges Namenskennzeichen je Dorfbewohner. */
    static String nameFor(PersonRef villager) {
        return "Neustadt " + Long.toHexString(villager.uuid().getLeastSignificantBits() & 0xFFFFFL).toUpperCase();
    }
}
