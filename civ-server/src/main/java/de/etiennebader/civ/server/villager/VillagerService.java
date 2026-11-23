package de.etiennebader.civ.server.villager;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.economy.CreditTownIncome;
import de.etiennebader.civ.application.party.LeaveParty;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.ChangeTier;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.application.population.Emigration;
import de.etiennebader.civ.application.population.Satisfaction;
import de.etiennebader.civ.application.population.SocialEffects;
import de.etiennebader.civ.application.population.TownMood;
import de.etiennebader.civ.application.population.VillagerDialogue;
import de.etiennebader.civ.application.population.VillagerMailbox;
import de.etiennebader.civ.application.population.VillagerLoyalty;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.ClaimWeighting;
import de.etiennebader.civ.application.territory.ClaimChunk;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.population.VillagerIdentity;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.contract.ContractKind;
import de.etiennebader.civ.domain.justice.CaseKind;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Motion;
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
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.TerritoryView;
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
import java.util.Set;
import java.util.function.ToIntFunction;

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
    private final Calendar calendar;
    private final ServerSettings settings;
    private final Texts texts;
    private final Announcements announcements;
    private final de.etiennebader.civ.server.conjuncture.ConjunctureService conjuncture;

    /** Wer abgewandert ist, aber im Spiel noch steht; das Spiel bringt ihn weg ({@link #takeDeparture}). */
    private final Map<PersonRef, PendingDeparture> departures = new HashMap<>();
    private final Map<PersonRef, BlockPosition> lastSeen = new HashMap<>();
    /** Politische Beeinflussung und Dorfbewohner-Kontakte (Issue #18); wie {@link #departures} nur im Speicher. */
    private final SocialEffects social = new SocialEffects();
    /** Kinder, die heute (seit dem letzten Tagesabschluss) in einer Schule gesehen wurden (Issue #21). */
    private final java.util.Set<PersonRef> attendedToday = new java.util.HashSet<>();
    /** Kinder, die seit dem letzten Tagesabschluss überhaupt gesehen wurden. */
    private final java.util.Set<PersonRef> childrenToday = new java.util.HashSet<>();
    /** Post zwischen Spielern und Dorfbewohnern bzw. unter Dorfbewohnern (Issue #18); wie {@link #social} nur im Speicher. */
    private final VillagerMailbox mailbox = new VillagerMailbox();
    /** Wie oft je 100 Chancen pro Tag ein sehr unzufriedener, freiheitsliebender Dorfbewohner auffällt. */
    private static final int CRIME_MAX_CHANCE_PERCENT = 5;
    /**
     * Dorfbewohner sparen zusätzlich ein Zehntel ihres Anteils am Gemeindeeinkommen, bis 50 Smaragde; davon kaufen
     * sparsame Aktien.
     */
    private static final int SAVINGS_DIVISOR = 10;
    private static final long SAVINGS_CAP = 5_000;

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

    public VillagerService(CivApplication app, ServerSettings settings, Texts texts, Announcements announcements,
                           de.etiennebader.civ.server.conjuncture.ConjunctureService conjuncture, Calendar calendar) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.conjuncture = Objects.requireNonNull(conjuncture, "conjuncture");
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
        lastSeen.put(villager, position);
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

    /**
     * Ein junger Dorfbewohner wurde an dieser Stelle gesehen (Issue #21): hält er sich auf einem Schulgrundstück
     * auf, gilt er heute als zur Schule gegangen. Die Werte ändern sich erst im Tagesabschluss, genau einmal je Tag.
     */
    public void childSeen(PersonRef child, BlockPosition position) {
        Objects.requireNonNull(child, "child");
        childrenToday.add(child);
        boolean inSchool = queries.territory().sovereignOf(position.chunk())
                .flatMap(id -> queries.land(id).plotAt(position.dimension(), position.x(), position.z()))
                .filter(plot -> plot.type() == de.etiennebader.civ.domain.land.PlotType.EDUCATION).isPresent();
        if (inSchool) {
            attendedToday.add(child);
        }
    }

    /** Ein Dorfbewohner ist erwachsen: Bildung und Intelligenz frieren ein. */
    public void grownUp(PersonRef villager) {
        app.villagerEducation().grownUp(villager);
    }

    private void educationDay() {
        for (PersonRef child : childrenToday) {
            app.villagerEducation().recordChildDay(child, attendedToday.contains(child));
        }
        childrenToday.clear();
        attendedToday.clear();
    }

    /** Ein Dorfbewohner ist gestorben oder verschwunden. */
    public void gone(PersonRef villager, Day day) {
        departures.remove(villager);
        lastSeen.remove(villager);
        childrenToday.remove(villager);
        attendedToday.remove(villager);
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
        return dailyIncome(town, calendar.today());
    }

    /** Wie {@link #dailyIncome(PolityView)}, aber für einen bestimmten Tag statt {@code calendar.today()}. */
    public Money dailyIncome(PolityView town, Day day) {
        long units = settings.villagerIncome().amount() * TownMood.of(town, queries.effectiveLaws(town), media(town)).productivity() / 100;
        units = Math.round(units * conjuncture.multiplierOf(town, day));
        return Money.of(units, town.currency());
    }

    /** {@code /civ konjunktur}: Konjunkturphase der Wirtschaftszone der eigenen Gemeinde. */
    public Reply conjunctureReply(Caller caller) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        var view = conjuncture.describe(town.get(), calendar.today());
        return Reply.success(Message.of(MessageKey.CONJUNCTURE_INFO, view.zoneName(),
                TranslationKeys.conjuncturePhase(view.phase()), Math.round(view.multiplier() * 100)));
    }

    /** Die Stimmung der Dorfbewohner einer Gemeinde. */
    public TownMood moodOf(PolityView town) {
        return TownMood.of(town, queries.effectiveLaws(town), media(town));
    }

    /** Zufriedenheit eines Dorfbewohners mit seiner Gemeinde; leer, wenn er zu keiner gehört. */
    public Optional<Satisfaction> satisfactionOf(PersonRef villager) {
        return queries.municipalityOf(villager)
                .map(town -> Satisfaction.of(Personality.of(villager), town, queries.effectiveLaws(town))
                        .withMedia(app.pressInfluence().satisfactionPoints(villager, town))
                        .withSocial(social.pointsOf(villager)));
    }

    /** Auskunft über einen Dorfbewohner, z. B. beim Anschauen mit Schleichen und Rechtsklick. */
    public Reply describe(PersonRef villager) {
        Personality personality = Personality.of(villager);
        Message trait = Message.of(TranslationKeys.trait(personality.dominant()));
        Optional<PolityView> town = queries.municipalityOf(villager);
        Message identity = identityLine(VillagerIdentity.of(villager));
        if (town.isEmpty()) {
            return Reply.success(Message.of(MessageKey.VILLAGER_HOMELESS, trait, personality.order(),
                    personality.thrift(), personality.belonging()), identity);
        }
        Satisfaction satisfaction = Satisfaction.of(personality, town.get(), queries.effectiveLaws(town.get()))
                .withMedia(app.pressInfluence().satisfactionPoints(villager, town.get()))
                .withSocial(social.pointsOf(villager));
        List<Message> lines = new ArrayList<>(List.of(
                Message.of(MessageKey.VILLAGER_INFO, town.get().name().value(), trait, personality.order(),
                        personality.thrift(), personality.belonging()),
                Message.of(MessageKey.VILLAGER_MOOD, moodWord(satisfaction.score()), satisfaction.score(),
                        signed(satisfaction.points(Satisfaction.Factor.ORDER)),
                        signed(satisfaction.points(Satisfaction.Factor.TAXES)),
                        signed(satisfaction.points(Satisfaction.Factor.VOICE)),
                        signed(satisfaction.points(Satisfaction.Factor.PROSPERITY)),
                        satisfaction.productivityPercent()),
                Message.of(MessageKey.VILLAGER_LOYALTY, signed(VillagerLoyalty.toPolity(personality, town.get(),
                        villager, calendar.today(), queries.effectiveLaws(town.get()))))));
        app.villagerEducation().find(villager).ifPresent(education -> lines.add(Message.of(
                MessageKey.VILLAGER_EDUCATION, education.points(), education.intelligence())));
        queries.partyOf(villager).ifPresent(party -> lines.add(Message.of(MessageKey.VILLAGER_PARTY_LOYALTY,
                signed(VillagerLoyalty.toParty(personality, party, villager, calendar.today())),
                signed(VillagerLoyalty.toChairperson(personality, party, calendar.today())))));
        lastSeen(villager).flatMap(position -> app.addresses().at(position))
                .ifPresent(address -> lines.add(Message.of(MessageKey.VILLAGER_ADDRESS, address.line())));
        queries.contracts().active().stream()
                .filter(contract -> contract.kind() == ContractKind.EMPLOYMENT && contract.counterparty().equals(villager))
                .findFirst()
                .ifPresent(contract -> lines.add(Message.of(MessageKey.VILLAGER_EMPLOYED,
                        texts.actor(contract.offeror()), contract.number())));
        mailbox.latest(villager).ifPresent(mail -> lines.add(Message.of(MessageKey.VILLAGER_MAIL_LATEST,
                texts.actor(mail.from()), mail.sent().value())));
        lines.add(identity);
        return Reply.success(lines);
    }

    /** Einwohnerregister der eigenen Gemeinde, ohne Privatanschriften; erfasst auch ungeladene Dorfbewohner. */
    public Reply residentRegister(Caller caller, int page) {
        Optional<PolityView> ownTown = queries.municipalityOf(caller.player());
        if (ownTown.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        PolityView town = ownTown.orElseThrow();
        List<Message> entries = town.members().stream().map(Membership::member)
                .sorted(java.util.Comparator.comparing(texts::actor, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ActorRef::uuid))
                .map(actor -> actor instanceof PersonRef villager ? identityLine(VillagerIdentity.of(villager))
                        : Message.of(MessageKey.VILLAGER_REGISTER_PLAYER, texts.actor(actor)))
                .toList();
        if (entries.isEmpty()) {
            return Reply.success(Message.of(MessageKey.VILLAGER_REGISTER_EMPTY, town.name().value()));
        }
        int pageSize = 12;
        int pages = 1 + (entries.size() - 1) / pageSize;
        if (page < 1 || page > pages) {
            return Reply.failure(Message.of(MessageKey.VILLAGER_REGISTER_PAGE, pages));
        }
        int start = (page - 1) * pageSize;
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.VILLAGER_REGISTER_HEADER, town.name().value(), page, pages));
        lines.addAll(entries.subList(start, Math.min(start + pageSize, entries.size())));
        return Reply.success(lines);
    }

    private static Message identityLine(VillagerIdentity identity) {
        return Message.of(MessageKey.VILLAGER_IDENTITY, identity.fullName(), identity.registerNumber());
    }

    /** Ein Spieler redet mit einem Dorfbewohner über ein Thema (Issue #18); die Antwort richtet sich nach seiner Stimmung. */
    public Reply talk(PersonRef villager, VillagerDialogue.Topic topic) {
        Satisfaction satisfaction = satisfactionOf(villager)
                .orElseGet(() -> new Satisfaction(Map.of(Satisfaction.Factor.BASE, 0)));
        Message topicWord = Message.of(TranslationKeys.dialogueTopic(topic));
        MessageKey key = switch (VillagerDialogue.moodOf(satisfaction)) {
            case CONTENT -> MessageKey.VILLAGER_TALK_CONTENT;
            case NEUTRAL -> MessageKey.VILLAGER_TALK_NEUTRAL;
            case DISCONTENT -> MessageKey.VILLAGER_TALK_DISCONTENT;
        };
        return Reply.success(Message.of(key, topicWord));
    }

    /**
     * Ein Spieler versucht, politisch auf einen Dorfbewohner einzuwirken; hebt dessen Zufriedenheit ein wenig an,
     * höchstens alle {@link SocialEffects#INFLUENCE_COOLDOWN_DAYS} Tage je Spieler-Dorfbewohner-Paar.
     */
    public Reply influence(Caller caller, PersonRef villager) {
        if (social.influence(caller.player(), villager, calendar.today())) {
            return Reply.success(Message.of(MessageKey.VILLAGER_INFLUENCE_OK));
        }
        return Reply.failure(Message.of(MessageKey.VILLAGER_INFLUENCE_COOLDOWN, SocialEffects.INFLUENCE_COOLDOWN_DAYS));
    }

    /** Ein Spieler schickt einem Dorfbewohner Post (vorformulierter Text, Issue #18). */
    public Reply sendMail(ActorRef from, PersonRef villager, String templateKey) {
        mailbox.send(from, villager, templateKey, calendar.today());
        return Reply.success(Message.of(MessageKey.VILLAGER_MAIL_SENT, texts.actor(villager)));
    }

    /** {@code /civ stimmung}: Stimmung der Dorfbewohner der eigenen Gemeinde. */
    public Reply mood(Caller caller) {
        Optional<PolityView> town = queries.municipalityOf(caller.player());
        if (town.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        TownMood mood = TownMood.of(town.get(), queries.effectiveLaws(town.get()), media(town.get()));
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
                        signed(mood.averageFactors().get(Satisfaction.Factor.PROSPERITY)),
                        signed(mood.averageFactors().getOrDefault(Satisfaction.Factor.MEDIA, 0))),
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
            Money income = dailyIncome(town, day);
            if (income.isPositive() && income.currency().equals(settings.villagerIncome().currency())) {
                creditIncome(town, income, day);
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
        socialAndCrime(day);
        educationDay();
        if (settings.villagerPolitics()) {
            governAutonomously(day);
            autoClaimChunk(day);
        }
        if (settings.ambitions()) {
            app.villagerAmbitions().execute(day);
            app.villagerBusiness().execute(day);
            app.villagerJobSearch().execute(day, villager -> false);
            app.villagerLandlords().execute(day);
        }
        return changes;
    }

    /**
     * Tagesabschluss für {@link #social} (abklingen, Dorfbewohner-Kontakte) und das Durchsickern der
     * {@link #CRIME_MAX_CHANCE_PERCENT} genannten Verdachtsfälle (Issue #18): ein sehr unzufriedener,
     * freiheitsliebender Dorfbewohner fällt gelegentlich mit einer Straftat auf. Das legt bewusst nur eine
     * Ankündigung vor, kein fertiges Gerichtsverfahren - das Anklagen selbst bleibt beim bestehenden
     * Spieler-ausgelösten Weg über {@code /civ gericht}, mit dem Dorfbewohner schon heute als Beschuldigte
     * angegeben werden können.
     */
    private void socialAndCrime(Day day) {
        List<List<PersonRef>> villagersByTown = new ArrayList<>();

        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }

            List<PersonRef> villagers = town.members().stream()
                    .map(Membership::member)
                    .filter(PersonRef.class::isInstance)
                    .map(PersonRef.class::cast)
                    .toList();

            villagersByTown.add(villagers);

            for (PersonRef villager : villagers) {
                rollCrime(town, villager, day);
            }
        }

        social.endOfDay(villagersByTown, day);
    }

    /**
     * Gemeinden ohne Spieler-Bürgermeister regiert kommissarisch der dienstälteste Dorfbewohner-Bürger
     * ({@link PolityView#mayor(boolean)}); er ändert das Recht, so wie es {@link CivApplication#villagerLegislator()}
     * für ihn vorschlägt, genau wie ein Dorfbewohner-Abgeordneter im Parlament einen Antrag stellen würde. Nur eine
     * Änderung je Gemeinde und Tag, damit das Recht nicht täglich hin- und herspringt.
     */
    private void governAutonomously(Day day) {
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }

            if (!(town.mayor(true).orElse(null) instanceof PersonRef villagerMayor)) {
                continue;
            }

            app.villagerLegislator().propose(villagerMayor, town).ifPresent(motion -> {
                if (motion instanceof Motion.Change change) {
                    try {
                        app.amendLaw().execute(
                                new AmendLaw.Command(town.id(), villagerMayor, change.change(), day)
                        );
                    } catch (DomainException e) {
                        // Verfassungsartikel oder tagende Parlamente entscheiden anders;
                        // dann bleibt es beim Status quo.
                    }
                }
            });
        }
    }

    private void rollCrime(PolityView town, PersonRef villager, Day day) {
        Personality personality = Personality.of(villager);
        int score = satisfactionOf(villager).map(Satisfaction::score).orElse(0);

        if (personality.order() >= 0 || score >= 0) {
            return;
        }

        int severity = Math.min(100, (-personality.order() - score) / 2);
        int chancePercent = Math.min(CRIME_MAX_CHANCE_PERCENT, severity / 20);

        if (chancePercent <= 0) {
            return;
        }

        long mix = villager.uuid().getMostSignificantBits()
                ^ Long.rotateLeft(day.value() + 1, 13);

        if (Math.floorMod(mix * 0x9E3779B97F4A7C15L, 100L) >= chancePercent) {
            return;
        }

        announcements.add(Announcement.toTown(
                town.id(),
                Message.of(
                        MessageKey.VILLAGER_CRIME_SUSPECTED,
                        texts.actor(villager),
                        de.etiennebader.civ.server.justice.JusticeService.kindName(CaseKind.CRIMINAL)
                )
        ));
    }

    /**
     * Gemeinden mit kommissarischem Dorfbewohner-Bürgermeister beanspruchen selbstständig je einen freien
     * Nachbar-Chunk ihres Kerngebiets pro Tag, solange die Gemeinde ihr Claim-Limit noch nicht erreicht hat – so,
     * wie es sonst der Bürgermeister oder das Bauamt manuell täte ({@code /civ grundstueck} bzw. die Weltkarte).
     * Lehnt das Recht (z. B. künftige Claim-Kosten) den Claim ab, wird es am nächsten Tag erneut versucht.
     */
    private void autoClaimChunk(Day day) {
        TerritoryView land = queries.territory();
        for (PolityView town : queries.polities()) {
            if (town.kind() != PolityKind.TERRITORIAL) {
                continue;
            }
            if (!(town.mayor(true).orElse(null) instanceof PersonRef)) {
                continue;
            }
            Set<ChunkCoord> owned = land.territoryOf(town.id());
            if (owned.size() >= settings.claimLimit(ClaimWeighting.PERSONS_QUARTER.weightOf(town))) {
                continue;
            }
            owned.stream()
                    .flatMap(chunk -> chunk.neighbours().stream())
                    .filter(candidate -> land.sovereignOf(candidate).isEmpty())
                    .findFirst()
                    .ifPresent(candidate -> {
                        try {
                            app.claimChunk().execute(new ClaimChunk.Command(town.id(), candidate, ClaimType.CORE, day));
                        } catch (DomainException e) {
                            // abgelehnt (z. B. künftige Claim-Kosten unbezahlbar); morgen erneut versuchen.
                        }
                    });
        }
    }

    /**
     * Teilt die Einnahmen auf: Angestellte Dorfbewohner erarbeiten ihren Anteil für den Arbeitgeber, der Rest geht an
     * die Gemeinde.
     */
    private void creditIncome(PolityView town, Money income, Day day) {
        int villagers = villagersOf(town);
        Map<ActorRef, Long> employers = new HashMap<>();
        if (villagers > 0) {
            long share = income.amount() / villagers;
            for (Contract contract : queries.contracts().active()) {
                if (contract.kind() == ContractKind.EMPLOYMENT && contract.counterparty() instanceof PersonRef worker
                        && town.isMember(worker)) {
                    employers.merge(contract.offeror(), share, Long::sum);
                }
            }
        }
        long toEmployers = employers.values().stream().mapToLong(Long::longValue).sum();
        employers.forEach((employer, amount) -> app.creditTownIncome().creditEmployer(employer,
                Money.of(amount, income.currency()), day));
        long rest = income.amount() - toEmployers;
        if (rest > 0) {
            app.creditTownIncome().execute(new CreditTownIncome.Command(town.id(), Money.of(rest, income.currency()),
                    day));
        }
        if (villagers > 0) {
            long savings = income.amount() / villagers / SAVINGS_DIVISOR;
            for (Membership member : town.members()) {
                if (savings > 0 && member.member() instanceof PersonRef villager
                        && queries.ledger().balanceOf(villager, income.currency()).amount() < SAVINGS_CAP) {
                    app.creditTownIncome().creditEmployer(villager, Money.of(savings, income.currency()), day);
                }
            }
        }
    }

    /** Zufriedenheitspunkte aus der Presse für die Dorfbewohner einer Gemeinde. */
    private ToIntFunction<PersonRef> media(PolityView town) {
        return villager -> app.pressInfluence().satisfactionPoints(villager, town);
    }

    /** Wo ein Dorfbewohner zuletzt gesehen wurde; daraus ergibt sich seine Anschrift. */
    public Optional<BlockPosition> lastSeen(PersonRef villager) {
        return Optional.ofNullable(lastSeen.get(villager));
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
        for (Emigration.Departure departure : Emigration.plan(queries.polities(), day, queries::effectiveLaws,
                (villager, town) -> VillagerLoyalty.toPolity(Personality.of(villager), town, villager, day,
                        queries.effectiveLaws(town)))) {
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
                    if (queries.partyOf(departure.villager()).isPresent()) {
                        app.leaveParty().execute(new LeaveParty.Command(departure.villager(), day));
                    }
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
