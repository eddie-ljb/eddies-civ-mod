package de.etiennebader.civ.server;

import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.domain.market.PriceLevel;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.territory.ClaimPolicy;
import de.etiennebader.civ.domain.territory.CompositeClaimPolicy;
import de.etiennebader.civ.server.federation.Levels;

import java.util.Objects;

/**
 * Einstellungen eines Servers. Später aus einer Konfigurationsdatei; vorerst feste Standardwerte.
 *
 * <p>Geld wird in Hundertsteln eines Smaragds geführt: 1 Smaragd = {@value de.etiennebader.civ.server.command.MoneyFormat#UNITS_PER_EMERALD}
 * Einheiten. So lassen sich auch billige Waren wie Erde bepreisen.
 *
 * @param ticksPerDay    Länge eines Zivilisationstags in Spielticks
 * @param claimBase      Chunks, die jede Gemeinde unabhängig von ihrer Größe beanspruchen darf
 * @param claimPerMember zusätzliche Chunks je Mitglied
 * @param currency       Währung neuer Gemeinden und des Servermarkts
 * @param priceReference Geldmenge je Spieler, bei der Marktpreise ihrem Grundpreis entsprechen
 * @param villagerIncome tägliche Wirtschaftsleistung je Dorfbewohner für die Gemeindekasse
 * @param tiers          ab wie vielen Einwohnern eine Gemeinde welche Stufe erreicht
 * @param web            die eingebettete Webseite
 * @param emigration     ob sehr unzufriedene Dorfbewohner ihre Gemeinde verlassen
 * @param villagerPolitics ob Dorfbewohner ohne Spieler-Bürgermeister kommissarisch regieren, Bünde gründen/beitreten
 *                         und Firmen gründen dürfen
 * @param levels         politische Ebenen, auf denen Bünde gegründet werden können
 * @param parties        ob und ab wie vielen Gleichgesinnten Dorfbewohner Parteien gründen
 * @param show           Inszenierung: Live-Auszählung, Debatten, Hymnen, Weltkarte
 * @param economy        Fein-tuning der Marktpreissimulation (Preisfaktor-Grenzen und -Dämpfung)
 * @param founding       Gründungskosten und Vorgänger-Pflicht für Gemeinden, Bünde und Firmen
 * @param ambitions      ob ehrgeizige Dorfbewohner Firmen, Parteien und Bünde gründen, kandidieren, Gebiet beanspruchen,
 *                       Firmen übernehmen und führen
 * @param warPortLock    wer sich im Krieg nicht mehr zu den Heimat-Häfen einer Polity porten darf
 */
public record ServerSettings(long ticksPerDay, int claimBase, int claimPerMember, CurrencyId currency,
                             Money priceReference, Money villagerIncome, TierThresholds tiers, WebSettings web,
                             boolean emigration, boolean villagerPolitics, Levels levels, PartySettings parties,
                             ShowSettings show, EconomySettings economy, boolean ambitions,
                             de.etiennebader.civ.server.founding.FoundingFees founding) {

    /** Ohne Gründungskosten. */
    public ServerSettings(long ticksPerDay, int claimBase, int claimPerMember, CurrencyId currency,
                          Money priceReference, Money villagerIncome, TierThresholds tiers, WebSettings web,
                          boolean emigration, boolean villagerPolitics, Levels levels, PartySettings parties,
                          ShowSettings show, EconomySettings economy, boolean ambitions) {
        this(ticksPerDay, claimBase, claimPerMember, currency, priceReference, villagerIncome, tiers, web, emigration,
                villagerPolitics, levels, parties, show, economy, ambitions,
                de.etiennebader.civ.server.founding.FoundingFees.FREE);
    }

    /** Mit ehrgeizigen Dorfbewohnern. */
    public ServerSettings(long ticksPerDay, int claimBase, int claimPerMember, CurrencyId currency,
                          Money priceReference, Money villagerIncome, TierThresholds tiers, WebSettings web,
                          boolean emigration, boolean villagerPolitics, Levels levels, PartySettings parties,
                          ShowSettings show, EconomySettings economy) {
        this(ticksPerDay, claimBase, claimPerMember, currency, priceReference, villagerIncome, tiers, web, emigration,
                villagerPolitics, levels, parties, show, economy, true);
    }

    /** Mit Standard-Wirtschaftssimulation. */
    public ServerSettings(long ticksPerDay, int claimBase, int claimPerMember, CurrencyId currency,
                          Money priceReference, Money villagerIncome, TierThresholds tiers, WebSettings web,
                          boolean emigration, boolean villagerPolitics, Levels levels, PartySettings parties,
                          ShowSettings show) {
        this(ticksPerDay, claimBase, claimPerMember, currency, priceReference, villagerIncome, tiers, web, emigration,
                villagerPolitics, levels, parties, show, EconomySettings.DEFAULT);
    }

    /** Mit Standard-Inszenierung und -Wirtschaftssimulation. */
    public ServerSettings(long ticksPerDay, int claimBase, int claimPerMember, CurrencyId currency,
                          Money priceReference, Money villagerIncome, TierThresholds tiers, WebSettings web,
                          boolean emigration, Levels levels, PartySettings parties) {
        this(ticksPerDay, claimBase, claimPerMember, currency, priceReference, villagerIncome, tiers, web, emigration,
                true, levels, parties, ShowSettings.DEFAULT, EconomySettings.DEFAULT);
    }

    /**
     * Fein-tuning der Marktpreissimulation ({@link de.etiennebader.civ.domain.market.PriceLevel}).
     *
     * @param priceLevelMin     kleinster Preisfaktor bei sehr geringer Geldmenge je Kopf, mindestens
     *                          {@value de.etiennebader.civ.domain.market.PriceLevel#MIN}
     * @param priceLevelMax     größter Preisfaktor bei sehr hoher Geldmenge je Kopf (Inflation), höchstens
     *                          {@value de.etiennebader.civ.domain.market.PriceLevel#MAX}
     * @param priceLevelDamping Dämpfung der Preisreaktion auf die Geldmenge je Kopf; 0,5 entspricht der
     *                          bisherigen festen Formel (Quadratwurzel), höhere Werte lassen die Preise
     *                          stärker auf Inflation/Deflation reagieren
     */
    public record EconomySettings(double priceLevelMin, double priceLevelMax, double priceLevelDamping) {

        public static final EconomySettings DEFAULT =
                new EconomySettings(PriceLevel.MIN, PriceLevel.MAX, PriceLevel.DEFAULT_DAMPING);

        public EconomySettings {
            if (!(priceLevelMin >= PriceLevel.MIN && priceLevelMin < priceLevelMax && priceLevelMax <= PriceLevel.MAX)) {
                throw new IllegalArgumentException(
                        "price_level_min/price_level_max außerhalb von " + PriceLevel.MIN + ".." + PriceLevel.MAX
                                + " oder min >= max: " + priceLevelMin + ".." + priceLevelMax);
            }
            if (!(priceLevelDamping > 0)) {
                throw new IllegalArgumentException("price_level_damping muss positiv sein: " + priceLevelDamping);
            }
        }
    }

    /**
     * Inszenierung von Wahlen und Hoheitszeichen.
     *
     * @param liveCount        ob Wahlergebnisse als Live-Auszählung nach und nach erscheinen
     * @param liveCountSeconds wie lange eine Live-Auszählung dauert
     * @param projections      ob während der Auszählung Hochrechnungen erscheinen
     * @param debateMinutes    Länge einer Wahldebatte; 0 schaltet Debatten ab
     * @param anthemOnEnter    ob die Hymne spielt, wenn man das Gebiet einer Gemeinde betritt
     * @param worldMap         ob die Webseite eine gerenderte Weltkarte zeigt
     * @param mapRadius        Umkreis in Chunks um Spieler, der für die Karte gezeichnet wird
     */
    public record ShowSettings(boolean liveCount, int liveCountSeconds, boolean projections, int debateMinutes,
                               boolean anthemOnEnter, boolean worldMap, int mapRadius) {

        public static final ShowSettings DEFAULT = new ShowSettings(true, 60, true, 5, false, true, 6);

        public ShowSettings {
            if (liveCountSeconds < 5 || liveCountSeconds > 600) {
                throw new IllegalArgumentException("Live-Auszählung 5–600 Sekunden: " + liveCountSeconds);
            }
            if (debateMinutes < 0 || debateMinutes > 60) {
                throw new IllegalArgumentException("Debatte 0–60 Minuten: " + debateMinutes);
            }
            if (mapRadius < 1 || mapRadius > 32) {
                throw new IllegalArgumentException("Kartenumkreis 1–32 Chunks: " + mapRadius);
            }
        }
    }

    /** Mit Standardregeln für Parteien. */
    public ServerSettings(long ticksPerDay, int claimBase, int claimPerMember, CurrencyId currency,
                          Money priceReference, Money villagerIncome, TierThresholds tiers, WebSettings web,
                          boolean emigration, Levels levels) {
        this(ticksPerDay, claimBase, claimPerMember, currency, priceReference, villagerIncome, tiers, web, emigration,
                true, levels, PartySettings.DEFAULT, ShowSettings.DEFAULT);
    }

    /**
     * Parteien der Dorfbewohner.
     *
     * @param villagers ob Dorfbewohner Parteien beitreten und gründen
     * @param founders  wie viele parteilose Gleichgesinnte einer Gemeinde mindestens eine Partei gründen
     */
    public record PartySettings(boolean villagers, int founders) {

        public static final PartySettings DEFAULT = new PartySettings(true, 5);

        public PartySettings {
            if (founders < 2) {
                throw new IllegalArgumentException("Mindestens 2 Gründer: " + founders);
            }
        }
    }

    /** Mit den eingebauten Ebenen. */
    public ServerSettings(long ticksPerDay, int claimBase, int claimPerMember, CurrencyId currency,
                          Money priceReference, Money villagerIncome, TierThresholds tiers, WebSettings web,
                          boolean emigration) {
        this(ticksPerDay, claimBase, claimPerMember, currency, priceReference, villagerIncome, tiers, web, emigration,
                true, Levels.DEFAULT, PartySettings.DEFAULT, ShowSettings.DEFAULT);
    }

    /** Mit Abwanderung. */
    public ServerSettings(long ticksPerDay, int claimBase, int claimPerMember, CurrencyId currency,
                          Money priceReference, Money villagerIncome, TierThresholds tiers, WebSettings web) {
        this(ticksPerDay, claimBase, claimPerMember, currency, priceReference, villagerIncome, tiers, web, true);
    }

    /**
     * Die eingebettete Webseite.
     *
     * @param bind      Adresse, an die der Server bindet, z. B. {@code 0.0.0.0}
     * @param publicUrl Adresse, die Spieler im Browser öffnen; leer heißt „Server-Adresse und Port“
     */
    public record WebSettings(boolean enabled, String bind, int port, String publicUrl, boolean trustProxy) {

        public static final WebSettings DEFAULT = new WebSettings(true, "0.0.0.0", 8765, "", false);

        /** Ohne Reverse-Proxy. */
        public WebSettings(boolean enabled, String bind, int port, String publicUrl) {
            this(enabled, bind, port, publicUrl, false);
        }

        public WebSettings {
            Objects.requireNonNull(bind, "bind");
            Objects.requireNonNull(publicUrl, "publicUrl");
            if (port < 0 || port > 65_535) {
                throw new IllegalArgumentException("Ungültiger Port: " + port);
            }
        }

        public WebSettings disabled() {
            return new WebSettings(false, bind, port, publicUrl, trustProxy);
        }
    }

    /** Einwohner (Spieler und Dorfbewohner), ab denen eine Gemeinde Marktgemeinde, Stadt bzw. Metropole wird. */
    public record TierThresholds(int marketTown, int city, int metropolis) {

        public static final TierThresholds DEFAULT = new TierThresholds(10, 30, 80);

        public TierThresholds {
            if (marketTown < 2 || city <= marketTown || metropolis <= city) {
                throw new IllegalArgumentException("Schwellen müssen aufsteigend und mindestens 2 sein: "
                        + marketTown + ", " + city + ", " + metropolis);
            }
        }

        public SettlementTier tierFor(int inhabitants) {
            if (inhabitants >= metropolis) {
                return SettlementTier.METROPOLIS;
            }
            if (inhabitants >= city) {
                return SettlementTier.CITY;
            }
            return inhabitants >= marketTown ? SettlementTier.MARKET_TOWN : SettlementTier.MUNICIPALITY;
        }
    }

    public ServerSettings {
        if (ticksPerDay < 1) {
            throw new IllegalArgumentException("ticksPerDay muss mindestens 1 sein: " + ticksPerDay);
        }
        if (claimBase < 1 || claimPerMember < 0) {
            throw new IllegalArgumentException("claimBase muss mindestens 1, claimPerMember mindestens 0 sein");
        }
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(priceReference, "priceReference");
        if (!priceReference.isPositive() || !priceReference.currency().equals(currency)) {
            throw new IllegalArgumentException("priceReference muss positiv und in " + currency + " sein");
        }
        Objects.requireNonNull(villagerIncome, "villagerIncome");
        if (villagerIncome.isNegative() || !villagerIncome.currency().equals(currency)) {
            throw new IllegalArgumentException("villagerIncome darf nicht negativ sein und muss in " + currency + " sein");
        }
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(web, "web");
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(parties, "parties");
        Objects.requireNonNull(economy, "economy");
    }

    /** Wie {@link #defaults()}, aber mit eigener Tageslänge, z. B. für Tests. */
    public static ServerSettings withDayLength(long ticksPerDay) {
        ServerSettings defaults = defaults();
        return new ServerSettings(ticksPerDay, defaults.claimBase(), defaults.claimPerMember(), defaults.currency(),
                defaults.priceReference(), defaults.villagerIncome(), defaults.tiers(), defaults.web().disabled(),
                defaults.emigration(), defaults.villagerPolitics(), defaults.levels(), defaults.parties(),
                new ShowSettings(false, 60, true, 5, false, false, 6));
    }

    /**
     * Ein Minecraft-Tag pro Zivilisationstag, 3×3 Chunks plus 4 je Mitglied, Smaragde als Währung,
     * neutrales Preisniveau bei 50 Smaragden je Spieler, ein Viertel Smaragd je Dorfbewohner und Tag.
     */
    public static ServerSettings defaults() {
        return new ServerSettings(Calendar.MINECRAFT_DAY_TICKS, 9, 4, CurrencyId.EMERALD,
                Money.of(50 * 100, CurrencyId.EMERALD), Money.of(25, CurrencyId.EMERALD), TierThresholds.DEFAULT,
                WebSettings.DEFAULT).withFounding(de.etiennebader.civ.server.founding.FoundingFees.DEFAULT);
    }

    /** Dieselben Einstellungen mit anderen Gründungskosten. */
    public ServerSettings withFounding(de.etiennebader.civ.server.founding.FoundingFees fees) {
        return new ServerSettings(ticksPerDay, claimBase, claimPerMember, currency, priceReference, villagerIncome,
                tiers, web, emigration, villagerPolitics, levels, parties, show, economy, ambitions, fees);
    }

    /** Zusammenhängendes Kerngebiet, begrenzte Außenposten, Größe nach Mitgliederzahl. */
    public ClaimPolicy claimPolicy() {
        return CompositeClaimPolicy.standard(claimBase, claimPerMember);
    }

    /** Wie viele Chunks eine Gemeinde mit diesem Gewicht ({@code PolityView#claimWeight()}) höchstens beanspruchen darf. */
    public int claimLimit(int members) {
        return claimBase + claimPerMember * members;
    }
}
