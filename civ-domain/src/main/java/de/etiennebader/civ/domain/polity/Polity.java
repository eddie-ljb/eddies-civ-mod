package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.LawBook;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregat Gemeinwesen: wer dazugehört und welches Recht gilt.
 *
 * <p>Invarianten: Eine Polity hat immer mindestens ein Mitglied, jeder Akteur ist höchstens
 * einmal Mitglied, und eine Polity kann nicht von einer anderen Polity gegründet werden.
 */
public final class Polity extends AggregateRoot<PolityEvent> implements PolityView {

    private final PolityId id;
    private final PolityName name;
    private final PolityKind kind;
    private final SettlementTier tier;
    private final CurrencyId currency;
    private final Day foundedOn;
    private final Optional<BlockPosition> seat;
    private final Map<ActorRef, Membership> members = new LinkedHashMap<>();
    private final LawBook lawBook = new LawBook();

    private Polity(PolityId id, PolityName name, PolityKind kind, SettlementTier tier,
                   CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.tier = Objects.requireNonNull(tier, "tier");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.foundedOn = Objects.requireNonNull(foundedOn, "foundedOn");
        this.seat = Objects.requireNonNull(seat, "seat");
    }

    /**
     * Gründet eine Gemeinde mit ihrem Sitz, dem Standort des Gemeindehauses. Der Gründer wird erster Bürger.
     *
     * @throws IllegalFounderException wenn der Gründer selbst eine Polity ist
     */
    public static Polity found(PolityId id, PolityName name, ActorRef founder, CurrencyId currency, BlockPosition seat,
                               Day day) {
        Objects.requireNonNull(founder, "founder");
        Objects.requireNonNull(seat, "seat");
        if (founder instanceof PolityRef) {
            throw new IllegalFounderException(founder);
        }
        Polity polity = new Polity(id, name, PolityKind.TERRITORIAL, SettlementTier.MUNICIPALITY, currency, day,
                Optional.of(seat));
        polity.members.put(founder, new Membership(founder, MembershipType.CITIZEN, day));
        polity.raise(new PolityFounded(id, name, founder, seat, day));
        return polity;
    }

    /**
     * Stellt eine gespeicherte Polity wieder her, ohne Events auszulösen. Mitglieder und Gesetze
     * werden in der übergebenen Reihenfolge übernommen; Gesetze in der Reihenfolge ihres Erlasses.
     *
     * @param seat leer bei Polities aus der Zeit, bevor es Gemeindehäuser gab
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben
     */
    public static Polity restore(PolityId id, PolityName name, PolityKind kind, SettlementTier tier, CurrencyId currency,
                                 Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                                 List<EnactedStatute> statutes) {
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(statutes, "statutes");
        Polity polity = new Polity(id, name, kind, tier, currency, foundedOn, seat);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("Gespeicherte Polity ohne Mitglieder: " + id);
        }
        for (Membership membership : members) {
            if (membership.member().equals(polity.asActor())) {
                throw new IllegalArgumentException("Polity ist Mitglied ihrer selbst: " + id);
            }
            if (polity.members.putIfAbsent(membership.member(), membership) != null) {
                throw new IllegalArgumentException("Doppelte Mitgliedschaft in " + id + ": " + membership.member());
            }
        }
        statutes.forEach(enacted -> polity.lawBook.enact(enacted.statute(), enacted.enactedOn()));
        return polity;
    }

    /**
     * Nimmt einen Akteur als Mitglied auf.
     *
     * @throws AlreadyMemberException wenn er bereits Mitglied ist
     */
    public void admit(ActorRef member, MembershipType type, Day day) {
        Objects.requireNonNull(member, "member");
        if (member.equals(asActor())) {
            throw new IllegalArgumentException("Eine Polity kann nicht Mitglied ihrer selbst sein");
        }
        if (isMember(member)) {
            throw new AlreadyMemberException(member);
        }
        members.put(member, new Membership(member, type, day));
        raise(new MemberAdmitted(id, member, type, day));
    }

    /**
     * Ein Mitglied tritt aus.
     *
     * @throws NotAMemberException wenn der Akteur kein Mitglied ist
     * @throws LastMemberException wenn er das letzte Mitglied ist
     */
    public void leave(ActorRef member, Day day) {
        Objects.requireNonNull(day, "day");
        if (!isMember(member)) {
            throw new NotAMemberException(member);
        }
        if (members.size() == 1) {
            throw new LastMemberException(member);
        }
        members.remove(member);
        raise(new MemberLeft(id, member, day));
    }

    /**
     * Erlässt ein Gesetz. In Phase 0 ein direkter Aufruf; Abstimmungen und Organe
     * schieben sich später als Use Case davor.
     */
    public void enact(Statute statute, Day day) {
        lawBook.enact(statute, day);
        raise(new StatuteEnacted(id, statute.id(), day));
    }

    /**
     * Hebt ein geltendes Gesetz auf.
     *
     * @throws de.etiennebader.civ.domain.law.UnknownStatuteException wenn es nicht gilt
     */
    public void repeal(StatuteId statuteId, Day day) {
        Objects.requireNonNull(day, "day");
        lawBook.repeal(statuteId);
        raise(new StatuteRepealed(id, statuteId, day));
    }

    @Override
    public boolean isMember(ActorRef actor) {
        return members.containsKey(Objects.requireNonNull(actor, "actor"));
    }

    @Override
    public Optional<Membership> membershipOf(ActorRef actor) {
        return Optional.ofNullable(members.get(Objects.requireNonNull(actor, "actor")));
    }

    /** Alle Mitgliedschaften in Beitrittsreihenfolge, nur lesend. */
    @Override
    public Collection<Membership> members() {
        return List.copyOf(members.values());
    }

    @Override
    public int memberCount() {
        return members.size();
    }

    /** Das geltende Recht, nur lesend. Änderungen laufen über {@link #enact} und {@link #repeal}. */
    @Override
    public LawView laws() {
        return lawBook;
    }

    @Override
    public Optional<BlockPosition> seat() {
        return seat;
    }

    /** Die Polity als handelnder Akteur, z. B. als Kontoinhaberin oder Grundbesitzerin. */
    @Override
    public PolityRef asActor() {
        return new PolityRef(id);
    }

    @Override
    public PolityId id() {
        return id;
    }

    @Override
    public PolityName name() {
        return name;
    }

    @Override
    public PolityKind kind() {
        return kind;
    }

    @Override
    public SettlementTier tier() {
        return tier;
    }

    @Override
    public CurrencyId currency() {
        return currency;
    }

    @Override
    public Day foundedOn() {
        return foundedOn;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Polity polity && id.equals(polity.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Polity[" + name + ", " + id.value() + "]";
    }
}
