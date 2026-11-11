package de.etiennebader.civ.server;

import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.territory.ClaimPolicy;
import de.etiennebader.civ.domain.territory.CompositeClaimPolicy;

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
 */
public record ServerSettings(long ticksPerDay, int claimBase, int claimPerMember, CurrencyId currency,
                             Money priceReference, Money villagerIncome, TierThresholds tiers, WebSettings web,
                             boolean emigration) {

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
    public record WebSettings(boolean enabled, String bind, int port, String publicUrl) {

        public static final WebSettings DEFAULT = new WebSettings(true, "0.0.0.0", 8765, "");

        public WebSettings {
            Objects.requireNonNull(bind, "bind");
            Objects.requireNonNull(publicUrl, "publicUrl");
            if (port < 0 || port > 65_535) {
                throw new IllegalArgumentException("Ungültiger Port: " + port);
            }
        }

        public WebSettings disabled() {
            return new WebSettings(false, bind, port, publicUrl);
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
    }

    /** Wie {@link #defaults()}, aber mit eigener Tageslänge, z. B. für Tests. */
    public static ServerSettings withDayLength(long ticksPerDay) {
        ServerSettings defaults = defaults();
        return new ServerSettings(ticksPerDay, defaults.claimBase(), defaults.claimPerMember(), defaults.currency(),
                defaults.priceReference(), defaults.villagerIncome(), defaults.tiers(), defaults.web().disabled(),
                defaults.emigration());
    }

    /**
     * Ein Minecraft-Tag pro Zivilisationstag, 3×3 Chunks plus 4 je Mitglied, Smaragde als Währung,
     * neutrales Preisniveau bei 50 Smaragden je Spieler, ein Viertel Smaragd je Dorfbewohner und Tag.
     */
    public static ServerSettings defaults() {
        return new ServerSettings(Calendar.MINECRAFT_DAY_TICKS, 9, 4, CurrencyId.EMERALD,
                Money.of(50 * 100, CurrencyId.EMERALD), Money.of(25, CurrencyId.EMERALD), TierThresholds.DEFAULT,
                WebSettings.DEFAULT);
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
