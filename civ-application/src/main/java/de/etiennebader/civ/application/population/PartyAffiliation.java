package de.etiennebader.civ.application.population;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Wie Dorfbewohner sich Parteien anschließen: Ihre Persönlichkeit ergibt eine Position auf den Achsen Ordnung
 * (Ordnungsliebe) und Wirtschaft (sparsam = niedrige Steuern, großzügig = Ausbau).
 *
 * <ul>
 *   <li>Wer keiner Partei angehört und einer nahe ist (Abstand ≤ {@value #JOIN_WITHIN}), tritt mit 25 % am Tag bei.</li>
 *   <li>Wer sich von seiner Partei weit entfernt hat (Abstand > {@value #LEAVE_BEYOND}), tritt aus.</li>
 *   <li>Finden sich in einer Gemeinde mindestens so viele Parteilose derselben Richtung wie verlangt und gibt es keine
 *   passende Partei, gründen sie eine; höchstens eine Gründung am Tag.</li>
 * </ul>
 */
public final class PartyAffiliation {

    public static final double JOIN_WITHIN = 70;
    public static final double LEAVE_BEYOND = 130;
    static final double JOIN_CHANCE = 0.25;
    static final int ARCHETYPE = 60;

    /** Ein Schritt, den der Server ausführt. */
    public sealed interface Step permits Join, Leave, Found {
    }

    public record Join(PersonRef villager, PolityId party) implements Step {
    }

    public record Leave(PersonRef villager, PolityId party) implements Step {
    }

    /**
     * @param direction Grundrichtung, z. B. für den Namen
     * @param members   Gründungsmitglieder; das erste ist der Vorsitz (der Heimatverbundenste)
     */
    public record Found(Platform direction, PolityId town, List<PersonRef> members, int order, int economy)
            implements Step {

        public Found {
            members = List.copyOf(members);
        }
    }

    private PartyAffiliation() {
    }

    /** Position eines Dorfbewohners: (Ordnung, Wirtschaft). */
    public static int[] position(Personality personality) {
        return new int[]{personality.order(), -personality.thrift()};
    }

    public static List<Step> plan(List<? extends PolityView> polities, Day day, int minFounders) {
        Objects.requireNonNull(polities, "polities");
        Objects.requireNonNull(day, "day");
        List<PolityView> parties = polities.stream().filter(polity -> polity.kind() == PolityKind.PARTY)
                .map(PolityView.class::cast).toList();
        Map<PersonRef, PolityView> partyOf = new HashMap<>();
        for (PolityView party : parties) {
            for (Membership membership : party.members()) {
                if (membership.member() instanceof PersonRef person) {
                    partyOf.put(person, party);
                }
            }
        }
        List<Step> steps = new ArrayList<>();
        boolean founded = false;
        for (PolityView town : polities) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            Map<Platform, List<PersonRef>> groups = new EnumMap<>(Platform.class);
            for (Membership membership : town.members()) {
                if (!(membership.member() instanceof PersonRef villager)) {
                    continue;
                }
                int[] position = position(Personality.of(villager));
                PolityView current = partyOf.get(villager);
                if (current != null) {
                    double distance = current.partyProfile().orElseThrow().distanceTo(position[0], position[1]);
                    if (distance > LEAVE_BEYOND) {
                        steps.add(new Leave(villager, current.id()));
                    }
                    continue;
                }
                Optional<PolityView> nearest = nearest(parties, position);
                if (nearest.isPresent() && distance(nearest.get(), position) <= JOIN_WITHIN) {
                    if (roll(villager, day) < JOIN_CHANCE) {
                        steps.add(new Join(villager, nearest.get().id()));
                    }
                    continue;
                }
                archetype(position).ifPresent(direction ->
                        groups.computeIfAbsent(direction, ignored -> new ArrayList<>()).add(villager));
            }
            if (founded) {
                continue;
            }
            for (Map.Entry<Platform, List<PersonRef>> group : groups.entrySet()) {
                if (group.getValue().size() < minFounders) {
                    continue;
                }
                int order = (int) Math.round(group.getValue().stream()
                        .mapToInt(villager -> Personality.of(villager).order()).average().orElse(0));
                int economy = (int) Math.round(group.getValue().stream()
                        .mapToInt(villager -> -Personality.of(villager).thrift()).average().orElse(0));
                boolean covered = parties.stream().anyMatch(party -> distance(party, new int[]{order, economy})
                        <= JOIN_WITHIN);
                if (covered) {
                    continue;
                }
                List<PersonRef> members = group.getValue().stream()
                        .sorted(Comparator.comparingInt((PersonRef villager) -> -Personality.of(villager).belonging())
                                .thenComparing(villager -> villager.uuid().toString()))
                        .toList();
                steps.add(new Found(group.getKey(), town.id(), members, order, economy));
                founded = true;
                break;
            }
        }
        return steps;
    }

    /** Die Grundrichtung, der eine Position nahe genug ist, um mit Gleichgesinnten eine Partei zu gründen. */
    static Optional<Platform> archetype(int[] position) {
        Platform best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Platform platform : List.of(Platform.ORDER, Platform.FREEDOM, Platform.LOW_TAXES, Platform.INVESTMENT)) {
            int[] centre = switch (platform) {
                case ORDER -> new int[]{ARCHETYPE, 0};
                case FREEDOM -> new int[]{-ARCHETYPE, 0};
                case LOW_TAXES -> new int[]{0, -ARCHETYPE};
                default -> new int[]{0, ARCHETYPE};
            };
            double distance = Math.hypot(position[0] - centre[0], position[1] - centre[1]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = platform;
            }
        }
        return bestDistance <= JOIN_WITHIN ? Optional.of(best) : Optional.empty();
    }

    private static Optional<PolityView> nearest(List<PolityView> parties, int[] position) {
        return parties.stream().min(Comparator.comparingDouble(party -> distance(party, position)));
    }

    private static double distance(PolityView party, int[] position) {
        return party.partyProfile().map(profile -> profile.distanceTo(position[0], position[1]))
                .orElse(Double.MAX_VALUE);
    }

    /** Fester Zufallswert in [0, 1) je Dorfbewohner und Tag. */
    static double roll(PersonRef villager, Day day) {
        long mixed = villager.uuid().getMostSignificantBits() ^ (day.value() * 0x9E3779B97F4A7C15L);
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53;
    }

    /** Ein passendes Profil für eine neue Partei dieser Richtung. */
    public static PartyProfile profileFor(Found found) {
        String emblem = switch (found.direction()) {
            case ORDER -> "minecraft:blue_banner";
            case FREEDOM -> "minecraft:yellow_banner";
            case LOW_TAXES -> "minecraft:green_banner";
            case INVESTMENT -> "minecraft:orange_banner";
            default -> PartyProfile.DEFAULT_EMBLEM;
        };
        return new PartyProfile(clamp(found.order()), clamp(found.economy()), emblem);
    }

    private static int clamp(int value) {
        return Math.max(-PartyProfile.MAX, Math.min(PartyProfile.MAX, value));
    }
}
