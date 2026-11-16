package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.community.CommunityView;
import de.etiennebader.civ.domain.contract.Contract;
import de.etiennebader.civ.domain.economy.CurrencyTotals;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.exchange.StockExchangeView;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.ElectionResult;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Poster;
import de.etiennebader.civ.domain.polity.Promise;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.polity.Street;
import de.etiennebader.civ.domain.territory.BlockPosition;
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
                    polity.kind().name(), polity.level(), polity.tier().name(), polity.currency().code(),
                    day(polity.foundedOn().value()),
                    polity.seat().map(BlockPosition::toString).orElse("-")));
            polity.partyProfile().ifPresent(profile -> lines.add(String.join("|", "  party",
                    Integer.toString(profile.order()), Integer.toString(profile.economy()), profile.emblem())));
            if (polity.constitution().proclaimed() || !polity.constitution().preamble().isEmpty()) {
                lines.add(String.join("|", "  constitution",
                        polity.constitution().proclaimedOn().map(day -> day(day.value())).orElse("draft"),
                        polity.constitution().preamble()));
            }
            for (Membership membership : polity.members()) {
                lines.add(String.join("|", "  member", actor(membership.member()), membership.type().name(),
                        day(membership.since().value())));
            }
            for (EnactedStatute enacted : polity.laws().inForce()) {
                String values = enacted.statute().values().stream()
                        .map(value -> value.key().id() + "=" + ParameterCatalog.encode(value))
                        .collect(Collectors.joining(","));
                lines.add(String.join("|", "  statute", enacted.statute().id().value().toString(),
                        enacted.statute().title(), day(enacted.enactedOn().value()), values)
                        + (enacted.statute().text().isEmpty() ? "" : "|text=" + enacted.statute().text()));
            }
            for (Proposal proposal : polity.proposals()) {
                String votes = proposal.votes().stream()
                        .map(vote -> actor(vote.voter()) + (vote.inFavour() ? "+" : "-"))
                        .collect(Collectors.joining(","));
                lines.add(String.join("|", "  proposal", proposal.id().value().toString(),
                        motion(proposal.motion()),
                        actor(proposal.proposer()), day(proposal.openedOn().value()), votes));
            }
            for (OfficeTerm term : polity.offices()) {
                lines.add(String.join("|", "  office", term.office().name(), actor(term.holder()),
                        day(term.since().value()), term.until().map(until -> day(until.value())).orElse("-")));
            }
            for (Election election : polity.elections()) {
                String candidates = election.candidates().stream()
                        .map(candidate -> actor(candidate) + ":" + election.platformOf(candidate).name())
                        .collect(Collectors.joining(","));
                String ballots = election.ballots().stream()
                        .map(ballot -> actor(ballot.voter()) + ">" + actor(ballot.candidate()))
                        .collect(Collectors.joining(","));
                lines.add(String.join("|", "  election", election.id().value().toString(), election.office().name(),
                        day(election.calledOn().value()), day(election.closesOn().value()), candidates, ballots));
            }
            if (polity.parliament().seated()) {
                String seats = polity.parliament().seats().stream()
                        .map(seat -> actor(seat.member()) + ":" + seat.party().map(party -> party.value().toString())
                                .orElse("-") + ":" + seat.votes())
                        .collect(Collectors.joining(","));
                lines.add(String.join("|", "  parliament", day(polity.parliament().electedOn().orElseThrow().value()),
                        polity.parliament().until().map(until -> day(until.value())).orElse("-"), seats));
            }
            for (Parliament.Seat reserve : polity.parliament().reserves()) {
                lines.add(String.join("|", "  reserve", actor(reserve.member()),
                        reserve.party().map(party -> party.value().toString()).orElse("-"),
                        Long.toString(reserve.votes())));
            }
            for (ElectionResult result : polity.results()) {
                String districts = result.districts().stream()
                        .map(district -> district.district().map(id -> id.value().toString()).orElse("-") + "="
                                + district.votes().entrySet().stream()
                                .map(entry -> actor(entry.getKey()) + ":" + entry.getValue())
                                .collect(Collectors.joining(",")))
                        .collect(Collectors.joining(";"));
                lines.add(String.join("|", "  result", result.id().value().toString(), result.office().name(),
                        day(result.closedOn().value()), districts,
                        result.winners().stream().map(StateDump::actor).collect(Collectors.joining(","))));
            }
            for (Promise promise : polity.promises()) {
                lines.add(String.join("|", "  promise", actor(promise.candidate()), promise.office().name(),
                        promise.text(), day(promise.madeOn().value()), promise.values().stream()
                                .map(value -> value.key().id() + "=" + value.value())
                                .collect(Collectors.joining(","))));
            }
            for (Street street : polity.streets()) {
                lines.add(String.join("|", "  street", street.name(), street.area().toString()));
            }
            for (Poster poster : polity.posters()) {
                lines.add(String.join("|", "  poster", poster.position().toString(), actor(poster.subject()),
                        actor(poster.owner()), day(poster.placedOn().value())));
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
        world.shops().shops().stream()
                .map(shop -> "shop|" + shop.position() + "|" + actor(shop.owner()) + "|" + shop.commodity().id() + "|"
                        + shop.price().amount() + shop.price().currency().code() + "|" + day(shop.openedOn().value()))
                .sorted()
                .forEach(lines::add);
        for (Contract contract : world.contracts().contracts()) {
            lines.add(String.join("|", "contract", Integer.toString(contract.number()), contract.id().value().toString(),
                    contract.kind().name(), actor(contract.offeror()), actor(contract.counterparty()),
                    actor(contract.payer()), actor(contract.payee()), Long.toString(contract.perDay().amount()),
                    Long.toString(contract.once().amount()), Long.toString(contract.deposit().amount()),
                    contract.plot().map(Object::toString).orElse("-"), contract.title(), contract.text(),
                    Long.toString(contract.termDays()), day(contract.offeredOn().value()),
                    contract.startedOn().map(start -> day(start.value())).orElse("-"),
                    contract.endsOn().map(end -> day(end.value())).orElse("-"), contract.status().name(),
                    contract.endReason().map(Enum::name).orElse("-")));
        }
        CommunityView community = world.community();
        community.letters().forEach(letter -> lines.add(String.join("|", "letter", letter.id().value().toString(),
                actor(letter.from()), actor(letter.to()), letter.subject(), letter.body(),
                day(letter.sentOn().value()), day(letter.deliverOn().value()), Boolean.toString(letter.read()))));
        community.friendships().forEach(pair -> lines.add("friends|" + actor(pair.get(0)) + "|" + actor(pair.get(1))));
        community.requests().forEach(pair -> lines.add("request|" + actor(pair.get(0)) + "|" + actor(pair.get(1))));
        community.trusts().forEach(pair -> lines.add("trust|" + actor(pair.get(0)) + "|" + actor(pair.get(1))));
        community.messages().forEach(message -> lines.add(String.join("|", "chat", Long.toString(message.seq()),
                actor(message.from()), message.to().map(StateDump::actor).orElse("-"), message.text(),
                Long.toString(message.at()), message.origin())));
        StockExchangeView exchange = world.exchange();
        exchange.listings().forEach(listing -> lines.add(String.join("|", "listing", listing.company().value().toString(),
                Long.toString(listing.totalShares()), Long.toString(listing.initialPrice()),
                day(listing.listedOn().value()))));
        exchange.allHoldings().stream()
                .map(holding -> String.join("|", "holding", holding.company().value().toString(), actor(holding.holder()),
                        Long.toString(holding.shares())))
                .sorted().forEach(lines::add);
        exchange.orders().forEach(order -> lines.add(String.join("|", "order", Integer.toString(order.number()),
                order.id().value().toString(), order.company().value().toString(), actor(order.trader()),
                order.side().name(), Long.toString(order.quantity()), Long.toString(order.remaining()),
                Long.toString(order.limit()), day(order.placedOn().value()))));
        exchange.trades().forEach(trade -> lines.add(String.join("|", "trade", trade.company().value().toString(),
                actor(trade.buyer()), actor(trade.seller()), Long.toString(trade.quantity()),
                Long.toString(trade.price()), day(trade.day().value()))));
        exchange.allQuotes().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey(java.util.Comparator.comparing(id -> id.value().toString())))
                .forEach(entry -> entry.getValue().forEach(quote -> lines.add(String.join("|", "quote",
                        entry.getKey().value().toString(), day(quote.day().value()), Long.toString(quote.open()),
                        Long.toString(quote.high()), Long.toString(quote.low()), Long.toString(quote.close()),
                        Long.toString(quote.volume())))));
        exchange.dividends().forEach(dividend -> lines.add(String.join("|", "dividend",
                dividend.company().value().toString(), Long.toString(dividend.perShare()),
                Long.toString(dividend.total()), day(dividend.day().value()))));
        world.press().articles().forEach(article -> lines.add(String.join("|", "article",
                Integer.toString(article.number()), article.id().value().toString(), article.newspaper().value().toString(),
                actor(article.author()), article.title(), article.body(), article.topic().name(),
                article.subject().map(StateDump::actor).orElse("-"), Integer.toString(article.tendency()),
                day(article.day().value()), article.towns().toString(), Integer.toString(article.readers()))));
        world.civic().constituencies().forEach(constituency -> lines.add(String.join("|", "constituency",
                constituency.id().value().toString(), constituency.polity().value().toString(), constituency.name(),
                constituency.areas().toString(), constituency.towns().toString())));
        world.civic().flags().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(
                java.util.Comparator.comparing(id -> id.value().toString()))).forEach(entry -> lines.add(String.join("|",
                "flag", entry.getKey().value().toString(), entry.getValue().toString())));
        world.civic().anthems().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(
                java.util.Comparator.comparing(id -> id.value().toString()))).forEach(entry -> lines.add(String.join("|",
                "anthem", entry.getKey().value().toString(), entry.getValue().toString())));
        world.justice().cases().forEach(found -> lines.add(String.join("|", "case", Integer.toString(found.number()),
                found.id().value().toString(), found.court().value().toString(), found.kind().name(), actor(found.plaintiff()),
                actor(found.defendant()), found.charge(), found.target(), found.stage().name(),
                day(found.filedOn().value()), day(found.stageUntil().value()), found.judge().map(StateDump::actor).orElse("-"),
                found.prosecutor().map(StateDump::actor).orElse("-"), found.defense().map(StateDump::actor).orElse("-"),
                found.witnesses().stream().map(StateDump::actor).toList().toString(),
                found.jurors().stream().map(StateDump::actor).toList().toString(),
                Integer.toString(found.evidence().size()), Integer.toString(found.statements().size()),
                found.votes().toString(), found.verdict().map(Object::toString).orElse("-"),
                found.appealOf().map(id -> id.value().toString()).orElse("-"))));
        world.justice().staff().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(
                java.util.Comparator.comparing(id -> id.value().toString()))).forEach(entry -> lines.add(String.join("|",
                "court", entry.getKey().value().toString(),
                entry.getValue().judges().stream().map(StateDump::actor).toList().toString(),
                entry.getValue().prosecutors().stream().map(StateDump::actor).toList().toString())));
        world.justice().exiles().forEach(exile -> lines.add(String.join("|", "exile", exile.polity().value().toString(),
                actor(exile.actor()), day(exile.until().value()))));
        world.market().stocks().stream()
                .map(stock -> "stock|" + stock.commodity().id() + "|" + stock.quantity() + "|" + day(stock.asOf().value()))
                .sorted()
                .forEach(lines::add);
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
                actor(plot.holder()), day(plot.holderSince().value()))
                + plot.askingPrice().map(price -> "|offer=" + price.amount() + price.currency().code()).orElse("");
    }

    private static String motion(Motion motion) {
        return switch (motion) {
            case Motion.Change change -> change.change().key().id() + "=" + ParameterCatalog.encode(change.change());
            case Motion.Enact enact -> "enact:" + enact.title() + ":" + enact.text() + ":" + enact.values().stream()
                    .map(value -> value.key().id() + "=" + ParameterCatalog.encode(value))
                    .collect(Collectors.joining(","));
            case Motion.Repeal repeal -> "repeal:" + repeal.statute().value() + ":" + repeal.title();
        };
    }

    private static String actor(ActorRef actor) {
        return actor.getClass().getSimpleName() + ":" + actor.uuid();
    }

    private static String day(long day) {
        return "d" + day;
    }
}
