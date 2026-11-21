package de.etiennebader.civ.application.population;

import de.etiennebader.civ.application.company.CompanyUseCases;
import de.etiennebader.civ.application.company.TakeOverCompany;
import de.etiennebader.civ.application.company.Trading;
import de.etiennebader.civ.application.office.StandForElection;
import de.etiennebader.civ.application.party.FoundParty;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.exchange.Listing;
import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.domain.exchange.StockExchangeView;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Ehrgeizige Dorfbewohner nehmen aktiv am wirtschaftlichen und politischen Leben teil und machen den Spielern
 * Konkurrenz.
 *
 * <ul>
 *   <li><b>Nur wenige:</b> Wer ehrgeizig ist, ergibt sich fest aus der Identität ({@link #drive}); gut jeder Sechste
 *       erreicht {@link #AMBITIOUS_FROM}. Alle anderen bleiben unbeteiligt.</li>
 *   <li><b>Firma gründen:</b> mit einem Teil des eigenen Geldes, solange es weniger Firmen als ein Zehntel der
 *       Dorfbewohner (mindestens eine) gibt.</li>
 *   <li><b>Partei gründen:</b> wer noch in keiner ist, mit einer Richtung nach der eigenen Persönlichkeit;
 *       höchstens eine Partei je 15 Dorfbewohner.</li>
 *   <li><b>Kandidieren:</b> in jeder laufenden Wahl der eigenen Gemeinde und der Bünde darüber (Länder, Nationen),
 *       auch in von Spielern gegründeten. Das Programm passt zur Persönlichkeit; Aggressive (Friedfertigkeit unter 0)
 *       setzen ihre Agenda häufiger durch. So können sie Bürgermeister, Landeschef oder Staatsoberhaupt werden.</li>
 *   <li><b>Firmen übernehmen:</b> Aggressive kaufen Aktien einer fremden Firma auf; wer mehr als die Hälfte hält,
 *       übernimmt die Leitung ({@link TakeOverCompany}).</li>
 * </ul>
 *
 * <p>Der Zufall ist fest je Dorfbewohner, Tag und Tätigkeit: dasselbe Spiel verläuft gleich. Je Tag werden höchstens
 * {@value #MAX_ACTIONS} Handlungen ausgeführt, damit Villager die Welt nicht überrollen.
 */
public final class VillagerAmbitions {

    /** Ab diesem Ehrgeiz (0 bis 100) ist ein Dorfbewohner überhaupt aktiv. */
    public static final int AMBITIOUS_FROM = 85;
    public static final int MAX_ACTIONS = 3;
    /** Tageswahrscheinlichkeit einer Firma- oder Parteigründung bei vollem Ehrgeiz. */
    static final double FOUNDING_CHANCE = 0.05;
    /** Tageswahrscheinlichkeit einer Kandidatur bei vollem Ehrgeiz und ohne Aggressivität. */
    static final double CANDIDACY_CHANCE = 0.5;
    private static final int VILLAGERS_PER_COMPANY = 10;
    private static final int VILLAGERS_PER_PARTY = 15;

    /** Was an einem Tag geschah. */
    public record Report(int companies, int parties, int candidacies, int takeovers) {
    }

    private final Supplier<WorldQueries> queries;
    private final CompanyUseCases companies;
    private final FoundParty foundParty;
    private final StandForElection standForElection;
    private final Trading trading;
    private final TakeOverCompany takeOver;

    public VillagerAmbitions(Supplier<WorldQueries> queries, CompanyUseCases companies, FoundParty foundParty,
                             StandForElection standForElection, Trading trading, TakeOverCompany takeOver) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.companies = Objects.requireNonNull(companies, "companies");
        this.foundParty = Objects.requireNonNull(foundParty, "foundParty");
        this.standForElection = Objects.requireNonNull(standForElection, "standForElection");
        this.trading = Objects.requireNonNull(trading, "trading");
        this.takeOver = Objects.requireNonNull(takeOver, "takeOver");
    }

    /** Ehrgeiz von 0 bis 100, fest aus der Identität. */
    public static int drive(PersonRef villager) {
        return (int) (roll(villager, Day.of(0), 0x51) * 101);
    }

    /** Ob dieser Dorfbewohner am politischen und wirtschaftlichen Leben teilnimmt. */
    public static boolean isAmbitious(PersonRef villager) {
        return drive(villager) >= AMBITIOUS_FROM;
    }

    /** Aggressivität von 0 (friedfertig) bis 1 (stark), aus der Friedfertigkeit der Persönlichkeit. */
    public static double aggression(Personality personality) {
        return (Personality.MAX - personality.peace()) / (2.0 * Personality.MAX);
    }

    /** Das Wahlprogramm, das einer Persönlichkeit am besten entspricht. */
    public static Platform platformFor(Personality personality) {
        Platform best = Platform.NONE;
        double bestScore = 0;
        for (Platform platform : Platform.values()) {
            double score = PlatformAffinity.of(platform, personality);
            if (score > bestScore) {
                bestScore = score;
                best = platform;
            }
        }
        return best;
    }

    /** Die Parteirichtung, die zu einer Persönlichkeit passt: Ordnung wie sie ist, Sparsame wollen niedrige Steuern. */
    public static PartyProfile profileFor(Personality personality) {
        int economy = Math.max(-PartyProfile.MAX, Math.min(PartyProfile.MAX, -personality.thrift()));
        return new PartyProfile(personality.order(), economy, PartyProfile.DEFAULT_EMBLEM);
    }

    public Report execute(Day day) {
        Objects.requireNonNull(day, "day");
        WorldQueries world = queries.get();
        Set<PersonRef> all = new LinkedHashSet<>();
        for (PolityView polity : world.polities()) {
            if (polity.kind() == PolityKind.TERRITORIAL) {
                polity.members().stream().map(Membership::member).filter(PersonRef.class::isInstance)
                        .map(PersonRef.class::cast).forEach(all::add);
            }
        }
        List<PersonRef> ambitious = new ArrayList<>(all.stream().filter(VillagerAmbitions::isAmbitious).toList());
        ambitious.sort(Comparator.comparing(PersonRef::uuid));
        int companyCap = Math.max(1, all.size() / VILLAGERS_PER_COMPANY);
        int partyCap = Math.max(1, all.size() / VILLAGERS_PER_PARTY);
        int companyCount = world.companies().size();
        int partyCount = world.parties().size();
        int actions = 0;
        int newCompanies = 0;
        int newParties = 0;
        int candidacies = 0;
        int takeovers = 0;
        // Reihum beginnen, damit nicht immer dieselben zuerst an die Reihe kommen.
        int start = ambitious.isEmpty() ? 0 : (int) Math.floorMod(day.value(), (long) ambitious.size());
        for (int index = 0; index < ambitious.size() && actions < MAX_ACTIONS; index++) {
            PersonRef villager = ambitious.get((start + index) % ambitious.size());
            Personality personality = Personality.of(villager);
            WorldQueries now = queries.get();
            double drive = drive(villager) / 100.0;
            if (takeOver(villager, personality, drive, now, day)) {
                takeovers++;
                actions++;
                continue;
            }
            if (candidate(villager, personality, drive, now, day)) {
                candidacies++;
                actions++;
                continue;
            }
            if (companyCount < companyCap && roll(villager, day, 0x52) < FOUNDING_CHANCE * drive
                    && foundCompany(villager, personality, now, day)) {
                companyCount++;
                newCompanies++;
                actions++;
                continue;
            }
            if (partyCount < partyCap && roll(villager, day, 0x53) < FOUNDING_CHANCE * drive
                    && foundParty(villager, personality, now, day)) {
                partyCount++;
                newParties++;
                actions++;
            }
        }
        return new Report(newCompanies, newParties, candidacies, takeovers);
    }

    private boolean candidate(PersonRef villager, Personality personality, double drive, WorldQueries world, Day day) {
        double chance = Math.min(1.0, CANDIDACY_CHANCE * drive * (1 + aggression(personality)));
        for (PolityView polity : world.polities()) {
            if ((polity.kind() != PolityKind.TERRITORIAL && polity.kind() != PolityKind.FEDERATION)
                    || !polity.isMember(villager)) {
                continue;
            }
            for (Election election : polity.elections()) {
                if (election.isCandidate(villager)
                        || roll(villager, day, 0x54 + election.office().ordinal()) >= chance) {
                    continue;
                }
                try {
                    standForElection.execute(new StandForElection.Command(polity.id(), election.id(), villager,
                            platformFor(personality), day));
                    return true;
                } catch (DomainException ignored) {
                    // kein Wahlrecht, Amtszeitgrenze o. Ä.: dann eben nicht
                }
            }
        }
        return false;
    }

    /**
     * Hält der Dorfbewohner die Aktienmehrheit einer Firma, übernimmt er sie. Sonst kauft ein aggressiver die Aktie der
     * Firma auf, die ihm am nächsten an der Mehrheit ist.
     */
    private boolean takeOver(PersonRef villager, Personality personality, double drive, WorldQueries world, Day day) {
        StockExchangeView exchange = world.exchange();
        PolityView target = null;
        double bestShare = 0;
        for (Listing listing : exchange.listings()) {
            Optional<PolityView> company = world.polity(listing.company());
            if (company.isEmpty() || company.get().kind() != PolityKind.COMPANY
                    || company.get().holds(villager, Office.MAYOR)) {
                continue;
            }
            long total = exchange.holders(listing.company()).values().stream().mapToLong(Long::longValue).sum();
            double share = total == 0 ? 0 : exchange.sharesOf(listing.company(), villager) / (double) total;
            if (share > bestShare) {
                bestShare = share;
                target = company.get();
            }
        }
        if (target != null) {
            try {
                if (takeOver.execute(target.id(), villager, day)) {
                    return true;
                }
            } catch (DomainException ignored) {
                // dann bleibt die Leitung, wie sie ist
            }
        }
        return aggression(personality) > 0.5 && roll(villager, day, 0x58) < FOUNDING_CHANCE * drive
                * (1 + aggression(personality)) && buyUp(villager, target, exchange, world, day);
    }

    /** Legt ein Kaufgebot auf die Aktie der Firma, mit der der Dorfbewohner am weitesten Richtung Mehrheit ist. */
    private boolean buyUp(PersonRef villager, PolityView started, StockExchangeView exchange, WorldQueries world,
                          Day day) {
        PolityView target = started;
        if (target == null) {
            target = world.companies().stream()
                    .filter(company -> exchange.listing(company.id()).isPresent())
                    .filter(company -> !company.holds(villager, Office.MAYOR))
                    .min(Comparator.comparing(company -> company.id().value())).orElse(null);
        }
        if (target == null) {
            return false;
        }
        PolityId targetId = target.id();
        long price = Math.max(1, exchange.price(targetId) * 105 / 100);
        long cash = world.ledger().balanceOf(villager, target.currency()).amount();
        long shares = cash / 2 / price;
        boolean hasOrder = exchange.ordersOf(villager).stream().anyMatch(order -> order.company().equals(targetId));
        if (shares <= 0 || hasOrder) {
            return false;
        }
        try {
            trading.place(villager, targetId, Side.BUY, shares, price, day);
            return true;
        } catch (DomainException ignored) {
            return false;
        }
    }

    private boolean foundCompany(PersonRef villager, Personality personality, WorldQueries world, Day day) {
        Optional<PolityView> home = world.municipalityOf(villager);
        if (home.isEmpty()) {
            return false;
        }
        long cash = world.ledger().balanceOf(villager, home.get().currency()).amount();
        long capital = cash / 4;
        if (capital < CompanyUseCases.MIN_CAPITAL || personality.thrift() < -60) {
            return false;
        }
        try {
            companies.foundCompany(villager, PolityName.of(companyName(villager)),
                    Money.of(capital, home.get().currency()), day);
            return true;
        } catch (DomainException ignored) {
            return false;
        }
    }

    private boolean foundParty(PersonRef villager, Personality personality, WorldQueries world, Day day) {
        Optional<PolityView> home = world.municipalityOf(villager);
        if (home.isEmpty() || world.partyOf(villager).isPresent()) {
            return false;
        }
        try {
            foundParty.execute(new FoundParty.Command(PolityName.of(partyName(villager, personality)), villager,
                    profileFor(personality), home.get().currency(), day));
            return true;
        } catch (DomainException ignored) {
            return false;
        }
    }

    static String companyName(PersonRef villager) {
        return "Handel " + tag(villager);
    }

    static String partyName(PersonRef villager, Personality personality) {
        String stem = switch (profileFor(personality).platform()) {
            case ORDER -> "Ordnung";
            case FREEDOM -> "Freiheit";
            case LOW_TAXES -> "Sparsame";
            case INVESTMENT -> "Aufbau";
            default -> "Bürger";
        };
        return stem + " " + tag(villager);
    }

    /** Kurzes eindeutiges Kennzeichen, damit Namen nicht kollidieren. */
    private static String tag(PersonRef villager) {
        return Long.toHexString(villager.uuid().getLeastSignificantBits() & 0xFFFFFL).toUpperCase();
    }

    /** Fester Zufall je Dorfbewohner, Tag und Tätigkeit, 0 bis unter 1. */
    public static double roll(PersonRef villager, Day day, int salt) {
        long mixed = villager.uuid().getMostSignificantBits() ^ villager.uuid().getLeastSignificantBits()
                ^ (day.value() * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53;
    }
}
