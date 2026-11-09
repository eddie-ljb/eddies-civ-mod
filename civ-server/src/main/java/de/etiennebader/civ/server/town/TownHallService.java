package de.etiennebader.civ.server.town;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.polity.DissolvePolity;
import de.etiennebader.civ.application.polity.MoveSeat;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.util.Objects;
import java.util.Optional;

/**
 * Gemeindehaus verlegen und Gemeinde auflösen. Beides darf nur der Bürgermeister, Auflösen auch ein Admin.
 * Das Ergebnis nennt den bisherigen Sitz, damit {@code civ-fabric} den alten Block entfernen kann.
 */
public final class TownHallService {

    /** Antwort und, bei Erfolg, der Ort des alten Gemeindehauses. */
    public record SeatChange(Reply reply, Optional<BlockPosition> oldSeat) {

        public SeatChange {
            Objects.requireNonNull(reply, "reply");
            Objects.requireNonNull(oldSeat, "oldSeat");
        }

        static SeatChange failed(Reply reply) {
            return new SeatChange(reply, Optional.empty());
        }
    }

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final Texts texts;
    private final Announcements announcements;

    public TownHallService(CivApplication app, Calendar calendar, Texts texts, Announcements announcements) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
    }

    /** Ob der Spieler Bürgermeister einer Gemeinde ist; dann verlegt ein neues Gemeindehaus deren Sitz. */
    public Optional<PolityView> townOfMayor(ActorRef player) {
        return queries.municipalityOf(player).filter(town -> TownRoles.isMayor(town, player));
    }

    /** Verlegt den Sitz der Gemeinde des Bürgermeisters an die Stelle eines neu gesetzten Gemeindehauses. */
    public SeatChange moveSeat(Caller caller, BlockPosition newSeat) {
        Optional<PolityView> town = townOfMayor(caller.player());
        if (town.isEmpty()) {
            return SeatChange.failed(Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)));
        }
        Optional<BlockPosition> oldSeat = town.get().seat();
        try {
            app.moveSeat().execute(new MoveSeat.Command(town.get().id(), caller.player(), newSeat, calendar.today()));
        } catch (DomainException e) {
            return SeatChange.failed(Reply.failure(texts.error(e)));
        }
        return new SeatChange(Reply.success(Message.of(MessageKey.SEAT_MOVED, town.get().name().value(),
                newSeat.x(), newSeat.y(), newSeat.z())), oldSeat);
    }

    /** Der Bürgermeister löst seine Gemeinde auf; die Kasse geht an ihn. */
    public SeatChange dissolve(Caller caller) {
        Optional<PolityView> member = queries.municipalityOf(caller.player());
        if (member.isEmpty()) {
            return SeatChange.failed(Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)));
        }
        if (!TownRoles.isMayor(member.get(), caller.player())) {
            return SeatChange.failed(Reply.failure(Message.of(MessageKey.ONLY_MAYOR, member.get().name().value())));
        }
        return dissolve(member.get(), caller.player());
    }

    /** Ein Admin löst eine Gemeinde auf; die Kasse geht an ihren Bürgermeister bzw. ihr erstes Mitglied. */
    public SeatChange dissolveByAdmin(String townName) {
        Optional<PolityView> town = queries.polityNamed(townName);
        if (town.isEmpty()) {
            return SeatChange.failed(Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, townName.strip())));
        }
        ActorRef heir = TownRoles.mayorOf(town.get())
                .or(() -> town.get().members().stream().map(Membership::member).findFirst())
                .orElseThrow();
        return dissolve(town.get(), heir);
    }

    private SeatChange dissolve(PolityView town, ActorRef heir) {
        Optional<BlockPosition> seat = town.seat();
        String name = town.name().value();
        try {
            Money treasury = app.dissolvePolity().execute(new DissolvePolity.Command(town.id(), heir, calendar.today()));
            announcements.add(Announcement.toAll(Message.of(MessageKey.TOWN_DISSOLVED, name)));
            return new SeatChange(Reply.success(Message.of(MessageKey.DISSOLVED, name, texts.money(treasury),
                    texts.actor(heir))), seat);
        } catch (DomainException e) {
            return SeatChange.failed(Reply.failure(texts.error(e)));
        }
    }
}
