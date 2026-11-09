package de.etiennebader.civ.server.villager;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.economy.CreditTownIncome;
import de.etiennebader.civ.application.polity.ChangeTier;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.LeavePolity;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.ServerSettings;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Texts;

import java.util.ArrayList;
import java.util.List;
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

    /** Was die Dorfbewohner der Gemeinde täglich erwirtschaften. */
    public Money dailyIncome(PolityView town) {
        return Money.of(settings.villagerIncome().amount() * villagersOf(town), town.currency());
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
        return changes;
    }
}
