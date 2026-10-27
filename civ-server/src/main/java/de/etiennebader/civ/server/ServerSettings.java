package de.etiennebader.civ.server;

import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.territory.ClaimPolicy;
import de.etiennebader.civ.domain.territory.CompositeClaimPolicy;

import java.util.Objects;

/**
 * Einstellungen eines Servers. Später aus einer Konfigurationsdatei; vorerst feste Standardwerte.
 *
 * @param ticksPerDay    Länge eines Zivilisationstags in Spielticks
 * @param claimBase      Chunks, die jede Gemeinde unabhängig von ihrer Größe beanspruchen darf
 * @param claimPerMember zusätzliche Chunks je Mitglied
 * @param currency       Währung neuer Gemeinden
 */
public record ServerSettings(long ticksPerDay, int claimBase, int claimPerMember, CurrencyId currency) {

    public ServerSettings {
        if (ticksPerDay < 1) {
            throw new IllegalArgumentException("ticksPerDay muss mindestens 1 sein: " + ticksPerDay);
        }
        if (claimBase < 1 || claimPerMember < 0) {
            throw new IllegalArgumentException("claimBase muss mindestens 1, claimPerMember mindestens 0 sein");
        }
        Objects.requireNonNull(currency, "currency");
    }

    /** Ein Minecraft-Tag pro Zivilisationstag, 3×3 Chunks plus 4 je Mitglied, Smaragde als Währung. */
    public static ServerSettings defaults() {
        return new ServerSettings(Calendar.MINECRAFT_DAY_TICKS, 9, 4, CurrencyId.EMERALD);
    }

    /** Zusammenhängendes Kerngebiet, begrenzte Außenposten, Größe nach Mitgliederzahl. */
    public ClaimPolicy claimPolicy() {
        return CompositeClaimPolicy.standard(claimBase, claimPerMember);
    }
}
