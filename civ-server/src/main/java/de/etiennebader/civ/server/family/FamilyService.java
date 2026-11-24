package de.etiennebader.civ.server.family;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.family.Family;
import de.etiennebader.civ.domain.family.FamilyRegistry;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.VillagerIdentity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Heirat, Scheidung und Kinder der Dorfbewohner (Issue #27, Nachtrag).
 *
 * <p>Jeden Tag lernen sich ledige Dorfbewohner einer Gemeinde paarweise kennen. Wie die Beziehung wächst, hängt von der
 * Verträglichkeit ihrer Persönlichkeiten ab; ab einer Beziehung von {@value Family#MARRY_BOND} heiraten sie. Ehepaare
 * mit schwindender Beziehung lassen sich scheiden (Bildung macht das seltener); Ehepaare mit guter Beziehung bekommen
 * Kinder, deren Wunschzahl von Gemeinsinn, Heimatverbundenheit und Bildung abhängt. Ein Kind wird erst mit dem
 * Erwachsenwerden vom Spiel als Dorfbewohner gemeldet; bis dahin liegt es in {@link #takeBirths()} für das Spiel bereit.
 */
public final class FamilyService {

    private final WorldQueries queries;
    private final Announcements announcements;
    private final FamilyStore store;
    private final FamilyRegistry registry;
    private final List<FamilyRegistry.Child> pendingBirths = new ArrayList<>();

    public FamilyService(WorldQueries queries, Announcements announcements, Optional<Path> file) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.store = new FamilyStore(file);
        this.registry = store.load();
    }

    public FamilyRegistry registry() {
        return registry;
    }

    /** Geburten, die das Spiel noch als Kind-Dorfbewohner auf die Welt bringen soll; die Liste wird geleert. */
    public synchronized List<FamilyRegistry.Child> takeBirths() {
        List<FamilyRegistry.Child> births = List.copyOf(pendingBirths);
        pendingBirths.clear();
        return births;
    }

    /** Geburten, die das Spiel noch nicht als Baby-Dorfbewohner auf die Welt gebracht hat (ohne sie zu entfernen). */
    public synchronized List<FamilyRegistry.Child> pendingBirths() {
        return List.copyOf(pendingBirths);
    }

    /** Das Spiel hat das Kind auf die Welt gebracht; es muss nicht mehr abgeholt werden. */
    public synchronized void born(de.etiennebader.civ.domain.shared.PersonId child) {
        pendingBirths.removeIf(pending -> pending.id().equals(child));
    }

    /** {@code /civ familie liste}: Ehepaare der eigenen Gemeinde. */
    public synchronized Reply couples(Caller caller) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        Set<PersonRef> members = new HashSet<>(adultsOf(town.get()));
        List<Message> lines = new ArrayList<>();
        for (FamilyRegistry.Marriage marriage : registry.marriages()) {
            if (members.contains(marriage.first()) || members.contains(marriage.second())) {
                lines.add(Message.of(MessageKey.FAMILY_LIST_ENTRY, VillagerIdentity.of(marriage.first()).fullName(),
                        VillagerIdentity.of(marriage.second()).fullName(), marriage.since().value(),
                        marriage.children(), registry.bond(marriage.first(), marriage.second())));
            }
        }
        if (lines.isEmpty()) {
            return Reply.success(Message.of(MessageKey.FAMILY_LIST_EMPTY, town.get().name().toString()));
        }
        lines.add(0, Message.of(MessageKey.FAMILY_LIST_HEADER, town.get().name().toString(), lines.size()));
        return Reply.success(lines);
    }

    /** {@code /civ familie kinder}: Kinder der Ehepaare der eigenen Gemeinde. */
    public synchronized Reply kids(Caller caller) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        Set<PersonRef> members = new HashSet<>(adultsOf(town.get()));
        List<Message> lines = new ArrayList<>();
        for (FamilyRegistry.Child child : registry.children()) {
            if (members.contains(child.parent1()) || members.contains(child.parent2())) {
                lines.add(Message.of(MessageKey.FAMILY_CHILD_ENTRY, VillagerIdentity.of(child.ref()).fullName(),
                        VillagerIdentity.of(child.parent1()).fullName(), VillagerIdentity.of(child.parent2()).fullName(),
                        child.born().value()));
            }
        }
        if (lines.isEmpty()) {
            return Reply.success(Message.of(MessageKey.FAMILY_CHILDREN_EMPTY, town.get().name().toString()));
        }
        return Reply.success(lines);
    }

    /** Ein Dorfbewohner ist gestorben oder verschwunden: Ehe endet, Beziehungen verfallen. */
    public synchronized void gone(PersonRef villager, Day day) {
        registry.gone(villager, day);
        store.save(registry);
    }

    /** Familienzeile zu einem Dorfbewohner für die Auskunft, falls es etwas zu sagen gibt. */
    public synchronized List<Message> describe(PersonRef villager) {
        List<Message> lines = new ArrayList<>();
        registry.spouseOf(villager).ifPresent(spouse -> lines.add(Message.of(MessageKey.VILLAGER_SPOUSE,
                VillagerIdentity.of(spouse).fullName(), registry.bond(villager, spouse))));
        int kids = registry.childrenOf(villager).size();
        if (kids > 0) {
            lines.add(Message.of(MessageKey.VILLAGER_CHILDREN, kids));
        }
        return lines;
    }

    /** Täglich: Ehen prüfen, Ledige zusammenführen. */
    public synchronized void endOfDay(Day day) {
        Set<PersonRef> everyone = new HashSet<>();
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            List<PersonRef> adults = adultsOf(town);
            everyone.addAll(adults);
            marriedLife(town, adults, day);
            meet(town, adults, day);
        }
        for (FamilyRegistry.Marriage marriage : registry.marriages()) {
            if (!everyone.contains(marriage.first()) || !everyone.contains(marriage.second())) {
                registry.gone(marriage.first(), day);
            }
        }
        store.save(registry);
    }

    private List<PersonRef> adultsOf(PolityView town) {
        List<PersonRef> adults = new ArrayList<>();
        for (Membership membership : town.members()) {
            if (membership.member() instanceof PersonRef person) {
                adults.add(person);
            }
        }
        adults.sort((a, b) -> a.uuid().compareTo(b.uuid()));
        return adults;
    }

    private long residentDays(PolityView town, PersonRef person, Day day) {
        return town.members().stream().filter(membership -> membership.member().equals(person)).findFirst()
                .map(membership -> Math.max(0, day.value() - membership.since().value())).orElse(0L);
    }

    private void marriedLife(PolityView town, List<PersonRef> adults, Day day) {
        Set<PersonRef> here = new HashSet<>(adults);
        for (FamilyRegistry.Marriage marriage : registry.marriages()) {
            if (!here.contains(marriage.first()) || !here.contains(marriage.second())) {
                continue; // das Paar lebt in einer anderen Gemeinde
            }
            Random dice = new Random(day.value() * 7919L + marriage.first().uuid().hashCode() * 31L
                    + marriage.second().uuid().hashCode());
            int compat = Family.compatibility(marriage.first(), marriage.second());
            int bond = registry.changeBond(marriage.first(), marriage.second(), Family.dailyBondChange(compat, true));
            int eduA = Family.education(marriage.first(), residentDays(town, marriage.first(), day));
            int eduB = Family.education(marriage.second(), residentDays(town, marriage.second(), day));
            int divorceChance = Family.divorceChancePerMille(bond, compat, eduA, eduB);
            if (divorceChance > 0 && dice.nextInt(1000) < divorceChance) {
                registry.divorce(marriage.first(), day);
                registry.changeBond(marriage.first(), marriage.second(), -100);
                announce(town, MessageKey.FAMILY_DIVORCED, marriage.first(), marriage.second());
                continue;
            }
            int wanted = Family.desiredChildren(marriage.first(), marriage.second(), eduA, eduB);
            boolean spaced = day.value() - marriage.lastBirth().value() >= Family.BIRTH_GAP_DAYS;
            if (marriage.children() < wanted && spaced && dice.nextInt(1000) < Family.birthChancePerMille(bond)) {
                FamilyRegistry.Child child = registry.birth(marriage.first(), day);
                pendingBirths.add(child);
                announce(town, MessageKey.FAMILY_BIRTH, marriage.first(), marriage.second());
            }
        }
    }

    /**
     * Ledige lernen sich kennen: Jeden Tag finden sich die am besten zueinander passenden Ledigen zu Paaren und
     * verbringen den Tag miteinander. Dadurch wächst die Beziehung eines Paares an aufeinanderfolgenden Tagen
     * (statt nur an den seltenen Tagen, an denen der Zufall genau diese beiden zusammenführt), und aus passenden
     * Paaren werden in einer guten Woche Eheleute. Wer zu niemandem passt, bleibt für sich.
     */
    private void meet(PolityView town, List<PersonRef> adults, Day day) {
        List<PersonRef> singles = adults.stream().filter(person -> !registry.isMarried(person)).toList();
        record Match(PersonRef first, PersonRef second, int compat, int bond) {
        }
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < singles.size(); i++) {
            for (int j = i + 1; j < singles.size(); j++) {
                PersonRef first = singles.get(i);
                PersonRef second = singles.get(j);
                int compat = Family.compatibility(first, second);
                if (compat >= Family.GOOD_MATCH && !related(first, second)) {
                    matches.add(new Match(first, second, compat, registry.bond(first, second)));
                }
            }
        }
        // Zuerst Paare mit der größten Beziehung, dann die verträglichsten: wer sich kennt, bleibt zusammen.
        matches.sort(Comparator.comparingInt(Match::bond).reversed().thenComparing(
                Comparator.comparingInt(Match::compat).reversed()));
        Set<PersonRef> taken = new HashSet<>();
        for (Match match : matches) {
            if (taken.contains(match.first()) || taken.contains(match.second())) {
                continue;
            }
            taken.add(match.first());
            taken.add(match.second());
            int bond = registry.changeBond(match.first(), match.second(),
                    Family.dailyBondChange(match.compat(), false));
            if (bond >= Family.MARRY_BOND) {
                registry.marry(match.first(), match.second(), day);
                registry.changeBond(match.first(), match.second(), Family.WEDDING_BONUS);
                announce(town, MessageKey.FAMILY_MARRIED, match.first(), match.second());
            }
        }
    }

    /** Eltern und Kinder sowie Geschwister heiraten nicht untereinander. */
    private boolean related(PersonRef first, PersonRef second) {
        var a = registry.child(first);
        var b = registry.child(second);
        if (a.isPresent() && (a.get().parent1().equals(second) || a.get().parent2().equals(second))) {
            return true;
        }
        if (b.isPresent() && (b.get().parent1().equals(first) || b.get().parent2().equals(first))) {
            return true;
        }
        return a.isPresent() && b.isPresent() && (a.get().parent1().equals(b.get().parent1())
                || a.get().parent2().equals(b.get().parent2()));
    }

    private void announce(PolityView town, MessageKey key, PersonRef first, PersonRef second) {
        announcements.add(Announcement.toTown(town.id(), Message.of(key, VillagerIdentity.of(first).fullName(),
                VillagerIdentity.of(second).fullName(), town.name().toString())));
    }
}
