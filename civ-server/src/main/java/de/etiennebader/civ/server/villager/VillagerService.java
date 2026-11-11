package de.etiennebader.civ.server.villager;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.economy.CreditTownIncome;
import de.etiennebader.civ.application.polity.ChangeTier;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.application.population.Emigration;
import de.etiennebader.civ.application.population.Satisfaction;
import de.etiennebader.civ.application.population.TownMood;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.population.Trait;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.command.TranslationKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Dorfbewohner werden Einwohner der Gemeinde, in deren Gebiet sie sich aufhalten.
 *
 * <ul>
 *     <li>Betritt ein Dorfbewohner das Gebiet einer anderen Gemeinde, zieht er dorthin um. In der Wildnis bleibt
 *         er Einwohner seiner bisherigen Gemeinde, damit kurze Ausflüge nichts ändern.</li>
 *     <li>Stirbt er, verlässt er die Gemeinde.</li>
 *     <li>Am Ende jedes Tages erwirtschaftet jeder Dorfbewohner Geld für die Gemeindekasse.</li>
 *     <li>Die Siedlungsstufe richtet sich nach der Zahl aller Einwohner.</li>
 * </ul>
 */
public final class VillagerService {

    private final CivApplication app;
    private final WorldQueries queries;
    private final ServerSettings settings;
    private final Texts texts;
    private final Announcements announcements;
    /** Wer abgewandert ist, aber im Spiel noch steht; das Spiel bringt ihn weg ({@link #takeDeparture}). */
    private final Map<PersonRef, PendingDeparture> departures = new HashMap<>();

    /**
     * Ein abgewanderter Dorfbewohner, den das Spiel noch fortbringen muss.
     *
     * @param destination Gemeindehaus der neuen Gemeinde; leer, wenn er in die Wildnis zieht
     */
    public record PendingDeparture(PersonRef villager, Optional<BlockPosition> destination) {

        public PendingDeparture {
            Objects.requireNonNull(villager, "villager");
            Objects.requireNonNull(destination, "destination");
        }
    }

    public VillagerService(CivApplication app, ServerSettings settings, Texts texts, Announcements announcements) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.settings = Objects.requireNonNull(settings, "settings");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
    }

    /**
     * Ein Dorfbewohner wurde an dieser Stelle gesehen.
     *
     * @return ob er dabei in eine Gemeinde gezogen ist
     */
    public boolean seen(PersonRef villager, BlockPosition position, Day day) {
        Objects.requireNonNull(villager, "villager");
        if (departures.containsKey(villager)) {
            return false;
        }
        Optional<PolityId> here = queries.territory().sovereignOf(position.chunk())
                .filter(id -> queries.polity(id).map(polity -> polity.kind() == PolityKind.TERRITORIAL).orElse(false));
        if (here.isEmpty()) {
            return false;
        }
        Optional<PolityView> current = queries.municipalityOf(villager);
        if (current.isPresent() && current.get().id().equals(here.get())) {
            return false;
        }
        try {
            current.ifPresent(old -> app.leavePolity().execute(new LeavePolity.Command(old.id(), villager, day)));
            app.joinPolity().execute(new JoinPolity.Command(here.get(), villager, MembershipType.RESIDENT, day));
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    /** Ein Dorfbewohner ist gestorben oder verschwunden. */
    public void gone(PersonRef villager, Day day) {
        departures.remove(villager);
        queries.municipalityOf(villager).ifPresent(town -> {
            try {
                app.leavePolity().execute(new LeavePolity.Command(town.id(), villager, day));
            } catch (DomainException ignored) {
                // Der letzte Einwohner bleibt; eine Gemeinde ohne Mitglieder darf es nicht geben.
            }
        });
    }

    /** Dorfbewohner einer Gemeinde. */
    public int villagersOf(PolityView town) {
        return (int) town.members().stream().filter(membership -> membership.member() instanceof PersonRef).count();
    }

    /**
     * Was die Dorfbewohner der Gemeinde täglich erwirtschaften: je Dorfbewohner der eingestellte Betrag, zufriedene
     * bis 50 % mehr, unzufriedene bis 50 % weniger ({@link TownMood#productivity()}).
     */
    public Money dailyIncome(PolityView town) {
        long units = settings.villagerIncome().amount() * TownMood.of(town).productivity() / 100;
        return Money.of(units, town.currency());
    }

    /** Die Stimmung der Dorfbewohner einer Gemeinde. */
    public TownMood moodOf(PolityView town) {
        return TownMood.of(town);
    }

    /** Zufriedenheit eines Dorfbewohners mit seiner Gemeinde; leer, wenn er zu keiner gehört. */
    public Optional<Satisfaction> satisfactionOf(PersonRef villager) {
        return queries.municipalityOf(villager)
                .map(town -> Satisfaction.of(Personality.of(villager), town));
    }

    /** Auskunft über einen Dorfbewohner, z. B. beim Anschauen mit Schleichen und Rechtsklick. */
    public Reply describe(PersonRef villager) {
        Personality personality = Personality.of(villager);
        Message trait = Message.of(TranslationKeys.trait(personality.dominant()));
        Optional<PolityView> town = queries.municipalityOf(villager);
        if (town.isEmpty()) {
            return Reply.success(Message.of(MessageKey.VILLAGER_HOMELESS, trait, personality.order(),
                    personality.thrift(), personality.belonging()));
        }
        Satisfaction satisfaction = Satisfaction.of(personality, town.get());
        return Reply.success(
                Message.of(MessageKey.VILLAGER_INFO, town.get().name().value(), trait, personality.order(),
                        personality.thrift(), personality.belonging()),
                Message.of(MessageKey.VILLAGER_MOOD, moodWord(satisfaction.score()), satisfaction.score(),
                        signed(satisfaction.points(Satisfaction.Factor.ORDER)),
                        signed(satisfaction.points(Satisfaction.Factor.TAXES)),
                        signed(satisfaction.points(Satisfaction.Factor.VOICE)),
                        signed(satisfaction.points(Satisfaction.Factor.PROSPERITY)),
                        satisfaction.productivityPercent()));
    }

    /** {@code /civ stimmung}: Stimmung der Dorfbewohner der eigenen Gemeinde. */
    public Reply mood(Caller caller) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        TownMood mood = TownMood.of(town.get());
        if (mood.villagers() == 0) {
            return Reply.success(Message.of(MessageKey.MOOD_NONE, town.get().name().value()));
        }
        return Reply.success(
                Message.of(MessageKey.MOOD_HEADER, town.get().name().value(), moodWord(mood.averageScore()),
                        mood.averageScore(), mood.content(), mood.villagers() - mood.content() - mood.discontent(),
                        mood.discontent()),
                Message.of(MessageKey.MOOD_FACTORS, signed(mood.averageFactors().get(Satisfaction.Factor.BASE)),
                        signed(mood.averageFactors().get(Satisfaction.Factor.ORDER)),
                        signed(mood.averageFactors().get(Satisfaction.Factor.TAXES)),
                        signed(mood.averageFactors().get(Satisfaction.Factor.VOICE)),
                        signed(mood.averageFactors().get(Satisfaction.Factor.PROSPERITY))),
                Message.of(MessageKey.MOOD_TRAITS, Arrays.stream(Trait.values())
                        .map(trait -> (Object) mood.traits().getOrDefault(trait, 0)).toArray()),
                Message.of(MessageKey.MOOD_INCOME, mood.productivity() / Math.max(1, mood.villagers()),
                        texts.money(dailyIncome(town.get()))),
                mood.atRisk() > 0 && settings.emigration()
                        ? Message.of(MessageKey.MOOD_AT_RISK, mood.atRisk())
                        : Message.of(MessageKey.MOOD_NO_RISK));
    }

    /** „zufrieden“, „gleichgültig“ oder „unzufrieden“. */
    public static Message moodWord(int score) {
        if (score >= Satisfaction.CONTENT) {
            return Message.of(MessageKey.MOOD_CONTENT);
        }
        return Message.of(score < -Satisfaction.CONTENT ? MessageKey.MOOD_DISCONTENT : MessageKey.MOOD_NEUTRAL);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    /**
     * Tagesabschluss: Einnahmen gutschreiben und Siedlungsstufen anpassen.
     *
     * @return Ankündigungen für alle Spieler, z. B. Aufstiege; sie gehen auch an {@link Announcements}
     */
    public List<Message> endOfDay(Day day) {
        List<Message> changes = new ArrayList<>();
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            Money income = dailyIncome(town);
            if (income.isPositive() && income.currency().equals(settings.villagerIncome().currency())) {
                app.creditTownIncome().execute(new CreditTownIncome.Command(town.id(), income, day));
            }
            SettlementTier before = town.tier();
            SettlementTier tier = settings.tiers().tierFor(town.memberCount());
            if (app.changeTier().execute(new ChangeTier.Command(town.id(), tier, day))) {
                MessageKey key = tier.compareTo(before) > 0 ? MessageKey.TOWN_PROMOTED : MessageKey.TOWN_DEMOTED;
                Message message = Message.of(key, town.name().value(), texts.tier(tier));
                changes.add(message);
                announcements.add(Announcement.toAll(message));
            }
        }
        if (settings.emigration()) {
            emigrate(day);
        }
        return changes;
    }

    /** Ob noch Abgewanderte im Spiel stehen. */
    public boolean hasDepartures() {
        return !departures.isEmpty();
    }

    /** Holt einen Abgewanderten ab, damit das Spiel ihn fortbringt; danach gilt er als fort. */
    public Optional<PendingDeparture> takeDeparture(PersonRef villager) {
        return Optional.ofNullable(departures.remove(villager));
    }

    private void emigrate(Day day) {
        Map<PolityId, Integer> toWild = new HashMap<>();
        Map<PolityId, Map<PolityId, Integer>> moved = new HashMap<>();
        for (Emigration.Departure departure : Emigration.plan(queries.polities(), day)) {
            try {
                app.leavePolity().execute(new LeavePolity.Command(departure.from(), departure.villager(), day));
                Optional<BlockPosition> seat = Optional.empty();
                if (departure.to().isPresent()) {
                    app.joinPolity().execute(new JoinPolity.Command(departure.to().get(), departure.villager(),
                            MembershipType.RESIDENT, day));
                    seat = queries.polity(departure.to().get()).flatMap(PolityView::seat);
                    moved.computeIfAbsent(departure.from(), ignored -> new HashMap<>())
                            .merge(departure.to().get(), 1, Integer::sum);
                } else {
                    toWild.merge(departure.from(), 1, Integer::sum);
                }
                departures.put(departure.villager(), new PendingDeparture(departure.villager(), seat));
            } catch (DomainException ignored) {
                // z. B. letzter Einwohner: dann bleibt er eben
            }
        }
        toWild.forEach((town, count) -> announcements.add(Announcement.toTown(town,
                Message.of(MessageKey.VILLAGERS_LEFT, count, texts.polityName(town)))));
        moved.forEach((from, targets) -> targets.forEach((to, count) -> {
            announcements.add(Announcement.toTown(from,
                    Message.of(MessageKey.VILLAGERS_MOVED_AWAY, count, texts.polityName(from), texts.polityName(to))));
            announcements.add(Announcement.toTown(to,
                    Message.of(MessageKey.VILLAGERS_MOVED_IN, count, texts.polityName(from), texts.polityName(to))));
        }));
    }
}
