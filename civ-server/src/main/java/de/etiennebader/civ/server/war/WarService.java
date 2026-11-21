package de.etiennebader.civ.server.war;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.economy.TransferFunds;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.economy.TransferReason;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.war.BattleOutcome;
import de.etiennebader.civ.domain.war.MilitaryRank;
import de.etiennebader.civ.domain.war.PeaceTerms;
import de.etiennebader.civ.domain.war.War;
import de.etiennebader.civ.domain.war.WarRegistry;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Krieg zwischen Gemeinden (Issue #27, erste spielbare Ausbaustufe).
 *
 * <ul>
 *     <li>Nur der Bürgermeister einer Gemeinde erklärt Krieg, schließt Frieden, kauft Neutralität und löst Gefechte aus.</li>
 *     <li>Spieler lassen sich als Soldat oder Offizier in ihre Gemeinde einschreiben. Im Krieg melden sich außerdem
 *         kriegerische Dorfbewohner (niedrige {@link Personality#peace()}) von selbst: Je kriegerischer, desto
 *         eher als Offizier.</li>
 *     <li>Soldaten und Offiziere kosten täglich Sold aus der Gemeindekasse
 *         ({@link StandardParameters#SOLDIER_WAGE}, {@link StandardParameters#OFFICER_WAGE}); reicht das Geld nicht,
 *         wird der Betreffende entlassen.</li>
 *     <li>Frieden ist nicht umsonst: Neutralität kauft eine Gemeinde für
 *         {@link StandardParameters#NEUTRALITY_FEE_PER_DAY} je Tag beim übergeordneten Bund (Land/Nation), der sie
 *         dafür schützt; Gemeinden ohne Bund können sie nicht kaufen.</li>
 * </ul>
 *
 * <p>Der Kriegszustand wird in diesem Stand nicht über einen Neustart hinweg gespeichert, siehe {@code docs/issue-27-krieg.md}.
 */
public final class WarService {

    /** Ab dieser Friedfertigkeit (kleiner oder gleich) meldet sich ein Dorfbewohner im Krieg als Soldat. */
    static final int VILLAGER_SOLDIER_PEACE = -20;
    /** Ab dieser Friedfertigkeit (kleiner oder gleich) als Offizier. */
    static final int VILLAGER_OFFICER_PEACE = -60;

    private final CivApplication app;
    private final WorldQueries queries;
    private final de.etiennebader.civ.application.day.Calendar calendar;
    private final Texts texts;
    private final Announcements announcements;
    private final IdGenerator ids;
    private final WarRegistry registry = WarRegistry.empty();

    public WarService(CivApplication app, de.etiennebader.civ.application.day.Calendar calendar, Texts texts,
                      Announcements announcements, IdGenerator ids) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public WarRegistry registry() {
        return registry;
    }

    // ------------------------------------------------------------------ Befehle

    /** Der Bürgermeister erklärt einer anderen Gemeinde den Krieg. */
    public synchronized Reply declare(Caller caller, String targetName) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty() || !isMayor(caller, own.get())) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_MAYOR));
        }
        Optional<PolityView> target = queries.polityNamed(targetName)
                .filter(polity -> polity.kind() == PolityKind.TERRITORIAL);
        if (target.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_UNKNOWN_POLITY, targetName));
        }
        if (target.get().id().equals(own.get().id())) {
            return Reply.failure(Message.of(MessageKey.WAR_SELF));
        }
        try {
            registry.declareWar(ids, own.get().id(), target.get().id(), calendar.today());
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        announcements.add(Announcement.toAll(Message.of(MessageKey.WAR_DECLARED, own.get().name().toString(),
                target.get().name().toString())));
        return Reply.success(Message.of(MessageKey.WAR_DECLARED, own.get().name().toString(),
                target.get().name().toString()));
    }

    /** Alle Kriege, Nummer = Reihenfolge der Erklärung. */
    public synchronized Reply list() {
        List<War> wars = registry.wars();
        if (wars.isEmpty()) {
            return Reply.success(Message.of(MessageKey.WAR_LIST_EMPTY));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.WAR_LIST_HEADER));
        for (int i = 0; i < wars.size(); i++) {
            War war = wars.get(i);
            lines.add(Message.of(MessageKey.WAR_LIST_LINE, i + 1, nameOf(war.aggressor()), nameOf(war.defender()),
                    strengthOf(war.aggressor()), strengthOf(war.defender()),
                    Message.of(war.ongoing() ? MessageKey.WAR_STATUS_ONGOING : MessageKey.WAR_STATUS_ENDED)));
        }
        return Reply.success(lines);
    }

    /** Ein Spieler dient mit Rang in der Garnison seiner Gemeinde. */
    public synchronized Reply enlist(Caller caller, MilitaryRank rank) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NO_TOWN));
        }
        try {
            registry.enlist(own.get().id(), caller.player(), rank, calendar.today());
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        return Reply.success(Message.of(MessageKey.WAR_ENLISTED, own.get().name().toString(), rankName(rank)));
    }

    public synchronized Reply discharge(Caller caller) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NO_TOWN));
        }
        try {
            registry.discharge(own.get().id(), caller.player(), calendar.today());
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        return Reply.success(Message.of(MessageKey.WAR_DISCHARGED, own.get().name().toString()));
    }

    /** Der Bürgermeister einer Kriegspartei löst das entscheidende Gefecht aus. */
    public synchronized Reply battle(Caller caller, int number) {
        Optional<War> war = partyWar(caller, number);
        if (war.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_PARTY, number));
        }
        PolityView aggressor = queries.polity(war.get().aggressor()).orElseThrow();
        long rate = queries.effectiveLaws(aggressor).valueOf(StandardParameters.WAR_REPARATIONS_PER_STRENGTH_POINT);
        Day today = calendar.today();
        BattleOutcome outcome;
        try {
            outcome = registry.resolveBattle(war.get().id(), rate, today);
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        if (outcome == BattleOutcome.DRAW) {
            return Reply.success(Message.of(MessageKey.WAR_BATTLE_DRAW, number));
        }
        War ended = registry.war(war.get().id()).orElseThrow();
        PeaceTerms terms = ended.outcome().orElseThrow();
        Message paid = payReparations(terms);
        Message line = Message.of(MessageKey.WAR_BATTLE_WON, nameOf(terms.winner()), nameOf(terms.loser()), paid);
        announcements.add(Announcement.toAll(line));
        return Reply.success(line);
    }

    /** Der Bürgermeister einer Kriegspartei beendet den Krieg ohne Sieger. */
    public synchronized Reply peace(Caller caller, int number) {
        Optional<War> war = partyWar(caller, number);
        if (war.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_PARTY, number));
        }
        try {
            registry.negotiatePeace(war.get().id(), calendar.today());
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
        Message line = Message.of(MessageKey.WAR_PEACE, nameOf(war.get().aggressor()), nameOf(war.get().defender()));
        announcements.add(Announcement.toAll(line));
        return Reply.success(line);
    }

    /** Der Bürgermeister kauft seiner Gemeinde für {@code days} Tage Neutralität, bezahlt aus der Gemeindekasse. */
    public synchronized Reply neutrality(Caller caller, int days) {
        Optional<PolityView> own = ownTown(caller);
        if (own.isEmpty() || !isMayor(caller, own.get())) {
            return Reply.failure(Message.of(MessageKey.WAR_NOT_MAYOR));
        }
        if (days < 1) {
            return Reply.failure(Message.of(MessageKey.WAR_NEUTRALITY_DAYS));
        }
        PolityView town = own.get();
        Optional<PolityView> protector = queries.parentOf(town.id());
        if (protector.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.WAR_NEUTRALITY_NO_PROTECTOR));
        }
        Day today = calendar.today();
        long fee = queries.effectiveLaws(town).valueOf(StandardParameters.NEUTRALITY_FEE_PER_DAY) * days;
        try {
            if (!registry.ongoingWarsOf(town.id()).isEmpty()) {
                throw new de.etiennebader.civ.domain.war.AtWarException(town.id());
            }
            if (fee > 0) {
                app.transferFunds().execute(new TransferFunds.Command(town.asActor(), protector.get().asActor(),
                        Money.of(fee, town.currency()), TransferReason.FEE, today));
            }
            registry.purchaseNeutrality(town.id(), today.plus(days), today);
            return Reply.success(Message.of(MessageKey.WAR_NEUTRAL, town.name().toString(), days,
                    texts.money(Money.of(fee, town.currency()))));
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }

    // ------------------------------------------------------------------ Tagesablauf

    /** Täglich: kriegerische Dorfbewohner melden sich, dann wird Sold gezahlt. */
    public synchronized void endOfDay(Day day) {
        for (War war : registry.wars()) {
            if (war.ongoing()) {
                villagersEnlist(war.aggressor(), day);
                villagersEnlist(war.defender(), day);
            }
        }
        payWages(day);
    }

    private void villagersEnlist(PolityId townId, Day day) {
        Optional<PolityView> town = queries.polity(townId);
        if (town.isEmpty()) {
            return;
        }
        Map<ActorRef, MilitaryRank> garrison = registry.garrisons().getOrDefault(townId, Map.of());
        for (Membership membership : town.get().members()) {
            if (membership.type() != MembershipType.CITIZEN || !(membership.member() instanceof PersonRef person)
                    || garrison.containsKey(person)) {
                continue;
            }
            int peace = Personality.of(person).peace();
            if (peace <= VILLAGER_OFFICER_PEACE) {
                registry.enlist(townId, person, MilitaryRank.OFFICER, day);
            } else if (peace <= VILLAGER_SOLDIER_PEACE) {
                registry.enlist(townId, person, MilitaryRank.SOLDIER, day);
            }
        }
    }

    private void payWages(Day day) {
        for (Map.Entry<PolityId, Map<ActorRef, MilitaryRank>> entry : registry.garrisons().entrySet()) {
            Optional<PolityView> town = queries.polity(entry.getKey());
            if (town.isEmpty()) {
                continue;
            }
            long soldier = queries.effectiveLaws(town.get()).valueOf(StandardParameters.SOLDIER_WAGE);
            long officer = queries.effectiveLaws(town.get()).valueOf(StandardParameters.OFFICER_WAGE);
            for (Map.Entry<ActorRef, MilitaryRank> member : entry.getValue().entrySet()) {
                long wage = member.getValue() == MilitaryRank.OFFICER ? officer : soldier;
                if (wage == 0) {
                    continue;
                }
                try {
                    app.transferFunds().execute(new TransferFunds.Command(town.get().asActor(), member.getKey(),
                            Money.of(wage, town.get().currency()), TransferReason.WAGE, day));
                } catch (DomainException e) {
                    registry.discharge(entry.getKey(), member.getKey(), day);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private Message payReparations(PeaceTerms terms) {
        PolityView winner = queries.polity(terms.winner()).orElseThrow();
        PolityView loser = queries.polity(terms.loser()).orElseThrow();
        if (terms.reparations() == 0) {
            return Message.of(MessageKey.WAR_NO_REPARATIONS);
        }
        Money amount = Money.of(terms.reparations(), loser.currency());
        try {
            app.transferFunds().execute(new TransferFunds.Command(loser.asActor(), winner.asActor(), amount,
                    TransferReason.WAR_REPARATIONS, calendar.today()));
            return texts.money(amount);
        } catch (DomainException e) {
            return Message.of(MessageKey.WAR_REPARATIONS_UNPAID, texts.money(amount));
        }
    }

    private Optional<War> partyWar(Caller caller, int number) {
        Optional<PolityView> own = ownTown(caller);
        List<War> wars = registry.wars();
        if (own.isEmpty() || !isMayor(caller, own.get()) || number < 1 || number > wars.size()) {
            return Optional.empty();
        }
        return Optional.of(wars.get(number - 1)).filter(war -> war.involves(own.get().id()));
    }

    private Optional<PolityView> ownTown(Caller caller) {
        return queries.municipalityOf(caller.player());
    }

    private static boolean isMayor(Caller caller, PolityView town) {
        return town.holds(caller.player(), Office.MAYOR);
    }

    private String nameOf(PolityId id) {
        return queries.polity(id).map(polity -> polity.name().toString()).orElse("?");
    }

    private long strengthOf(PolityId id) {
        Map<ActorRef, MilitaryRank> garrison = registry.garrisons().getOrDefault(id, Map.of());
        long soldiers = garrison.values().stream().filter(rank -> rank == MilitaryRank.SOLDIER).count();
        return soldiers + garrison.size() - soldiers;
    }

    private static Message rankName(MilitaryRank rank) {
        return Message.of(rank == MilitaryRank.OFFICER ? MessageKey.WAR_RANK_OFFICER : MessageKey.WAR_RANK_SOLDIER);
    }
}
