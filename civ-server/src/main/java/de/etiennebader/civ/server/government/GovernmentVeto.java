package de.etiennebader.civ.server.government;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.support.MotionGate;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.government.Government;
import de.etiennebader.civ.domain.government.GovernmentException;
import de.etiennebader.civ.domain.government.PendingLaw;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Veto-Recht des Oberhaupts: Beschlossene Gesetze warten die Vetofrist ab. Das Oberhaupt kann unterzeichnen (gilt
 * sofort) oder sein Veto einlegen (verfällt); schweigt es, gilt das Gesetz nach Fristablauf. Eigene Anträge des
 * Oberhaupts und Parteiprogramme sind ausgenommen. Ein Dorfbewohner als Oberhaupt legt Veto ein, wenn der
 * Antragsteller einer weit entfernten Partei angehört.
 */
public final class GovernmentVeto implements MotionGate {

    /** Ab diesem Abstand der Parteirichtungen legt ein Dorfbewohner als Oberhaupt Veto ein. */
    static final int VILLAGER_VETO_DISTANCE = 60;

    private final GovernmentService core;
    private final CivApplication app;
    private final WorldQueries queries;
    private final Announcements announcements;
    private final boolean villagerPolitics;

    GovernmentVeto(GovernmentService core, CivApplication app, Announcements announcements,
                   boolean villagerPolitics) {
        this.core = core;
        this.app = app;
        this.queries = app.queries();
        this.announcements = announcements;
        this.villagerPolitics = villagerPolitics;
    }

    @Override
    public boolean hold(Polity polity, Proposal proposal, Day day) {
        synchronized (core) {
            Optional<Government> found = core.registry().find(polity.id());
            if (found.isEmpty()) {
                return false;
            }
            Government g = found.get();
            Motion motion = proposal.motion();
            Optional<ActorRef> head = polity.mayor(villagerPolitics);
            if (!g.configured() || !g.head().veto() || g.vetoDays() <= 0 || head.isEmpty()
                    || head.get().equals(proposal.proposer()) || motion instanceof Motion.PartyProgram) {
                return false;
            }
            PendingLaw law = toPending(motion, proposal.proposer(), day, day.plus(g.vetoDays()));
            g.hold(law);
            core.store();
            announcements.add(Announcement.toTown(polity.id(), Message.of(MessageKey.GOV_VETO_PENDING, law.title(),
                    law.deadline().value())));
            return true;
        }
    }

    /** Wartende Gesetze, nummeriert. */
    public synchronized Reply list(Caller caller) {
        Optional<PolityView> town = core.townOf(caller);
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.GOV_NO_TOWN));
        }
        List<PendingLaw> pending = core.registry().of(town.get().id()).pendingLaws();
        if (pending.isEmpty()) {
            return Reply.success(Message.of(MessageKey.GOV_NO_PENDING));
        }
        List<Message> lines = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            PendingLaw law = pending.get(i);
            lines.add(Message.of(MessageKey.GOV_PENDING_LINE, i + 1, law.title(), core.nameOf(law.proposer()),
                    law.deadline().value()));
        }
        return Reply.success(lines);
    }

    /** Das Oberhaupt unterzeichnet: das Gesetz gilt sofort. */
    public synchronized Reply sign(Caller caller, int number) {
        return core.as(caller, (town, g) -> town.holds(caller.player(), de.etiennebader.civ.domain.polity.Office.MAYOR),
                (town, g) -> {
                    PendingLaw law = g.takePending(number);
                    enact(town.id(), law);
                    Message line = Message.of(MessageKey.GOV_SIGNED, law.title());
                    announcements.add(Announcement.toTown(town.id(), line));
                    return line;
                });
    }

    /** Das Oberhaupt legt Veto ein: das Gesetz verfällt. */
    public synchronized Reply veto(Caller caller, int number) {
        return core.as(caller, (town, g) -> town.holds(caller.player(), de.etiennebader.civ.domain.polity.Office.MAYOR),
                (town, g) -> {
                    if (!g.head().veto()) {
                        throw new GovernmentException(ErrorCode.NO_VETO_POWER);
                    }
                    PendingLaw law = g.takePending(number);
                    Message line = Message.of(MessageKey.GOV_VETOED, law.title());
                    announcements.add(Announcement.toTown(town.id(), line));
                    return line;
                });
    }

    /** Länge der Vetofrist in Tagen; 0 schaltet die Wartezeit ab. */
    public synchronized Reply setVetoDays(Caller caller, int days) {
        return core.asHead(caller, (town, g) -> {
            g.configureVetoDays(days);
            return Message.of(MessageKey.GOV_VETO_DAYS, g.vetoDays());
        });
    }

    /** Tagesende: Dorfbewohner als Oberhaupt entscheiden, abgelaufene Fristen setzen Gesetze in Kraft. */
    void endOfDay(PolityView town, Government g, Day day) {
        Optional<ActorRef> head = town.mayor(villagerPolitics);
        List<PendingLaw> pending = g.pendingLaws();
        for (int i = pending.size(); i >= 1; i--) {
            PendingLaw law = pending.get(i - 1);
            boolean villagerVeto = head.filter(h -> h instanceof PersonRef).isPresent() && g.head().veto()
                    && distant(head.get(), law.proposer());
            boolean expired = !day.isBefore(law.deadline());
            if (villagerVeto) {
                g.takePending(i);
                announcements.add(Announcement.toTown(town.id(), Message.of(MessageKey.GOV_VETOED, law.title())));
            } else if (expired || head.filter(h -> h instanceof PersonRef).isPresent()) {
                g.takePending(i);
                enact(town.id(), law);
                announcements.add(Announcement.toTown(town.id(), Message.of(MessageKey.GOV_SIGNED, law.title())));
            }
        }
    }

    private boolean distant(ActorRef head, ActorRef proposer) {
        Optional<PartyProfile> a = queries.partyOf(head).flatMap(PolityView::partyProfile);
        Optional<PartyProfile> b = queries.partyOf(proposer).flatMap(PolityView::partyProfile);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        int distance = Math.max(Math.abs(a.get().order() - b.get().order()),
                Math.abs(a.get().economy() - b.get().economy()));
        return distance > VILLAGER_VETO_DISTANCE;
    }

    private void enact(de.etiennebader.civ.domain.shared.PolityId polity, PendingLaw law) {
        try {
            app.carryOutMotion().execute(polity, toMotion(law), core.today());
        } catch (DomainException | IllegalArgumentException e) {
            announcements.add(Announcement.toTown(polity, Message.of(MessageKey.GOV_AGREEMENT_FAILED, law.title(),
                    e.getMessage() == null ? "?" : e.getMessage())));
        }
    }

    // ------------------------------------------------------------------ Umwandlung

    static PendingLaw toPending(Motion motion, ActorRef proposer, Day accepted, Day deadline) {
        return switch (motion) {
            case Motion.Change change -> new PendingLaw("CHANGE", change.change().key().id(), "",
                    encode(change.values()), "", proposer, accepted, deadline);
            case Motion.Enact enact -> new PendingLaw("ENACT", enact.title(), enact.text(), encode(enact.values()), "",
                    proposer, accepted, deadline);
            case Motion.Repeal repeal -> new PendingLaw("REPEAL", repeal.title(), "", List.of(),
                    repeal.statute().value().toString(), proposer, accepted, deadline);
            case Motion.PartyProgram program -> throw new IllegalArgumentException("Parteiprogramme sind ausgenommen");
        };
    }

    private static List<String> encode(List<ParameterValue<?>> values) {
        return values.stream().map(v -> v.key().id() + "=" + ParameterCatalog.encode(v)).toList();
    }

    static Motion toMotion(PendingLaw law) {
        ParameterCatalog catalog = ParameterCatalog.standard();
        List<ParameterValue<?>> values = new ArrayList<>();
        for (String entry : law.values()) {
            int eq = entry.indexOf('=');
            values.add(catalog.decode(entry.substring(0, eq), entry.substring(eq + 1)));
        }
        return switch (law.kind()) {
            case "CHANGE" -> new Motion.Change(values.get(0));
            case "ENACT" -> new Motion.Enact(law.title(), law.text(), values);
            default -> new Motion.Repeal(new StatuteId(UUID.fromString(law.statute())), law.title());
        };
    }
}
