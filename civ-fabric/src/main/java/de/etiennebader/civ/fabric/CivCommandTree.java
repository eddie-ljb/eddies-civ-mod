package de.etiennebader.civ.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.etiennebader.civ.EddiesCivMod;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.CivCommands;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.office.OfficeService;
import de.etiennebader.civ.server.office.OfficeView;
import de.etiennebader.civ.server.plot.PlotService;
import de.etiennebader.civ.server.town.TownHallService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Der Befehlsbaum {@code /civ}. Liest nur Argumente und Spielerposition aus und gibt die
 * Antworten von {@link CivCommands} aus; die eigentliche Logik liegt in {@code civ-server}.
 *
 * <pre>
 * /civ [info]                       eigene Gemeinde, aktueller Chunk, Kontostand, Tag
 * /civ beitreten &lt;Name&gt;             einer Gemeinde beitreten
 * /civ austreten                    aus der eigenen Gemeinde austreten
 * /civ beanspruchen [aussenposten]  aktuellen Chunk beanspruchen
 * /civ freigeben                    aktuellen Chunk aufgeben
 * /civ gemeinde [Name]              Steckbrief einer Gemeinde
 * /civ liste                        alle Gemeinden
 * /civ recht                        Regelungen der eigenen Gemeinde
 * /civ recht setzen &lt;Regel&gt; &lt;Wert&gt; Regelung ändern (nur Bürger)
 * /civ menue                       Menü der eigenen Gemeinde
 * /civ karte                        Karte der Chunks ringsum, Beanspruchen per Klick
 * /civ grenzen                      Gebietsgrenzen kurz als Partikel anzeigen
 * /civ hilfe                        Überblick über alle Befehle
 * /civ konto                        Kontostand
 * /civ einzahlen [Anzahl]           Smaragde aus dem Inventar einzahlen (ohne Anzahl: alle)
 * /civ abheben &lt;Anzahl&gt;             Smaragde abheben
 * /civ ueberweisen &lt;Wer&gt; &lt;Betrag&gt;   an Spieler oder Gemeinde überweisen, z. B. 2,5
 * /civ kasse einzahlen|entnehmen &lt;Betrag&gt;  Gemeindekasse (Entnehmen nur Bürgermeister)
 * /civ preis                        Marktpreise des Items in der Hand
 * /civ verkaufen [Anzahl]           Item in der Hand an den Markt verkaufen
 * /civ kaufen &lt;Anzahl&gt; &lt;Item&gt;       vom Markt kaufen
 * /civ laden eroeffnen &lt;Preis&gt;   Kiste im Blick wird Laden für das Item in der Hand
 * /civ laden schliessen             Laden an der Kiste im Blick schließen
 * /civ laden liste                  eigene Läden
 * /civ web                          Anmeldecode für die Webseite
 * /civ abstimmungen                offene Abstimmungen der eigenen Gemeinde
 * /civ abstimmen &lt;Nr&gt; ja|nein     abstimmen (nur Bürger)
 * /civ aemter                      Ämter, Inhaber und laufende Wahlen
 * /civ wahl ausrufen &lt;Amt&gt;         Wahl ausrufen (Bürgermeister; andere bei freiem Amt)
 * /civ wahl kandidieren &lt;Amt&gt; [Programm]  kandidieren (nur Bürger); ordnung|freiheit|steuern|ausbau|ohne
 * /civ stimmung                    Stimmung der Dorfbewohner der eigenen Gemeinde
 * /civ wahl stimmen &lt;Amt&gt; &lt;Name&gt;  wählen (nur Bürger), bis zum Ende der Wahl änderbar
 * /civ amt ernennen &lt;Amt&gt; &lt;Name&gt;  Schatzmeister oder Bauamt ernennen (Bürgermeister)
 * /civ amt entlassen &lt;Amt&gt;         Amt frei machen (Bürgermeister)
 * /civ grundstueck [info]           Grundstück unter den Füßen, Umriss als Partikel
 * /civ grundstueck ecke1|ecke2      Ecken einer Auswahl setzen
 * /civ grundstueck erstellen &lt;Art&gt;  Auswahl zum Grundstück der Gemeinde machen (Bürger)
 * /civ grundstueck chunk &lt;Art&gt;      ganzen Chunk zum Grundstück machen (Bürger)
 * /civ grundstueck anbieten &lt;Preis&gt; zum Verkauf anbieten (Inhaber; bei Gemeindeland Bürger)
 * /civ grundstueck zurueckziehen    Angebot zurückziehen
 * /civ grundstueck kaufen           angebotenes Grundstück kaufen
 * /civ grundstueck vergeben &lt;Spieler&gt;  Gemeindeland verschenken (Bürger)
 * /civ grundstueck aufloesen        Gemeindeland-Grundstück auflösen (Bürger)
 *
 * Nur Admins:
 * /civ gruenden &lt;Name&gt;              Gemeinde ohne Gemeindehaus-Block gründen, Sitz ist der eigene Standort
 * /civ admin umgehen                Gebietsschutz für sich selbst aus- und wieder einschalten
 * /civ admin aufloesen &lt;Name&gt;      Gemeinde auflösen, Kasse an den Bürgermeister
 * /civ admin tag                   laufenden Tag sofort beenden (Steuern, Einnahmen, Abstimmungen)
 * </pre>
 *
 * Gegründet wird im Spiel normalerweise durch Setzen eines im Amboss benannten Gemeindehauses.
 */
public final class CivCommandTree {

    private static final String NAME = "name";
    private static final String LAW = "regel";
    private static final String VALUE = "wert";
    private static final String AMOUNT = "betrag";
    private static final String COUNT = "anzahl";
    private static final String RECIPIENT = "empfaenger";
    private static final String OFFICE = "amt";
    private static final String CANDIDATE = "kandidat";
    private static final String PLATFORM = "programm";
    private static final String ITEM = "item";
    private static final String TYPE = "art";
    private static final String PRICE = "preis";
    private static final String PLAYER = "spieler";
    private static final List<String> YES_NO = List.of("ja", "nein");

    private final CivServerHost host;

    public CivCommandTree(CivServerHost host) {
        this.host = host;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("civ")
                .executes(context -> asPlayer(context, CivCommands::info))
                .then(Commands.literal("info")
                        .executes(context -> asPlayer(context, CivCommands::info)))
                .then(Commands.literal("gruenden")
                        .requires(AdminPermission.REQUIRED)
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .executes(context -> asPlayer(context,
                                        (commands, caller) -> commands.found(caller, name(context))))))
                .then(Commands.literal("beitreten")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPolityNames)
                                .executes(context -> asPlayer(context,
                                        (commands, caller) -> commands.join(caller, name(context))))))
                .then(Commands.literal("austreten")
                        .executes(context -> asPlayer(context, CivCommands::leave)))
                .then(Commands.literal("beanspruchen")
                        .executes(context -> asPlayer(context,
                                (commands, caller) -> commands.claim(caller, ClaimType.CORE)))
                        .then(Commands.literal("aussenposten")
                                .executes(context -> asPlayer(context,
                                        (commands, caller) -> commands.claim(caller, ClaimType.OUTPOST)))))
                .then(Commands.literal("freigeben")
                        .executes(context -> asPlayer(context, CivCommands::release)))
                .then(Commands.literal("gemeinde")
                        .executes(context -> asPlayer(context,
                                (commands, caller) -> commands.describe(caller, Optional.empty())))
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPolityNames)
                                .executes(context -> asPlayer(context,
                                        (commands, caller) -> commands.describe(caller, Optional.of(name(context)))))))
                .then(Commands.literal("liste")
                        .executes(context -> run(context.getSource(), CivCommands::list)))
                .then(Commands.literal("recht")
                        .executes(context -> asPlayer(context,
                                (commands, caller) -> commands.laws(caller, Optional.empty())))
                        .then(Commands.literal("setzen")
                                .then(Commands.argument(LAW, StringArgumentType.word())
                                        .suggests(this::suggestLaws)
                                        .then(Commands.argument(VALUE, StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(YES_NO, builder))
                                                .executes(context -> asPlayer(context, (commands, caller) -> commands.amendLaw(
                                                        caller, StringArgumentType.getString(context, LAW),
                                                        StringArgumentType.getString(context, VALUE))))))))
                .then(plotCommands())
                .then(Commands.literal("laden")
                        .then(Commands.literal("eroeffnen")
                                .then(Commands.argument(PRICE, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> openShop(civ, player,
                                                StringArgumentType.getString(context, PRICE))))))
                        .then(Commands.literal("schliessen")
                                .executes(context -> withCiv(context, (civ, player) -> ShopChests.lookedAt(player)
                                        .map(pos -> civ.shops().close(Players.caller(player), Players.position(player.level(), pos)))
                                        .orElseGet(() -> Reply.failure(Message.of(MessageKey.SHOP_NO_CHEST))))))
                        .then(Commands.literal("liste")
                                .executes(context -> withCiv(context, (civ, player) -> civ.shops().list(Players.caller(player))))))
                .then(Commands.literal("web")
                        .executes(context -> withCiv(context, (civ, player) -> civ.webLogin(Players.caller(player)))))
                .then(Commands.literal("abstimmungen")
                        .executes(context -> withCiv(context, (civ, player) -> civ.voting().list(Players.caller(player)))))
                .then(Commands.literal("abstimmen")
                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1))
                                .then(Commands.literal("ja")
                                        .executes(context -> withCiv(context, (civ, player) -> civ.voting().vote(
                                                Players.caller(player), IntegerArgumentType.getInteger(context, COUNT), true))))
                                .then(Commands.literal("nein")
                                        .executes(context -> withCiv(context, (civ, player) -> civ.voting().vote(
                                                Players.caller(player), IntegerArgumentType.getInteger(context, COUNT), false))))))
                .then(Commands.literal("stimmung")
                        .executes(context -> withCiv(context, (civ, player) -> civ.villagers().mood(Players.caller(player)))))
                .then(Commands.literal("aemter")
                        .executes(context -> withCiv(context, (civ, player) -> civ.offices().list(Players.caller(player)))))
                .then(Commands.literal("wahl")
                        .then(Commands.literal("ausrufen")
                                .then(Commands.argument(OFFICE, StringArgumentType.word())
                                        .suggests(CivCommandTree::suggestOffices)
                                        .executes(context -> withCiv(context, (civ, player) -> civ.offices().callElection(
                                                Players.caller(player), StringArgumentType.getString(context, OFFICE))))))
                        .then(Commands.literal("kandidieren")
                                .then(Commands.argument(OFFICE, StringArgumentType.word())
                                        .suggests(CivCommandTree::suggestOffices)
                                        .executes(context -> withCiv(context, (civ, player) -> civ.offices().stand(
                                                Players.caller(player), StringArgumentType.getString(context, OFFICE))))
                                        .then(Commands.argument(PLATFORM, StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        OfficeService.platformNames(), builder))
                                                .executes(context -> withCiv(context, (civ, player) -> civ.offices().stand(
                                                        Players.caller(player), StringArgumentType.getString(context, OFFICE),
                                                        StringArgumentType.getString(context, PLATFORM)))))))
                        .then(Commands.literal("stimmen")
                                .then(Commands.argument(OFFICE, StringArgumentType.word())
                                        .suggests(CivCommandTree::suggestOffices)
                                        .then(Commands.argument(CANDIDATE, StringArgumentType.string())
                                                .suggests(this::suggestCandidates)
                                                .executes(context -> withCiv(context, (civ, player) -> civ.offices().vote(
                                                        Players.caller(player), StringArgumentType.getString(context, OFFICE),
                                                        StringArgumentType.getString(context, CANDIDATE))))))))
                .then(Commands.literal("amt")
                        .then(Commands.literal("ernennen")
                                .then(Commands.argument(OFFICE, StringArgumentType.word())
                                        .suggests(CivCommandTree::suggestOffices)
                                        .then(Commands.argument(RECIPIENT, StringArgumentType.string())
                                                .suggests(this::suggestRecipients)
                                                .executes(context -> withCiv(context, (civ, player) -> civ.offices().appoint(
                                                        Players.caller(player), StringArgumentType.getString(context, OFFICE),
                                                        StringArgumentType.getString(context, RECIPIENT)))))))
                        .then(Commands.literal("entlassen")
                                .then(Commands.argument(OFFICE, StringArgumentType.word())
                                        .suggests(CivCommandTree::suggestOffices)
                                        .executes(context -> withCiv(context, (civ, player) -> civ.offices().dismiss(
                                                Players.caller(player), StringArgumentType.getString(context, OFFICE)))))))
                .then(Commands.literal("hilfe")
                        .executes(context -> run(context.getSource(), CivCommands::help)))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> MenuOpener.ownTown(civ, player)
                                ? null : civ.commands().describe(Players.caller(player), Optional.empty()))))
                .then(Commands.literal("karte")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            MenuOpener.map(civ, player);
                            return null;
                        })))
                .then(Commands.literal("grenzen")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            host.showBorders(player);
                            return Reply.success(Message.of(MessageKey.BORDERS_SHOWN));
                        })))
                .then(Commands.literal("konto")
                        .executes(context -> withCiv(context, (civ, player) -> civ.bank().balance(Players.caller(player)))))
                .then(Commands.literal("einzahlen")
                        .executes(context -> withCiv(context, (civ, player) -> deposit(civ, player, Integer.MAX_VALUE)))
                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1))
                                .executes(context -> withCiv(context, (civ, player) ->
                                        deposit(civ, player, IntegerArgumentType.getInteger(context, COUNT))))))
                .then(Commands.literal("abheben")
                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1, 2_304))
                                .executes(context -> withCiv(context, (civ, player) ->
                                        withdraw(civ, player, IntegerArgumentType.getInteger(context, COUNT))))))
                .then(Commands.literal("ueberweisen")
                        .then(Commands.argument(RECIPIENT, StringArgumentType.string())
                                .suggests(this::suggestRecipients)
                                .then(Commands.argument(AMOUNT, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.bank().transfer(
                                                Players.caller(player), StringArgumentType.getString(context, RECIPIENT),
                                                StringArgumentType.getString(context, AMOUNT)))))))
                .then(Commands.literal("kasse")
                        .then(Commands.literal("einzahlen")
                                .then(Commands.argument(AMOUNT, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.bank().payIntoTreasury(
                                                Players.caller(player), StringArgumentType.getString(context, AMOUNT))))))
                        .then(Commands.literal("entnehmen")
                                .then(Commands.argument(AMOUNT, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.bank().takeFromTreasury(
                                                Players.caller(player), StringArgumentType.getString(context, AMOUNT)))))))
                .then(Commands.literal("preis")
                        .executes(context -> withCiv(context, (civ, player) -> inHand(player)
                                .map(stack -> civ.market().priceInfo(Goods.traits(stack)))
                                .orElseGet(CivCommandTree::nothingInHand))))
                .then(Commands.literal("verkaufen")
                        .executes(context -> withCiv(context, (civ, player) -> sellHeld(civ, player, Integer.MAX_VALUE)))
                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1))
                                .executes(context -> withCiv(context, (civ, player) ->
                                        sellHeld(civ, player, IntegerArgumentType.getInteger(context, COUNT))))))
                .then(Commands.literal("kaufen")
                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1, 2_304))
                                .then(Commands.argument(ITEM, StringArgumentType.greedyString())
                                        .suggests(this::suggestOffers)
                                        .executes(context -> withCiv(context, (civ, player) -> buy(civ, player,
                                                StringArgumentType.getString(context, ITEM),
                                                IntegerArgumentType.getInteger(context, COUNT)))))))
                .then(Commands.literal("admin")
                        .requires(AdminPermission.REQUIRED)
                        .then(Commands.literal("umgehen")
                                .executes(context -> asPlayer(context, CivCommands::toggleProtectionBypass)))
                        .then(Commands.literal("tag")
                                .executes(context -> withCiv(context, (civ, player) -> {
                                    Components.broadcast(player.level().getServer(), civ.commands().newDay(civ.endDayNow()));
                                    return null;
                                })))
                        .then(Commands.literal("aufloesen")
                                .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                        .suggests(this::suggestPolityNames)
                                        .executes(context -> withCiv(context, (civ, player) -> {
                                            TownHallService.SeatChange change = civ.townHalls().dissolveByAdmin(name(context));
                                            if (change.reply().success()) {
                                                change.oldSeat().ifPresent(seat ->
                                                        TownHalls.remove(player.level().getServer(), seat, Optional.empty()));
                                            }
                                            return change.reply();
                                        }))))));
    }

    /** Ein Befehl, der einen Spieler als Aufrufer braucht. */
    @FunctionalInterface
    private interface PlayerCommand {

        Reply execute(CivCommands commands, Caller caller);
    }

    private int asPlayer(CommandContext<CommandSourceStack> context, PlayerCommand command) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Caller caller = Players.caller(player);
        return run(context.getSource(), commands -> command.execute(commands, caller));
    }

    private LiteralArgumentBuilder<CommandSourceStack> plotCommands() {
        return Commands.literal("grundstueck")
                .executes(context -> withCiv(context, this::plotInfo))
                .then(Commands.literal("info")
                        .executes(context -> withCiv(context, this::plotInfo)))
                .then(Commands.literal("ecke1")
                        .executes(context -> withCiv(context, (civ, player) -> corner(civ, player, 1))))
                .then(Commands.literal("ecke2")
                        .executes(context -> withCiv(context, (civ, player) -> corner(civ, player, 2))))
                .then(Commands.literal("erstellen")
                        .then(Commands.argument(TYPE, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(PlotService.typeNames(), builder))
                                .executes(context -> withCiv(context, (civ, player) -> civ.plots().createFromSelection(
                                        Players.caller(player), StringArgumentType.getString(context, TYPE))))))
                .then(Commands.literal("chunk")
                        .then(Commands.argument(TYPE, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(PlotService.typeNames(), builder))
                                .executes(context -> withCiv(context, (civ, player) -> civ.plots().createChunk(
                                        Players.caller(player), StringArgumentType.getString(context, TYPE))))))
                .then(Commands.literal("anbieten")
                        .then(Commands.argument(PRICE, StringArgumentType.word())
                                .executes(context -> withCiv(context, (civ, player) -> civ.plots().offer(
                                        Players.caller(player), StringArgumentType.getString(context, PRICE))))))
                .then(Commands.literal("zurueckziehen")
                        .executes(context -> withCiv(context, (civ, player) -> civ.plots().withdrawOffer(Players.caller(player)))))
                .then(Commands.literal("kaufen")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            Reply reply = civ.plots().buyHere(Players.caller(player));
                            if (reply.success()) {
                                Feedback.fanfare(player);
                            }
                            return reply;
                        })))
                .then(Commands.literal("vergeben")
                        .then(Commands.argument(PLAYER, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getOnlinePlayerNames(), builder))
                                .executes(context -> withCiv(context, (civ, player) -> civ.plots().assign(
                                        Players.caller(player), StringArgumentType.getString(context, PLAYER))))))
                .then(Commands.literal("aufloesen")
                        .executes(context -> withCiv(context, (civ, player) -> civ.plots().dissolve(Players.caller(player)))));
    }

    private Reply plotInfo(CivServer civ, ServerPlayer player) {
        civ.plots().plotAt(Players.ref(player), Players.position(player))
                .ifPresent(plot -> host.showArea(player, plot.area()));
        return civ.plots().info(Players.caller(player));
    }

    private Reply corner(CivServer civ, ServerPlayer player, int corner) {
        Reply reply = civ.plots().selectCorner(Players.caller(player), corner);
        civ.plots().selection(Players.ref(player)).area().ifPresent(area -> host.showArea(player, area));
        return reply;
    }

    /** Ein Befehl mit Zugriff auf Spieler und Zivilisation; {@code null} heißt: keine Ausgabe, z. B. weil ein Menü öffnet. */
    @FunctionalInterface
    private interface CivAction {

        Reply execute(CivServer civ, ServerPlayer player);
    }

    private int withCiv(CommandContext<CommandSourceStack> context, CivAction action) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Reply reply;
        try {
            Optional<CivServer> civ = host.current();
            reply = civ.isEmpty() ? Reply.failure(Message.of(MessageKey.INTERNAL_ERROR)) : action.execute(civ.get(), player);
        } catch (RuntimeException e) {
            EddiesCivMod.LOGGER.error("Fehler bei einem /civ-Befehl", e);
            reply = Reply.failure(Message.of(MessageKey.INTERNAL_ERROR));
        }
        if (reply == null) {
            return Command.SINGLE_SUCCESS;
        }
        send(context.getSource(), reply);
        return reply.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static Reply deposit(CivServer civ, ServerPlayer player, int wanted) {
        int taken = Emeralds.take(player, Math.min(wanted, Emeralds.count(player)));
        if (taken == 0) {
            return Reply.failure(Message.of(MessageKey.BANK_NOTHING_TO_DEPOSIT));
        }
        Reply reply = civ.bank().deposit(Players.caller(player), taken);
        if (reply.success()) {
            Feedback.money(player);
        } else {
            Emeralds.give(player, taken);
        }
        return reply;
    }

    private static Reply withdraw(CivServer civ, ServerPlayer player, int amount) {
        Reply reply = civ.bank().withdraw(Players.caller(player), amount);
        if (reply.success()) {
            Emeralds.give(player, amount);
            Feedback.money(player);
        }
        return reply;
    }

    private static Reply openShop(CivServer civ, ServerPlayer player, String price) {
        Optional<ItemStack> held = inHand(player);
        if (held.isEmpty()) {
            return nothingInHand();
        }
        return ShopChests.lookedAt(player)
                .map(pos -> civ.shops().open(Players.caller(player), Players.position(player.level(), pos),
                        Goods.traits(held.get()), price))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.SHOP_NO_CHEST)));
    }

    private static Optional<ItemStack> inHand(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
    }

    private static Reply nothingInHand() {
        return Reply.failure(Message.of(MessageKey.MARKET_NOTHING_IN_HAND));
    }

    private static Reply sellHeld(CivServer civ, ServerPlayer player, int wanted) {
        Optional<ItemStack> held = inHand(player);
        if (held.isEmpty()) {
            return nothingInHand();
        }
        ItemStack stack = held.get();
        int quantity = Math.min(wanted, stack.getCount());
        Reply reply = civ.market().sell(Players.caller(player), Goods.traits(stack), quantity);
        if (reply.success()) {
            stack.shrink(quantity);
            Feedback.money(player);
        }
        return reply;
    }

    private static Reply buy(CivServer civ, ServerPlayer player, String itemId, int quantity) {
        Optional<Item> item = Goods.item(itemId.strip());
        if (item.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.MARKET_NOT_TRADABLE, itemId.strip()));
        }
        Reply reply = civ.market().buy(Players.caller(player), Goods.traits(new ItemStack(item.get())), quantity);
        if (reply.success()) {
            int remaining = quantity;
            while (remaining > 0) {
                int stack = Math.min(remaining, item.get().getDefaultMaxStackSize());
                player.getInventory().placeItemBackInInventory(new ItemStack(item.get(), stack), Prediction.SERVER_ONLY);
                remaining -= stack;
            }
            Feedback.money(player);
        }
        return reply;
    }

    private int run(CommandSourceStack source, Function<CivCommands, Reply> action) {
        Reply reply;
        try {
            reply = host.current()
                    .map(CivServer::commands)
                    .map(action)
                    .orElseGet(() -> Reply.failure(Message.of(MessageKey.INTERNAL_ERROR)));
        } catch (RuntimeException e) {
            EddiesCivMod.LOGGER.error("Fehler bei einem /civ-Befehl", e);
            reply = Reply.failure(Message.of(MessageKey.INTERNAL_ERROR));
        }
        send(source, reply);
        return reply.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static void send(CommandSourceStack source, Reply reply) {
        for (Message line : reply.lines()) {
            MutableComponent text = Components.prefixed(Components.of(line), reply.success());
            if (reply.success()) {
                source.sendSuccess(() -> text, false);
            } else {
                source.sendFailure(text);
            }
        }
    }

    private CompletableFuture<Suggestions> suggestPolityNames(CommandContext<CommandSourceStack> context,
                                                              SuggestionsBuilder builder) {
        List<String> names = host.current()
                .map(civ -> civ.queries().polities().stream().map(polity -> polity.name().value()).toList())
                .orElse(List.of());
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private CompletableFuture<Suggestions> suggestLaws(CommandContext<CommandSourceStack> context,
                                                       SuggestionsBuilder builder) {
        List<String> laws = host.current().map(civ -> civ.commands().lawIds()).orElse(List.of());
        return SharedSuggestionProvider.suggest(laws, builder);
    }

    private CompletableFuture<Suggestions> suggestRecipients(CommandContext<CommandSourceStack> context,
                                                             SuggestionsBuilder builder) {
        List<String> names = new java.util.ArrayList<>();
        host.current().ifPresent(civ -> {
            civ.queries().polities().forEach(polity -> names.add(StringArgumentType.escapeIfRequired(polity.name().value())));
            names.addAll(context.getSource().getOnlinePlayerNames());
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private static CompletableFuture<Suggestions> suggestOffices(CommandContext<CommandSourceStack> context,
                                                                 SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(OfficeService.officeNames(), builder);
    }

    /** Kandidaten der laufenden Wahl für das schon eingegebene Amt. */
    private CompletableFuture<Suggestions> suggestCandidates(CommandContext<CommandSourceStack> context,
                                                             SuggestionsBuilder builder) {
        List<String> names = new java.util.ArrayList<>();
        ServerPlayer player = context.getSource().getPlayer();
        Optional<Office> office = OfficeService.parse(StringArgumentType.getString(context, OFFICE));
        host.current().ifPresent(civ -> {
            if (player == null || office.isEmpty()) {
                return;
            }
            civ.queries().municipalityOf(Players.ref(player))
                    .flatMap(town -> civ.offices().officesOf(town, Players.ref(player)).stream()
                            .filter(view -> view.office() == office.get()).findFirst())
                    .flatMap(OfficeView::election)
                    .ifPresent(election -> election.candidates().forEach(candidate ->
                            names.add(StringArgumentType.escapeIfRequired(candidate.name()))));
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private CompletableFuture<Suggestions> suggestOffers(CommandContext<CommandSourceStack> context,
                                                         SuggestionsBuilder builder) {
        List<String> ids = host.current()
                .map(civ -> civ.market().offers(200).stream().map(offer -> offer.commodity().id()).toList())
                .orElse(List.of());
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static String name(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, NAME);
    }
}
