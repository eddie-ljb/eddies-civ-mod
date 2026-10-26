package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.CurrencyTotals;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.domain.territory.TerritoryView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Kanonische Textform des gesamten Weltzustands: eine Zeile pro Eintrag, in fester Reihenfolge.
 * Zwei Zustände sind genau dann fachlich gleich, wenn ihre Dumps gleich sind. Gedacht für Tests
 * („Zustand übersteht Speichern und Laden“) und zur Fehlersuche.
 *
 * <p>Reihenfolgen, die fachlich zählen (Gemeinden, Mitglieder, Gesetze, Grundstücke), bleiben
 * erhalten; Mengen ohne Reihenfolge (Claims, Konten) werden sortiert.
 */
public final class StateDump {

    private StateDump() {
    }

    public static String of(WorldQueries world) {
        List<String> lines = new ArrayList<>();
        TerritoryView territory = world.territory();
        for (PolityView polity : world.polities()) {
            lines.add(String.join("|", "polity", polity.id().value().toString(), polity.name().value(),
                    polity.kind().name(), polity.tier().name(), polity.currency().code(), day(polity.foundedOn().value())));
            for (Membership membership : polity.members()) {
                lines.add(String.join("|", "  member", actor(membership.member()), membership.type().name(),
                        day(membership.since().value())));
            }
            for (EnactedStatute enacted : polity.laws().inForce()) {
                String values = enacted.statute().values().stream()
                        .map(value -> value.key().id() + "=" + ParameterCatalog.encode(value))
                        .collect(Collectors.joining(","));
                lines.add(String.join("|", "  statute", enacted.statute().id().value().toString(),
                        enacted.statute().title(), day(enacted.enactedOn().value()), values));
            }
            territory.territoryOf(polity.id()).stream()
                    .sorted(Comparator.comparing((ChunkCoord chunk) -> chunk.dimension().value())
                            .thenComparingInt(ChunkCoord::x).thenComparingInt(ChunkCoord::z))
                    .forEach(chunk -> lines.add(claim(chunk, territory.claimAt(chunk).orElseThrow())));
            for (Plot plot : world.land(polity.id()).plots()) {
                lines.add(plot(plot));
            }
        }
        LedgerView ledger = world.ledger();
        ledger.accountBalances().stream()
                .map(balance -> "account|" + actor(balance.account()) + "|" + balance.balance())
                .sorted()
                .forEach(lines::add);
        ledger.currencyTotals().stream()
                .sorted(Comparator.comparing((CurrencyTotals totals) -> totals.currency().code()))
                .forEach(totals -> lines.add("currency|" + totals.currency().code() + "|supply=" + totals.supply().amount()
                        + "|cash=" + totals.cashInCirculation().amount()));
        return String.join("\n", lines) + "\n";
    }

    private static String claim(ChunkCoord chunk, Claim claim) {
        return String.join("|", "  claim", chunk.dimension().value(), Integer.toString(chunk.x()), Integer.toString(chunk.z()),
                claim.type().name(), day(claim.since().value()));
    }

    private static String plot(Plot plot) {
        Area area = plot.area();
        return String.join("|", "  plot", plot.id().value().toString(), plot.type().name(),
                area.dimension().value() + "[" + area.minX() + "," + area.minZ() + ".." + area.maxX() + "," + area.maxZ() + "]",
                actor(plot.holder()), day(plot.holderSince().value()));
    }

    private static String actor(ActorRef actor) {
        return actor.getClass().getSimpleName() + ":" + actor.uuid();
    }

    private static String day(long day) {
        return "d" + day;
    }
}
