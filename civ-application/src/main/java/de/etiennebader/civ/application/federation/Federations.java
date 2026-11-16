package de.etiennebader.civ.application.federation;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Hält die Bürger der Bünde synchron: Wer Mitglied einer Gemeinde ist, ist mit derselben Art der Mitgliedschaft auch
 * Mitglied jedes Bundes darüber. So funktionieren Wahlen, Ämter und Abstimmungen auf jeder Ebene genauso wie in einer
 * Gemeinde.
 */
public final class Federations {

    private Federations() {
    }

    /** Der Bund, dem die Polity direkt angehört. */
    public static Optional<Polity> parentOf(Collection<Polity> polities, PolityId id) {
        Objects.requireNonNull(id, "id");
        PolityRef ref = new PolityRef(id);
        return polities.stream()
                .filter(polity -> polity.kind() == PolityKind.FEDERATION && polity.isMember(ref))
                .findFirst();
    }

    /** Alle Bünde über der Polity, vom nächsten bis zum obersten. */
    public static List<Polity> ancestorsOf(Collection<Polity> polities, PolityId id) {
        List<Polity> chain = new ArrayList<>();
        Set<PolityId> seen = new HashSet<>();
        Optional<Polity> parent = parentOf(polities, id);
        while (parent.isPresent() && seen.add(parent.get().id())) {
            chain.add(parent.get());
            parent = parentOf(polities, parent.get().id());
        }
        return chain;
    }

    /** Ein Spieler oder Dorfbewohner ist einer Gemeinde beigetreten: auch allen Bünden darüber. */
    public static void memberJoined(Aggregates aggregates, PolityId polity, ActorRef member, MembershipType type,
                                    Day day) {
        if (member instanceof PolityRef) {
            return;
        }
        for (Polity ancestor : ancestorsOf(aggregates.repositories().polities().findAll(), polity)) {
            if (!ancestor.isMember(member)) {
                ancestor.admit(member, type, day);
                aggregates.commit(ancestor);
            }
        }
    }

    /** Ein Spieler oder Dorfbewohner hat eine Gemeinde verlassen: auch alle Bünde darüber. */
    public static void memberLeft(Aggregates aggregates, PolityId polity, ActorRef member, Day day) {
        if (member instanceof PolityRef) {
            return;
        }
        for (Polity ancestor : ancestorsOf(aggregates.repositories().polities().findAll(), polity)) {
            if (ancestor.isMember(member) && ancestor.members().size() > 1) {
                ancestor.leave(member, day);
                aggregates.commit(ancestor);
            }
        }
    }

    /** Spieler und Dorfbewohner einer Polity, ohne Gliedkörperschaften. */
    static List<Membership> people(PolityView polity) {
        return polity.members().stream()
                .filter(membership -> membership.type() != MembershipType.CONSTITUENT)
                .filter(membership -> !(membership.member() instanceof PolityRef))
                .toList();
    }

    /** Nimmt die Polity samt ihren Leuten in den Bund und alle Bünde darüber auf. */
    static void attach(Aggregates aggregates, Polity federation, Polity constituent, Day day) {
        federation.admit(constituent.asActor(), MembershipType.CONSTITUENT, day);
        aggregates.commit(federation);
        for (Membership person : people(constituent)) {
            if (!federation.isMember(person.member())) {
                federation.admit(person.member(), person.type(), day);
            }
            memberJoined(aggregates, federation.id(), person.member(), person.type(), day);
        }
        aggregates.commit(federation);
    }

    /**
     * Löst die Polity aus ihrem Bund; ihre Leute verlassen ihn und alle Bünde darüber.
     *
     * @return der Bund, den sie verlassen hat
     */
    public static Optional<Polity> detach(Aggregates aggregates, Polity constituent, Day day) {
        Optional<Polity> parent = parentOf(aggregates.repositories().polities().findAll(), constituent.id());
        if (parent.isEmpty()) {
            return Optional.empty();
        }
        List<Polity> ancestors = ancestorsOf(aggregates.repositories().polities().findAll(), constituent.id());
        for (Membership person : people(constituent)) {
            for (Polity ancestor : ancestors) {
                if (ancestor.isMember(person.member()) && ancestor.members().size() > 1) {
                    ancestor.leave(person.member(), day);
                }
            }
        }
        parent.get().leave(constituent.asActor(), day);
        ancestors.forEach(aggregates::commit);
        return parent;
    }
}
