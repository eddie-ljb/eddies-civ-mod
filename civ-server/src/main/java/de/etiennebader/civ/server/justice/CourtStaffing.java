package de.etiennebader.civ.server.justice;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.justice.CourtStaff;
import de.etiennebader.civ.domain.justice.Role;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Texts;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Besetzt Richter- und Staatsanwaltsstellen, wie es die Verfassung ({@code court_staffing}) vorgibt (Issue #84):
 * <ul>
 *     <li>{@code automatic}: die Gemeinde ernennt den gebildetsten freien Dorfbewohner;</li>
 *     <li>{@code election}: alle Dorfbewohner stimmen ab (Kandidaten: Dorfbewohner und Spieler, die kandidieren), die Mehrheit entscheidet (Gebildete, Ordnungsliebende gewinnen
 *         eher; die Stimmabgabe ist deterministisch je Tag und Wähler);</li>
 *     <li>{@code head}/{@code government}: Spieler ernennen selbst (siehe {@code JusticeUseCases.appoint}).</li>
 * </ul>
 * Es werden nur Vakanzen gefüllt: je eine Stelle, ab 24 Mitgliedern zwei.
 */
final class CourtStaffing {

    /** Eignung eines Spielers ohne verfolgten Bildungsstand (wie bei Dorfbewohnern ohne Daten). */
    private static final int NEUTRAL = 50;

    private final CivApplication app;
    private final WorldQueries queries;
    private final Texts texts;
    private final Announcements announcements;

    private final CourtCandidacies candidacies;

    CourtStaffing(CivApplication app, Texts texts, Announcements announcements, CourtCandidacies candidacies) {
        this.candidacies = candidacies;
        this.app = app;
        this.queries = app.queries();
        this.texts = texts;
        this.announcements = announcements;
    }

    void endOfDay(Day day) {
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL && town.kind() != PolityKind.FEDERATION) {
                continue;
            }
            String mode = queries.effectiveLaws(town).valueOf(StandardParameters.COURT_STAFFING);
            if (!mode.equals("automatic") && !mode.equals("election")) {
                continue;
            }
            int seats = town.members().size() >= 24 ? 2 : 1;
            for (Role role : List.of(Role.JUDGE, Role.PROSECUTOR)) {
                fill(town, role, seats, mode.equals("election"), day);
            }
        }
    }

    private void fill(PolityView town, Role role, int seats, boolean election, Day day) {
        CourtStaff staff = app.justice().staffOf(town.id());
        int held = (role == Role.JUDGE ? staff.judges() : staff.prosecutors()).size();
        if (held >= seats) {
            return;
        }
        List<ActorRef> candidates = new java.util.ArrayList<>(town.members().stream().map(Membership::member)
                .filter(PersonRef.class::isInstance).toList());
        if (election) {
            // Spieler kandidieren freiwillig (/civ <Ebene> gericht kandidieren) und müssen noch Mitglied sein
            candidacies.of(town.id()).stream().filter(town::isMember).forEach(candidates::add);
        }
        candidates.removeIf(c -> staff.judges().contains(c) || staff.prosecutors().contains(c));
        if (candidates.isEmpty()) {
            return;
        }
        Optional<ActorRef> winner = election ? elect(town, candidates, role, day) : candidates.stream()
                .max(Comparator.comparingInt(this::suitability).thenComparing(c -> c.uuid().toString()));
        winner.ifPresent(person -> {
            app.justice().fillSeat(town.id(), role, person, day);
            candidacies.withdraw(town.id(), person);
            announcements.add(Announcement.toTown(town.id(), Message.of(
                    election ? MessageKey.COURT_ELECTED : MessageKey.COURT_AUTO_APPOINTED, texts.actor(person),
                    JusticeService.roleName(role), town.name().toString())));
        });
    }

    /** Jeder Dorfbewohner wählt den Kandidaten mit der besten Eignung (Bildung plus Eindruck von 0 bis 40). */
    private int suitability(ActorRef candidate) {
        return candidate instanceof PersonRef villager ? app.villagerEducation().bonusOf(villager) : NEUTRAL;
    }

    private Optional<ActorRef> elect(PolityView town, List<ActorRef> candidates, Role role, Day day) {
        Map<ActorRef, Integer> tally = new HashMap<>();
        for (Membership membership : town.members()) {
            if (!(membership.member() instanceof PersonRef voter)) {
                continue;
            }
            Random impression = new Random(day.value() * 104_729L + voter.uuid().getLeastSignificantBits()
                    + role.ordinal());
            ActorRef best = null;
            int bestScore = Integer.MIN_VALUE;
            for (ActorRef candidate : candidates) {
                int score = suitability(candidate) + impression.nextInt(41);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            tally.merge(best, 1, Integer::sum);
        }
        return tally.entrySet().stream().max(Map.Entry.<ActorRef, Integer>comparingByValue()
                .thenComparing(e -> e.getKey().uuid().toString())).map(Map.Entry::getKey);
    }
}
