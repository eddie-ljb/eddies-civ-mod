package de.etiennebader.civ.fabric;

import de.etiennebader.civ.fabric.content.Flags;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.etiennebader.civ.EddiesCivMod;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.exchange.Side;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.fabric.content.LawBooks;
import de.etiennebader.civ.fabric.screen.CaseScreen;
import de.etiennebader.civ.fabric.screen.CourtScreen;
import de.etiennebader.civ.domain.justice.Role;
import de.etiennebader.civ.fabric.content.Newspapers;
import de.etiennebader.civ.fabric.gui.Screens;
import de.etiennebader.civ.fabric.screen.ContractsScreen;
import de.etiennebader.civ.fabric.screen.ElectionsScreen;
import de.etiennebader.civ.fabric.screen.ExchangeScreen;
import de.etiennebader.civ.fabric.screen.FriendsScreen;
import de.etiennebader.civ.fabric.screen.LevelScreen;
import de.etiennebader.civ.fabric.screen.MailScreen;
import de.etiennebader.civ.fabric.screen.ParliamentScreen;
import de.etiennebader.civ.fabric.screen.PartiesScreen;
import de.etiennebader.civ.fabric.screen.PressScreen;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.ServerConfig;
import de.etiennebader.civ.server.campaign.CampaignService;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.CivCommands;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.constitution.ConstitutionService;
import de.etiennebader.civ.server.federation.Level;
import de.etiennebader.civ.server.federation.Levels;
import de.etiennebader.civ.server.office.OfficeService;
import de.etiennebader.civ.server.office.OfficeView;
import de.etiennebader.civ.server.party.PartyService;
import de.etiennebader.civ.server.plot.PlotService;
import de.etiennebader.civ.server.town.TownHallService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * /civ firma gruenden &lt;Kapital&gt; &lt;Name&gt;  Firma mit Aktien an der Börse; info, liste, aufnehmen, entlassen,
 *                                   einstellen, dividende, ausgeben, aufloesen
 * /civ boerse [kaufen|verkaufen &lt;Firma&gt; &lt;Anzahl&gt; [Limit] | depot | buch | streichen | menue]
 * /civ zeitung gruenden|redakteur|entlassen|schreiben|veroeffentlichen|lesen|artikel|liste|menue|aufloesen
 * /civ laden eroeffnen &lt;Preis&gt; [Firma]  Laden für sich oder eine geleitete Firma
 * /civ strasse anlegen|aufheben &lt;Name&gt;  Straße aus der Stab-Auswahl anlegen (Bürgermeister, Bauamt)
 * /civ adresse [Spieler]            Anschrift; /civ adressen: Straßen und Hausnummern
 * /civ vertrag …                    Verträge: liste, zeigen, annehmen, ablehnen, kuendigen, miete, arbeit, frei, buendnis
 * /civ post [lesen &lt;Nr&gt;]           Postfach; /civ brief &lt;Spieler&gt; &lt;Betreff&gt; | &lt;Text&gt;
 * /civ freund [entfernen] &lt;Spieler&gt;  Freundschaft anfragen, annehmen, beenden; /civ freunde
 * /civ nachricht &lt;Freund&gt; &lt;Text&gt;   Direktnachricht, auch an Freunde auf der Webseite
 *
 * Nur Admins:
 * /civ gruenden &lt;Name&gt;              Gemeinde ohne Gemeindehaus-Block gründen, Sitz ist der eigene Standort
 * /civ ebenen                      eigene Gemeinde, Land, Nation
 * /civ gemeinde|land|nation &lt;Befehl&gt;  info, menue, aemter, wahl, amt, parlament, recht, abstimmungen, abstimmen,
 *                                   verfassung, gesetze, umfrage, wahlergebnis für genau diese Ebene
 * /civ ebene &lt;Ebene&gt; &lt;Befehl&gt;     dasselbe für jede Ebene, auch eigene aus der Konfiguration
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
    private static final String LEVEL = "ebene";
    private static final String ARTICLE = "artikel";
    private static final String TEXT = "text";
    private static final String TOWN = "gemeinde";
    private static final String NUMBER = "nr";
    private static final String DEPOSIT = "kaution";
    private static final String DAYS = "tage";
    private static final int VILLAGER_REACH = 6;
    private static final String ORDER_AXIS = "ordnung";
    private static final String ECONOMY_AXIS = "wirtschaft";
    private static final List<String> YES_NO = List.of("ja", "nein");

    private final CivServerHost host;

    public CivCommandTree(CivServerHost host) {
        this.host = host;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("civ")
                .requires(AdminPermission.require(AdminPermission.USE, net.minecraft.server.permissions.PermissionLevel.ALL))
                .executes(context -> asPlayer(context, CivCommands::info))
                .then(Commands.literal("info")
                        .executes(context -> asPlayer(context, CivCommands::info)))
                .then(Commands.literal("gruenden")
                        .requires(AdminPermission.require(AdminPermission.FOUND,
                                net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS))
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
                .then(levelCommands(Commands.literal(Polity.MUNICIPAL_LEVEL), context -> Polity.MUNICIPAL_LEVEL)
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
                                                StringArgumentType.getString(context, PRICE), Optional.empty())))
                                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                                .suggests(this::suggestOwnCompanies)
                                                .executes(context -> withCiv(context, (civ, player) -> openShop(civ,
                                                        player, StringArgumentType.getString(context, PRICE),
                                                        Optional.of(name(context))))))))
                        .then(Commands.literal("schliessen")
                                .executes(context -> withCiv(context, (civ, player) -> ShopChests.lookedAt(player)
                                        .map(pos -> civ.shops().close(Players.caller(player), Players.position(player.level(), pos)))
                                        .orElseGet(() -> Reply.failure(Message.of(MessageKey.SHOP_NO_CHEST))))))
                        .then(Commands.literal("liste")
                                .executes(context -> withCiv(context, (civ, player) -> civ.shops().list(Players.caller(player))))))
                .then(Commands.literal("web")
                        .requires(AdminPermission.require(AdminPermission.WEB,
                                net.minecraft.server.permissions.PermissionLevel.ALL))
                        .executes(context -> withCiv(context, (civ, player) -> civ.webLogin(Players.caller(player))))
                        .then(Commands.literal("abmelden")
                                .executes(context -> withCiv(context, (civ, player) -> civ.webLogout(
                                        Players.caller(player))))))
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
                .then(federationCommands())
                .then(constitutionCommands())
                .then(lawBookCommands())
                .then(partyCommands())
                .then(parliamentCommands())
                .then(campaignCommands())
                .then(promiseCommands())
                .then(pollCommands())
                .then(resultCommands())
                .then(companyCommands())
                .then(exchangeCommands())
                .then(pressCommands())
                .then(streetCommands())
                .then(addressCommands())
                .then(Commands.literal("adressen")
                        .executes(context -> withCiv(context, (civ, player) -> civ.civil().listStreets(
                                Players.caller(player)))))
                .then(contractCommands())
                .then(mailCommands())
                .then(letterCommands())
                .then(friendCommands())
                .then(Commands.literal("freunde")
                        .executes(context -> withCiv(context, (civ, player) -> civ.community().showFriends(
                                Players.caller(player))))
                        .then(Commands.literal("menue")
                                .executes(context -> withCiv(context, (civ, player) -> {
                                    Screens.open(player, new FriendsScreen(civ, player, Optional.empty()));
                                    return null;
                                }))))
                .then(Commands.literal("nachricht")
                        .then(Commands.argument(PLAYER, StringArgumentType.word())
                                .suggests(this::suggestFriends)
                                .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.community().direct(
                                                Players.ref(player), Players.name(player),
                                                StringArgumentType.getString(context, PLAYER),
                                                StringArgumentType.getString(context, TEXT)))))))
                .then(Commands.literal("handbuch")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            de.etiennebader.civ.fabric.content.Handbook.give(player);
                            return Reply.success(Message.of(MessageKey.HANDBOOK_GIVEN));
                        })))
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
                        .then(Commands.literal("sicherung")
                                .executes(context -> withCiv(context, (civ, player) -> civ.backupNow()
                                        .map(file -> Reply.success(Message.of(MessageKey.ADMIN_BACKUP_DONE,
                                                file.getFileName().toString())))
                                        .orElseGet(() -> Reply.failure(Message.of(MessageKey.ADMIN_BACKUP_NONE))))))
                        .then(Commands.literal("tag")
                                .executes(context -> withCiv(context, (civ, player) -> {
                                    Components.broadcast(player.level().getServer(), civ.commands().newDay(civ.endDayNow()));
                                    return null;
                                })))
                        .then(courtRoot())
                .then(Commands.literal("ebenen")
                                .executes(context -> withCiv(context, (civ, player) -> civ.federations().listLevels())))
                        .then(Commands.literal("aufloesen")
                                .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                        .suggests(this::suggestPolityNames)
                                        .executes(context -> withCiv(context, (civ, player) -> {
                                            if (civ.federations().federationNames().contains(name(context).strip())) {
                                                return civ.federations().dissolveByAdmin(Players.caller(player), name(context));
                                            }
                                            TownHallService.SeatChange change = civ.townHalls().dissolveByAdmin(name(context));
                                            if (change.reply().success()) {
                                                change.oldSeat().ifPresent(seat ->
                                                        TownHalls.remove(player.level().getServer(), seat, Optional.empty()));
                                            }
                                            return change.reply();
                                        }))))));
        registerLevels(dispatcher, root);
        EnglishCommands.addTo(root);
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

    private LiteralArgumentBuilder<CommandSourceStack> partyCommands() {
        return Commands.literal("partei")
                .executes(context -> withCiv(context, (civ, player) -> civ.parties().info(Players.caller(player),
                        Optional.empty())))
                .then(Commands.literal("liste")
                        .executes(context -> withCiv(context, (civ, player) -> civ.parties().list(
                                Players.caller(player)))))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            Feedback.click(player);
                            Screens.open(player, new PartiesScreen(civ, player, Optional.empty()));
                            return null;
                        })))
                .then(Commands.literal("info")
                        .executes(context -> withCiv(context, (civ, player) -> civ.parties().info(
                                Players.caller(player), Optional.empty())))
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPartyNames)
                                .executes(context -> withCiv(context, (civ, player) -> civ.parties().info(
                                        Players.caller(player), Optional.of(name(context)))))))
                .then(Commands.literal("gruenden")
                        .then(Commands.argument(LEVEL, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        PartyService.directionNames(), builder))
                                .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.parties().found(
                                                Players.caller(player), StringArgumentType.getString(context, LEVEL),
                                                name(context)))))))
                .then(Commands.literal("beitreten")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPartyNames)
                                .executes(context -> withCiv(context, (civ, player) -> civ.parties().join(
                                        Players.caller(player), name(context))))))
                .then(Commands.literal("austreten")
                        .executes(context -> withCiv(context, (civ, player) -> civ.parties().leave(
                                Players.caller(player)))))
                .then(Commands.literal("aufloesen")
                        .executes(context -> withCiv(context, (civ, player) -> civ.parties().dissolve(
                                Players.caller(player)))))
                .then(Commands.literal("wappen")
                        .executes(context -> withCiv(context, (civ, player) -> player.getMainHandItem().isEmpty()
                                ? Reply.failure(Message.of(MessageKey.INVALID_EMBLEM))
                                : civ.parties().setEmblem(Players.caller(player), BuiltInRegistries.ITEM
                                .getKey(player.getMainHandItem().getItem()).toString()))))
                .then(Commands.literal("richtung")
                        .then(Commands.argument(ORDER_AXIS, IntegerArgumentType.integer(-100, 100))
                                .then(Commands.argument(ECONOMY_AXIS, IntegerArgumentType.integer(-100, 100))
                                        .executes(context -> withCiv(context, (civ, player) -> civ.parties()
                                                .setDirection(Players.caller(player),
                                                        IntegerArgumentType.getInteger(context, ORDER_AXIS),
                                                        IntegerArgumentType.getInteger(context, ECONOMY_AXIS)))))));
    }

    private CompletableFuture<Suggestions> suggestPartyNames(CommandContext<CommandSourceStack> context,
                                                             SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(host.current().map(civ -> civ.queries().parties().stream()
                .map(party -> party.name().value()).toList()).orElse(List.of()), builder);
    }

    private LiteralArgumentBuilder<CommandSourceStack> lawBookCommands() {
        return Commands.literal("gesetz")
                .executes(context -> withCiv(context, (civ, player) -> civ.lawBooks().list(Players.caller(player),
                        Optional.empty())))
                .then(Commands.literal("liste")
                        .executes(context -> withCiv(context, (civ, player) -> civ.lawBooks().list(
                                Players.caller(player), Optional.empty())))
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPolityNames)
                                .executes(context -> withCiv(context, (civ, player) -> civ.lawBooks().list(
                                        Players.caller(player), Optional.of(name(context)))))))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            MenuOpener.lawBook(civ, player, Players.position(player));
                            return null;
                        })))
                .then(Commands.literal("zeigen")
                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1))
                                .executes(context -> withCiv(context, (civ, player) -> civ.lawBooks().show(
                                        Players.caller(player), IntegerArgumentType.getInteger(context, COUNT))))))
                .then(Commands.literal("pruefen")
                        .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                .executes(context -> withCiv(context, (civ, player) -> civ.lawBooks().check(
                                        StringArgumentType.getString(context, TEXT))))))
                .then(Commands.literal("erlassen")
                        .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                .executes(context -> withCiv(context, (civ, player) -> enactParagraph(civ, player,
                                        StringArgumentType.getString(context, TEXT))))))
                .then(Commands.literal("aufheben")
                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1))
                                .executes(context -> withCiv(context, (civ, player) -> civ.lawBooks().repeal(
                                        Players.caller(player), IntegerArgumentType.getInteger(context, COUNT))))));
    }

    /** {@code <Titel> | <Text>}; ohne Trennstrich kommt der Text aus dem Buch mit Feder in der Hand. */
    private static Reply enactParagraph(CivServer civ, ServerPlayer player, String input) {
        int bar = input.indexOf('|');
        if (bar >= 0) {
            return civ.lawBooks().enact(Players.caller(player), input.substring(0, bar).strip(),
                    input.substring(bar + 1).strip());
        }
        return LawBooks.textOf(player.getMainHandItem())
                .map(text -> civ.lawBooks().enact(Players.caller(player), input.strip(), text))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.PARAGRAPH_NEEDS_TEXT)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> parliamentCommands() {
        return Commands.literal("parlament")
                .executes(context -> withCiv(context, (civ, player) -> civ.parliaments().info(Players.caller(player),
                        Optional.empty())))
                .then(Commands.literal("zeigen")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPolityNames)
                                .executes(context -> withCiv(context, (civ, player) -> civ.parliaments().info(
                                        Players.caller(player), Optional.of(name(context)))))))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            civ.parliaments().target(Players.caller(player), Optional.empty()).ifPresentOrElse(
                                    polity -> Screens.open(player, new ParliamentScreen(civ, player, polity,
                                            Optional.empty())),
                                    () -> Feedback.reply(player, Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY))));
                            return null;
                        })));
    }

    private LiteralArgumentBuilder<CommandSourceStack> campaignCommands() {
        return Commands.literal("rede")
                .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                        .executes(context -> withCiv(context, (civ, player) -> speak(civ, player,
                                StringArgumentType.getString(context, TEXT)))));
    }

    /** Eine Rede: geht an Spieler in Hörweite, wirkt auf Dorfbewohner in Hörweite. */
    private static Reply speak(CivServer civ, ServerPlayer player, String text) {
        String speech = text.length() > CampaignService.MAX_SPEECH ? text.substring(0, CampaignService.MAX_SPEECH) : text;
        AABB range = player.getBoundingBox().inflate(CampaignService.SPEECH_RANGE);
        List<PersonRef> listeners = player.level().getEntitiesOfClass(Villager.class, range).stream()
                .map(villager -> new PersonRef(new PersonId(villager.getUUID())))
                .toList();
        Component line = Components.of(Message.of(MessageKey.SPEECH_BROADCAST, Players.name(player), speech));
        double maxDistance = (double) CampaignService.SPEECH_RANGE * CampaignService.SPEECH_RANGE;
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other.level() == player.level() && other.distanceToSqr(player) <= maxDistance) {
                other.sendSystemMessage(line);
            }
        }
        return civ.campaigns().speak(Players.caller(player), speech, listeners);
    }

    private LiteralArgumentBuilder<CommandSourceStack> promiseCommands() {
        return Commands.literal("versprechen")
                .executes(context -> withCiv(context, (civ, player) -> civ.campaigns().listPromises(
                        Players.caller(player), Optional.empty())))
                .then(Commands.literal("liste")
                        .executes(context -> withCiv(context, (civ, player) -> civ.campaigns().listPromises(
                                Players.caller(player), Optional.empty())))
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPolityNames)
                                .executes(context -> withCiv(context, (civ, player) -> civ.campaigns().listPromises(
                                        Players.caller(player), Optional.of(name(context)))))))
                .then(Commands.literal("geben")
                        .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                .executes(context -> withCiv(context, (civ, player) -> civ.campaigns().promise(
                                        Players.caller(player), StringArgumentType.getString(context, TEXT))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> pollCommands() {
        return Commands.literal("umfrage")
                .executes(context -> withCiv(context, (civ, player) -> civ.campaigns().poll(Players.caller(player),
                        Optional.empty())))
                .then(Commands.argument(NAME, StringArgumentType.greedyString())
                        .suggests(this::suggestPolityNames)
                        .executes(context -> withCiv(context, (civ, player) -> civ.campaigns().poll(
                                Players.caller(player), Optional.of(name(context))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> resultCommands() {
        return Commands.literal("wahlergebnis")
                .executes(context -> withCiv(context, (civ, player) -> civ.campaigns().lastResult(
                        Players.caller(player), Optional.empty())))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            civ.queries().municipalityOf(Players.ref(player)).ifPresentOrElse(
                                    town -> Screens.open(player, new ElectionsScreen(civ, player, town, Optional.empty())),
                                    () -> Feedback.reply(player, Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY))));
                            return null;
                        })))
                .then(Commands.argument(NAME, StringArgumentType.greedyString())
                        .suggests(this::suggestPolityNames)
                        .executes(context -> withCiv(context, (civ, player) -> civ.campaigns().lastResult(
                                Players.caller(player), Optional.of(name(context))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> constitutionCommands() {
        return Commands.literal("verfassung")
                .executes(context -> withCiv(context, (civ, player) -> civ.constitutions().show(Players.caller(player),
                        Optional.empty())))
                .then(Commands.literal("zeigen")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPolityNames)
                                .executes(context -> withCiv(context, (civ, player) -> civ.constitutions().show(
                                        Players.caller(player), Optional.of(name(context)))))))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            civ.queries().municipalityOf(Players.ref(player)).ifPresentOrElse(
                                    town -> MenuOpener.constitution(civ, player, town.id()),
                                    () -> Feedback.reply(player, Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY))));
                            return null;
                        })))
                .then(Commands.literal("praeambel")
                        .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                .executes(context -> withCiv(context, (civ, player) -> civ.constitutions().setPreamble(
                                        Players.caller(player), StringArgumentType.getString(context, TEXT))))))
                .then(Commands.literal("ausrufen")
                        .executes(context -> withCiv(context, (civ, player) -> civ.constitutions().proclaim(
                                Players.caller(player), Optional.empty())))
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPolityNames)
                                .executes(context -> withCiv(context, (civ, player) -> civ.constitutions().proclaim(
                                        Players.caller(player), Optional.of(name(context)))))))
                .then(Commands.literal("artikel")
                        .then(Commands.argument(ARTICLE, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        ConstitutionService.articleIds(), builder))
                                .then(Commands.argument(VALUE, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.constitutions().amend(
                                                Players.caller(player), StringArgumentType.getString(context, ARTICLE),
                                                StringArgumentType.getString(context, VALUE)))))));
    }

    /**
     * Je Ebene ein eigener Befehl: {@code /civ land …}, {@code /civ nation …} und eigene Ebenen aus der
     * Konfiguration, sofern ihr Name nicht schon belegt ist; jede Ebene außerdem über {@code /civ ebene <Ebene> …}.
     */
    private void registerLevels(CommandDispatcher<CommandSourceStack> dispatcher,
                                LiteralCommandNode<CommandSourceStack> root) {
        for (String id : levelIds()) {
            if (root.getChild(id) == null) {
                dispatcher.register(Commands.literal("civ").then(levelCommands(Commands.literal(id), context -> id)));
            }
        }
        dispatcher.register(Commands.literal("civ")
                .then(Commands.literal("ebenen")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().overview(
                                Players.caller(player)))))
                .then(Commands.literal("debatte")
                        .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                .executes(context -> withCiv(context, (civ, player) -> civ.debates().speak(
                                        Players.caller(player), StringArgumentType.getString(context, TEXT),
                                        System.currentTimeMillis())))))
                .then(Commands.literal("ebene")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().overview(
                                Players.caller(player))))
                        .then(levelCommands(Commands.argument(LEVEL, StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(levelIds(), builder)),
                                context -> StringArgumentType.getString(context, LEVEL)))));
    }

    /** Kennungen aller Ebenen: aus dem laufenden Server, sonst aus der Konfigurationsdatei. */
    private List<String> levelIds() {
        Levels levels = host.current().map(civ -> civ.federations().levels()).orElseGet(CivCommandTree::configuredLevels);
        return levels.all().stream().map(Level::id).toList();
    }

    private static Levels configuredLevels() {
        // Befehle werden vor dem Serverstart registriert; die Datei liegt wie in CivServerHost unter config/.
        Path file = Path.of("config", EddiesCivMod.MOD_ID + ".properties");
        if (!Files.exists(file)) {
            return Levels.DEFAULT;
        }
        try {
            return ServerConfig.load(file).levels();
        } catch (RuntimeException e) {
            return Levels.DEFAULT;
        }
    }

    /** Die Befehle einer Ebene unter {@code node}; {@code level} liefert ihre Kennung. */
    private <T extends ArgumentBuilder<CommandSourceStack, T>> T levelCommands(
            T node, Function<CommandContext<CommandSourceStack>, String> level) {
        return node
                .executes(context -> withCiv(context, (civ, player) -> civ.levels().info(Players.caller(player),
                        level.apply(context))))
                .then(Commands.literal("info")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().info(Players.caller(player),
                                level.apply(context)))))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().with(Players.caller(player),
                                level.apply(context), polity -> {
                                    LevelScreen.open(civ, player, polity, Optional.empty());
                                    return null;
                                }))))
                .then(Commands.literal("aemter")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().offices(
                                Players.caller(player), level.apply(context)))))
                .then(Commands.literal("wahl")
                        .then(Commands.literal("ausrufen")
                                .then(Commands.argument(OFFICE, StringArgumentType.word())
                                        .suggests(CivCommandTree::suggestOffices)
                                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().callElection(
                                                Players.caller(player), level.apply(context),
                                                StringArgumentType.getString(context, OFFICE))))))
                        .then(Commands.literal("kandidieren")
                                .then(Commands.argument(OFFICE, StringArgumentType.word())
                                        .suggests(CivCommandTree::suggestOffices)
                                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().stand(
                                                Players.caller(player), level.apply(context),
                                                StringArgumentType.getString(context, OFFICE), Optional.empty())))
                                        .then(Commands.argument(PLATFORM, StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        OfficeService.platformNames(), builder))
                                                .executes(context -> withCiv(context, (civ, player) -> civ.levels().stand(
                                                        Players.caller(player), level.apply(context),
                                                        StringArgumentType.getString(context, OFFICE),
                                                        Optional.of(StringArgumentType.getString(context, PLATFORM))))))))
                        .then(Commands.literal("stimmen")
                                .then(Commands.argument(OFFICE, StringArgumentType.word())
                                        .suggests(CivCommandTree::suggestOffices)
                                        .then(Commands.argument(CANDIDATE, StringArgumentType.string())
                                                .suggests((context, builder) -> suggestLevelCandidates(context, builder,
                                                        level.apply(context)))
                                                .executes(context -> withCiv(context, (civ, player) -> civ.levels().vote(
                                                        Players.caller(player), level.apply(context),
                                                        StringArgumentType.getString(context, OFFICE),
                                                        StringArgumentType.getString(context, CANDIDATE))))))))
                .then(Commands.literal("amt")
                        .then(Commands.literal("ernennen")
                                .then(Commands.argument(OFFICE, StringArgumentType.word())
                                        .suggests(CivCommandTree::suggestOffices)
                                        .then(Commands.argument(RECIPIENT, StringArgumentType.string())
                                                .suggests(this::suggestRecipients)
                                                .executes(context -> withCiv(context, (civ, player) -> civ.levels().appoint(
                                                        Players.caller(player), level.apply(context),
                                                        StringArgumentType.getString(context, OFFICE),
                                                        StringArgumentType.getString(context, RECIPIENT)))))))
                        .then(Commands.literal("entlassen")
                                .then(Commands.argument(OFFICE, StringArgumentType.word())
                                        .suggests(CivCommandTree::suggestOffices)
                                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().dismiss(
                                                Players.caller(player), level.apply(context),
                                                StringArgumentType.getString(context, OFFICE)))))))
                .then(Commands.literal("parlament")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().parliament(
                                Players.caller(player), level.apply(context)))))
                .then(Commands.literal("recht")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().laws(
                                Players.caller(player), level.apply(context))))
                        .then(Commands.literal("setzen")
                                .then(Commands.argument(LAW, StringArgumentType.word())
                                        .suggests(this::suggestLaws)
                                        .then(Commands.argument(VALUE, StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(YES_NO, builder))
                                                .executes(context -> withCiv(context, (civ, player) -> civ.levels().amendLaw(
                                                        Players.caller(player), level.apply(context),
                                                        StringArgumentType.getString(context, LAW),
                                                        StringArgumentType.getString(context, VALUE))))))))
                .then(Commands.literal("abstimmungen")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().proposals(
                                Players.caller(player), level.apply(context)))))
                .then(Commands.literal("abstimmen")
                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1))
                                .then(Commands.literal("ja")
                                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().voteOnProposal(
                                                Players.caller(player), level.apply(context),
                                                IntegerArgumentType.getInteger(context, COUNT), true))))
                                .then(Commands.literal("nein")
                                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().voteOnProposal(
                                                Players.caller(player), level.apply(context),
                                                IntegerArgumentType.getInteger(context, COUNT), false))))))
                .then(Commands.literal("verfassung")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().constitution(
                                Players.caller(player), level.apply(context))))
                        .then(Commands.literal("ausrufen")
                                .executes(context -> withCiv(context, (civ, player) -> civ.levels().proclaim(
                                        Players.caller(player), level.apply(context))))))
                .then(Commands.literal("gesetze")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().lawBook(
                                Players.caller(player), level.apply(context)))))
                .then(Commands.literal("umfrage")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().poll(
                                Players.caller(player), level.apply(context)))))
                .then(Commands.literal("wahlergebnis")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().lastResult(
                                Players.caller(player), level.apply(context)))))
                .then(Commands.literal("wahlkreis")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().constituencies(
                                Players.caller(player), level.apply(context))))
                        .then(Commands.literal("liste")
                                .executes(context -> withCiv(context, (civ, player) -> civ.levels().constituencies(
                                        Players.caller(player), level.apply(context)))))
                        .then(constituencyAction("anlegen", level))
                        .then(constituencyAction("gebiet", level))
                        .then(constituencyAction("gebiet-entfernen", level))
                        .then(constituencyAction("leeren", level))
                        .then(constituencyAction("aufloesen", level))
                        .then(Commands.literal("gemeinde")
                                .then(Commands.argument(NAME, StringArgumentType.string())
                                        .then(Commands.argument(TOWN, StringArgumentType.greedyString())
                                                .executes(context -> withCiv(context, (civ, player) ->
                                                        civ.levels().constituencyTown(Players.caller(player),
                                                                level.apply(context),
                                                                StringArgumentType.getString(context, NAME),
                                                                StringArgumentType.getString(context, TOWN)))))))
                        .then(Commands.literal("gemeinde-entfernen")
                                .then(Commands.argument(NAME, StringArgumentType.string())
                                        .then(Commands.argument(TOWN, StringArgumentType.greedyString())
                                                .executes(context -> withCiv(context, (civ, player) ->
                                                        civ.levels().constituencyTownRemove(Players.caller(player),
                                                                level.apply(context),
                                                                StringArgumentType.getString(context, NAME),
                                                                StringArgumentType.getString(context, TOWN))))))))
                .then(Commands.literal("debatte")
                        .then(Commands.literal("starten")
                                .executes(context -> withCiv(context, (civ, player) -> civ.levels().startDebate(
                                        Players.caller(player), level.apply(context), Optional.empty())))
                                .then(Commands.argument(OFFICE, StringArgumentType.word())
                                        .suggests(CivCommandTree::suggestOffices)
                                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().startDebate(
                                                Players.caller(player), level.apply(context),
                                                Optional.of(StringArgumentType.getString(context, OFFICE))))))))
                .then(Commands.literal("flagge")
                        .executes(context -> withCiv(context, (civ, player) -> giveFlag(civ, player,
                                level.apply(context))))
                        .then(Commands.literal("setzen")
                                .executes(context -> withCiv(context, (civ, player) -> {
                                    Optional<de.etiennebader.civ.domain.civic.Flag> flag = Flags.of(player.getMainHandItem());
                                    if (flag.isEmpty()) {
                                        return Reply.failure(Message.of(MessageKey.FLAG_NEEDS_BANNER));
                                    }
                                    return civ.levels().setFlag(Players.caller(player), level.apply(context), flag);
                                })))
                        .then(Commands.literal("holen")
                                .executes(context -> withCiv(context, (civ, player) -> giveFlag(civ, player,
                                        level.apply(context)))))
                        .then(Commands.literal("entfernen")
                                .executes(context -> withCiv(context, (civ, player) -> civ.levels().setFlag(
                                        Players.caller(player), level.apply(context), Optional.empty())))))
                .then(Commands.literal("hymne")
                        .executes(context -> withCiv(context, (civ, player) -> playAnthem(civ, player,
                                level.apply(context))))
                        .then(Commands.literal("spielen")
                                .executes(context -> withCiv(context, (civ, player) -> playAnthem(civ, player,
                                        level.apply(context)))))
                        .then(Commands.literal("setzen")
                                .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().setAnthem(
                                                Players.caller(player), level.apply(context),
                                                StringArgumentType.getString(context, TEXT))))))
                        .then(Commands.literal("entfernen")
                                .executes(context -> withCiv(context, (civ, player) -> civ.levels().removeAnthem(
                                        Players.caller(player), level.apply(context))))))
                .then(courtOf(level));
    }

    // ---- Gerichte ----

    /** {@code /civ <Ebene> gericht …}: Gericht einer Ebene, Klagen dort, Personal. */
    private LiteralArgumentBuilder<CommandSourceStack> courtOf(Function<CommandContext<CommandSourceStack>, String> level) {
        return Commands.literal("gericht")
                .executes(context -> withCiv(context, (civ, player) -> civ.levels().with(Players.caller(player),
                        level.apply(context), polity -> civ.justice().court(Players.caller(player), polity))))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().with(Players.caller(player),
                                level.apply(context), polity -> {
                                    Screens.open(player, new CourtScreen(civ, player, Optional.of(polity), Optional.empty()));
                                    return null;
                                }))))
                .then(Commands.literal("klage")
                        .then(Commands.argument(TYPE, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("straf", "zivil", "verfassung"), builder))
                                .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().with(
                                                Players.caller(player), level.apply(context), polity -> civ.justice().file(
                                                        Players.caller(player), polity,
                                                        StringArgumentType.getString(context, TYPE),
                                                        StringArgumentType.getString(context, TEXT))))))))
                .then(courtStaff("richter", Role.JUDGE, level))
                .then(courtStaff("staatsanwalt", Role.PROSECUTOR, level));
    }

    private LiteralArgumentBuilder<CommandSourceStack> courtStaff(
            String literal, Role role, Function<CommandContext<CommandSourceStack>, String> level) {
        return Commands.literal(literal)
                .then(courtStaffAction("ernennen", true, role, level))
                .then(courtStaffAction("entlassen", false, role, level));
    }

    private LiteralArgumentBuilder<CommandSourceStack> courtStaffAction(
            String literal, boolean appointed, Role role, Function<CommandContext<CommandSourceStack>, String> level) {
        return Commands.literal(literal)
                .then(Commands.argument(PLAYER, StringArgumentType.word())
                        .suggests(CivCommandTree::suggestPeople)
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().with(Players.caller(player),
                                level.apply(context), polity -> civ.justice().appoint(Players.caller(player), polity,
                                        role, StringArgumentType.getString(context, PLAYER), villagerRef(player),
                                        appointed)))));
    }

    /** {@code /civ gericht …}: Verfahren nach Nummer, gilt für alle Ebenen. */
    private LiteralArgumentBuilder<CommandSourceStack> courtRoot() {
        return Commands.literal("gericht")
                .executes(context -> withCiv(context, (civ, player) -> {
                    Screens.open(player, new CourtScreen(civ, player, civ.federations().levels()
                            .find(Polity.MUNICIPAL_LEVEL)
                            .flatMap(municipal -> civ.levels().polityOf(Players.ref(player), municipal)),
                            Optional.empty()));
                    return null;
                }))
                .then(Commands.literal("faelle")
                        .executes(context -> withCiv(context, (civ, player) -> civ.justice().mine(Players.caller(player)))))
                .then(Commands.literal("klage")
                        .then(Commands.argument(TYPE, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("straf", "zivil", "verfassung"), builder))
                                .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().with(
                                                Players.caller(player), Polity.MUNICIPAL_LEVEL, polity -> civ.justice()
                                                        .file(Players.caller(player), polity,
                                                                StringArgumentType.getString(context, TYPE),
                                                                StringArgumentType.getString(context, TEXT))))))))
                .then(caseCommand("fall", (civ, player, number, context) -> {
                    Screens.open(player, new CaseScreen(civ, player, number, Optional.empty()));
                    return null;
                }))
                .then(caseCommand("akte", (civ, player, number, context) -> civ.justice().show(Players.caller(player),
                        number)))
                .then(caseCommand("eroeffnen", (civ, player, number, context) -> civ.justice().open(
                        Players.caller(player), number)))
                .then(caseCommand("weiter", (civ, player, number, context) -> civ.justice().advance(
                        Players.caller(player), number)))
                .then(caseCommand("berufung", (civ, player, number, context) -> civ.justice().appeal(
                        Players.caller(player), number)))
                .then(caseCommand("beweis", (civ, player, number, context) -> handEvidence(civ, player, number))
                        .then(Commands.argument(NUMBER, IntegerArgumentType.integer(1))
                                .then(Commands.literal("vertrag")
                                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1))
                                                .executes(context -> withCiv(context, (civ, player) -> civ.justice()
                                                        .contractEvidence(Players.caller(player),
                                                                IntegerArgumentType.getInteger(context, NUMBER),
                                                                IntegerArgumentType.getInteger(context, COUNT))))))
                                .then(Commands.literal("artikel")
                                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1))
                                                .executes(context -> withCiv(context, (civ, player) -> civ.justice()
                                                        .articleEvidence(Players.caller(player),
                                                                IntegerArgumentType.getInteger(context, NUMBER),
                                                                IntegerArgumentType.getInteger(context, COUNT))))))
                                .then(Commands.literal("text")
                                        .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                                .executes(context -> withCiv(context, (civ, player) -> civ.justice()
                                                        .evidence(Players.caller(player),
                                                                IntegerArgumentType.getInteger(context, NUMBER), "text",
                                                                shorten(StringArgumentType.getString(context, TEXT)),
                                                                StringArgumentType.getString(context, TEXT))))))))
                .then(caseTextCommand("einstellen", (civ, player, number, text) -> civ.justice().dismiss(
                        Players.caller(player), number, text), true))
                .then(caseTextCommand("aussage", (civ, player, number, text) -> civ.justice().testify(
                        Players.caller(player), number, text), false))
                .then(caseTextCommand("plaedoyer", (civ, player, number, text) -> civ.justice().plead(
                        Players.caller(player), number, text), false))
                .then(caseTextCommand("urteil", (civ, player, number, text) -> civ.justice().decide(
                        Players.caller(player), number, text), false))
                .then(caseTextCommand("verteidiger", (civ, player, number, text) -> civ.justice().defense(
                        Players.caller(player), number, text, villagerRef(player)), false))
                .then(caseTextCommand("zeuge", (civ, player, number, text) -> civ.justice().summon(
                        Players.caller(player), number, text, villagerRef(player)), false))
                .then(Commands.literal("stimme")
                        .then(Commands.argument(NUMBER, IntegerArgumentType.integer(1))
                                .then(Commands.literal("schuldig")
                                        .executes(context -> withCiv(context, (civ, player) -> civ.justice().vote(
                                                Players.caller(player), IntegerArgumentType.getInteger(context, NUMBER),
                                                true))))
                                .then(Commands.literal("unschuldig")
                                        .executes(context -> withCiv(context, (civ, player) -> civ.justice().vote(
                                                Players.caller(player), IntegerArgumentType.getInteger(context, NUMBER),
                                                false))))));
    }

    @FunctionalInterface
    private interface CaseAction {

        Reply execute(CivServer civ, ServerPlayer player, int number, CommandContext<CommandSourceStack> context);
    }

    @FunctionalInterface
    private interface CaseTextAction {

        Reply execute(CivServer civ, ServerPlayer player, int number, String text);
    }

    private LiteralArgumentBuilder<CommandSourceStack> caseCommand(String literal, CaseAction action) {
        return Commands.literal(literal)
                .then(Commands.argument(NUMBER, IntegerArgumentType.integer(1))
                        .executes(context -> withCiv(context, (civ, player) -> action.execute(civ, player,
                                IntegerArgumentType.getInteger(context, NUMBER), context))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> caseTextCommand(String literal, CaseTextAction action,
                                                                       boolean optional) {
        var number = Commands.argument(NUMBER, IntegerArgumentType.integer(1))
                .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                        .executes(context -> withCiv(context, (civ, player) -> action.execute(civ, player,
                                IntegerArgumentType.getInteger(context, NUMBER),
                                StringArgumentType.getString(context, TEXT)))));
        if (optional) {
            number = number.executes(context -> withCiv(context, (civ, player) -> action.execute(civ, player,
                    IntegerArgumentType.getInteger(context, NUMBER), "")));
        }
        return Commands.literal(literal).then(number);
    }

    /** Holt Waren aus dem Firmenlager ins Inventar; was nicht passt, fällt vor die Füße. */
    private static Reply pickup(CivServer civ, ServerPlayer player, String company, String item, int quantity) {
        String id = item.contains(":") ? item : "minecraft:" + item;
        Optional<net.minecraft.world.item.Item> type = Optional.ofNullable(net.minecraft.resources.Identifier.tryParse(id))
                .flatMap(net.minecraft.core.registries.BuiltInRegistries.ITEM::getOptional);
        if (type.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CASE_UNKNOWN_EVIDENCE, item));
        }
        de.etiennebader.civ.server.company.CompanyService.Pickup pickup = civ.companies().take(Players.caller(player),
                company, id, quantity);
        long remaining = pickup.quantity();
        while (remaining > 0) {
            int stack = (int) Math.min(remaining, type.get().getDefaultMaxStackSize());
            player.getInventory().placeItemBackInInventory(new ItemStack(type.get(), stack),
                    net.minecraft.util.Prediction.SERVER_ONLY);
            remaining -= stack;
        }
        return pickup.reply();
    }

    /** Beweis aus der Hand: Buch mit Feder, signiertes Buch oder ein beliebiger Gegenstand. */
    public static Reply handEvidence(CivServer civ, ServerPlayer player, int number) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.CASE_UNKNOWN_EVIDENCE, "-"));
        }
        Optional<LawBooks.Draft> draft = LawBooks.draftOf(stack);
        if (draft.isPresent()) {
            return civ.justice().evidence(Players.caller(player), number, "buch", draft.get().title(), draft.get().text());
        }
        net.minecraft.world.item.component.WrittenBookContent book = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (book != null) {
            String text = book.getPages(false).stream().map(net.minecraft.network.chat.Component::getString)
                    .collect(java.util.stream.Collectors.joining("\n"));
            return civ.justice().evidence(Players.caller(player), number, "buch", book.title().raw(),
                    text.isBlank() ? book.title().raw() : text);
        }
        String name = stack.getHoverName().getString();
        return civ.justice().evidence(Players.caller(player), number, "gegenstand", shorten(stack.getCount() + "× " + name),
                BuiltInRegistries.ITEM.getKey(stack.getItem()) + " ×" + stack.getCount());
    }

    private static String shorten(String text) {
        return text.length() <= 60 ? text : text.substring(0, 59) + "…";
    }

    /** Der Dorfbewohner, den der Spieler ansieht, sonst der nächste in Reichweite. */
    private static Optional<PersonRef> villagerRef(ServerPlayer player) {
        return nearestVillager(player).map(PersonRef.class::cast);
    }

    private static CompletableFuture<Suggestions> suggestPeople(CommandContext<CommandSourceStack> context,
                                                                SuggestionsBuilder builder) {
        List<String> names = new java.util.ArrayList<>(context.getSource().getOnlinePlayerNames());
        names.add("dorfbewohner");
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private LiteralArgumentBuilder<CommandSourceStack> constituencyAction(
            String action, Function<CommandContext<CommandSourceStack>, String> level) {
        return Commands.literal(action)
                .then(Commands.argument(NAME, StringArgumentType.greedyString())
                        .executes(context -> withCiv(context, (civ, player) -> civ.levels().constituency(
                                Players.caller(player), level.apply(context), action,
                                StringArgumentType.getString(context, NAME)))));
    }

    /** Gibt dem Spieler ein Banner mit der Flagge der Ebene. */
    private static Reply giveFlag(CivServer civ, ServerPlayer player, String levelId) {
        return civ.levels().with(Players.caller(player), levelId, polity -> civ.civic().flagOf(polity.id())
                .map(flag -> {
                    ItemStack banner = Flags.banner(flag, player.level().registryAccess());
                    player.getInventory().placeItemBackInInventory(banner, Prediction.SERVER_ONLY);
                    return Reply.success(Message.of(MessageKey.FLAG_GIVEN, polity.name().value()));
                })
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.FLAG_NONE, polity.name().value()))));
    }

    /** Spielt die Hymne der Ebene für den Spieler ab. */
    private static Reply playAnthem(CivServer civ, ServerPlayer player, String levelId) {
        return civ.levels().with(Players.caller(player), levelId, polity -> civ.civic().anthemOf(polity.id())
                .map(anthem -> {
                    Ambience.play(player, anthem);
                    return Reply.success(Message.of(MessageKey.ANTHEM_PLAYING, polity.name().value(), anthem.title()));
                })
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.ANTHEM_NONE, polity.name().value()))));
    }

    /** Kandidaten der laufenden Wahl auf der angegebenen Ebene. */
    private CompletableFuture<Suggestions> suggestLevelCandidates(CommandContext<CommandSourceStack> context,
                                                                  SuggestionsBuilder builder, String levelId) {
        List<String> names = new java.util.ArrayList<>();
        ServerPlayer player = context.getSource().getPlayer();
        Optional<Office> office = OfficeService.parse(StringArgumentType.getString(context, OFFICE));
        host.current().ifPresent(civ -> {
            if (player == null || office.isEmpty()) {
                return;
            }
            civ.federations().levels().find(levelId)
                    .flatMap(level -> civ.levels().polityOf(Players.ref(player), level))
                    .flatMap(polity -> civ.offices().officesOf(polity, Players.ref(player)).stream()
                            .filter(view -> view.office() == office.get()).findFirst())
                    .flatMap(OfficeView::election)
                    .ifPresent(election -> election.candidates().forEach(candidate ->
                            names.add(StringArgumentType.escapeIfRequired(candidate.name()))));
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private LiteralArgumentBuilder<CommandSourceStack> federationCommands() {
        return Commands.literal("bund")
                .executes(context -> withCiv(context, (civ, player) -> civ.federations().info(Players.caller(player),
                        Optional.empty())))
                .then(Commands.literal("gruenden")
                        .then(Commands.argument(LEVEL, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(host.current()
                                        .map(civ -> civ.federations().levelIds()).orElse(List.of()), builder))
                                .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.federations().found(
                                                Players.caller(player), StringArgumentType.getString(context, LEVEL),
                                                name(context)))))))
                .then(Commands.literal("einladen")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPolityNames)
                                .executes(context -> withCiv(context, (civ, player) -> civ.federations().invite(
                                        Players.caller(player), name(context))))))
                .then(Commands.literal("beitreten")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestFederationNames)
                                .executes(context -> withCiv(context, (civ, player) -> civ.federations().join(
                                        Players.caller(player), name(context))))))
                .then(Commands.literal("austreten")
                        .executes(context -> withCiv(context, (civ, player) -> civ.federations().leave(
                                Players.caller(player)))))
                .then(Commands.literal("aufloesen")
                        .executes(context -> withCiv(context, (civ, player) -> civ.federations().dissolve(
                                Players.caller(player)))))
                .then(Commands.literal("info")
                        .executes(context -> withCiv(context, (civ, player) -> civ.federations().info(
                                Players.caller(player), Optional.empty())))
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPolityNames)
                                .executes(context -> withCiv(context, (civ, player) -> civ.federations().info(
                                        Players.caller(player), Optional.of(name(context)))))));
    }

    private CompletableFuture<Suggestions> suggestFederationNames(CommandContext<CommandSourceStack> context,
                                                                  SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(host.current().map(civ -> civ.federations().federationNames())
                .orElse(List.of()), builder);
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

    private static Reply openShop(CivServer civ, ServerPlayer player, String price, Optional<String> companyName) {
        Optional<ItemStack> held = inHand(player);
        if (held.isEmpty()) {
            return nothingInHand();
        }
        ActorRef owner = Players.ref(player);
        if (companyName.isPresent()) {
            Optional<PolityView> company = civ.companies().company(companyName.get())
                    .filter(found -> found.holds(Players.ref(player), Office.MAYOR));
            if (company.isEmpty()) {
                return Reply.failure(Message.of(MessageKey.COMPANY_UNKNOWN, companyName.get()));
            }
            owner = company.get().asActor();
        }
        ActorRef shopOwner = owner;
        return ShopChests.lookedAt(player)
                .map(pos -> civ.shops().open(Players.caller(player), Players.position(player.level(), pos),
                        Goods.traits(held.get()), price, shopOwner))
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

    private LiteralArgumentBuilder<CommandSourceStack> streetCommands() {
        return Commands.literal("strasse")
                .executes(context -> withCiv(context, (civ, player) -> civ.civil().listStreets(Players.caller(player))))
                .then(Commands.literal("liste")
                        .executes(context -> withCiv(context, (civ, player) -> civ.civil().listStreets(
                                Players.caller(player)))))
                .then(Commands.literal("anlegen")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .executes(context -> withCiv(context, (civ, player) -> civ.civil().layStreet(
                                        Players.caller(player), name(context))))))
                .then(Commands.literal("aufheben")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestStreets)
                                .executes(context -> withCiv(context, (civ, player) -> civ.civil().removeStreet(
                                        Players.caller(player), name(context))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> addressCommands() {
        return Commands.literal("adresse")
                .executes(context -> withCiv(context, (civ, player) -> civ.civil().address(Players.caller(player),
                        Optional.empty())))
                .then(Commands.argument(PLAYER, StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                context.getSource().getOnlinePlayerNames(), builder))
                        .executes(context -> withCiv(context, (civ, player) -> civ.civil().address(
                                Players.caller(player), Optional.of(StringArgumentType.getString(context, PLAYER))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> contractCommands() {
        return Commands.literal("vertrag")
                .executes(context -> withCiv(context, (civ, player) -> civ.civil().list(Players.caller(player))))
                .then(Commands.literal("liste")
                        .executes(context -> withCiv(context, (civ, player) -> civ.civil().list(Players.caller(player)))))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            Screens.open(player, new ContractsScreen(civ, player, Optional.empty()));
                            return null;
                        })))
                .then(contractAction("zeigen", (civ, player, number) -> civ.civil().show(Players.caller(player), number)))
                .then(contractAction("annehmen", (civ, player, number) -> civ.civil().sign(Players.caller(player), number)))
                .then(contractAction("ablehnen", (civ, player, number) -> civ.civil().decline(Players.caller(player),
                        number)))
                .then(contractAction("kuendigen", (civ, player, number) -> civ.civil().giveNotice(
                        Players.caller(player), number)))
                .then(Commands.literal("miete")
                        .then(Commands.argument(PLAYER, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getOnlinePlayerNames(), builder))
                                .then(Commands.argument(PRICE, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.civil().offerLease(
                                                Players.caller(player), StringArgumentType.getString(context, PLAYER),
                                                StringArgumentType.getString(context, PRICE), Optional.empty(), 0)))
                                        .then(Commands.argument(DEPOSIT, StringArgumentType.word())
                                                .executes(context -> withCiv(context, (civ, player) -> civ.civil()
                                                        .offerLease(Players.caller(player),
                                                                StringArgumentType.getString(context, PLAYER),
                                                                StringArgumentType.getString(context, PRICE),
                                                                Optional.of(StringArgumentType.getString(context, DEPOSIT)),
                                                                0)))
                                                .then(Commands.argument(DAYS, IntegerArgumentType.integer(0, 3650))
                                                        .executes(context -> withCiv(context, (civ, player) -> civ
                                                                .civil().offerLease(Players.caller(player),
                                                                        StringArgumentType.getString(context, PLAYER),
                                                                        StringArgumentType.getString(context, PRICE),
                                                                        Optional.of(StringArgumentType.getString(
                                                                                context, DEPOSIT)),
                                                                        IntegerArgumentType.getInteger(context,
                                                                                DAYS)))))))))
                .then(Commands.literal("arbeit")
                        .then(Commands.literal("dorfbewohner")
                                .then(Commands.argument(PRICE, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> hireVillager(civ, player,
                                                StringArgumentType.getString(context, PRICE))))))
                        .then(Commands.argument(PLAYER, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getOnlinePlayerNames(), builder))
                                .then(Commands.argument(PRICE, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.civil()
                                                .offerEmployment(Players.caller(player),
                                                        StringArgumentType.getString(context, PLAYER),
                                                        StringArgumentType.getString(context, PRICE), 0)))
                                        .then(Commands.argument(DAYS, IntegerArgumentType.integer(0, 3650))
                                                .executes(context -> withCiv(context, (civ, player) -> civ.civil()
                                                        .offerEmployment(Players.caller(player),
                                                                StringArgumentType.getString(context, PLAYER),
                                                                StringArgumentType.getString(context, PRICE),
                                                                IntegerArgumentType.getInteger(context, DAYS))))))))
                .then(Commands.literal("frei")
                        .then(Commands.argument(RECIPIENT, StringArgumentType.string())
                                .suggests(this::suggestRecipients)
                                .then(Commands.argument(AMOUNT, StringArgumentType.word())
                                        .then(Commands.argument(PRICE, StringArgumentType.word())
                                                .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                                        .executes(context -> withCiv(context, (civ, player) -> civ
                                                                .civil().offerCustom(Players.caller(player),
                                                                        StringArgumentType.getString(context, RECIPIENT),
                                                                        StringArgumentType.getString(context, AMOUNT),
                                                                        StringArgumentType.getString(context, PRICE),
                                                                        StringArgumentType.getString(context, TEXT),
                                                                        0))))))))
                .then(Commands.literal("buendnis")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPolityNames)
                                .executes(context -> withCiv(context, (civ, player) -> civ.civil().offerAlliance(
                                        Players.caller(player), name(context),
                                        civ.queries().municipalityOf(Players.ref(player)))))));
    }

    private interface ContractAction {
        Reply execute(CivServer civ, ServerPlayer player, int number);
    }

    private LiteralArgumentBuilder<CommandSourceStack> contractAction(String literal, ContractAction action) {
        return Commands.literal(literal)
                .then(Commands.argument(NUMBER, IntegerArgumentType.integer(1))
                        .executes(context -> withCiv(context, (civ, player) -> action.execute(civ, player,
                                IntegerArgumentType.getInteger(context, NUMBER)))));
    }

    /** Stellt den nächsten Dorfbewohner in Reichweite ein. */
    private static Reply hireVillager(CivServer civ, ServerPlayer player, String wage) {
        AABB range = player.getBoundingBox().inflate(VILLAGER_REACH);
        return player.level().getEntitiesOfClass(Villager.class, range).stream()
                .min(java.util.Comparator.comparingDouble(villager -> villager.distanceToSqr(player)))
                .map(villager -> civ.civil().offerEmployment(Players.caller(player),
                        new PersonRef(new PersonId(villager.getUUID())), wage, 0))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.VILLAGER_NONE_NEAR)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> mailCommands() {
        return Commands.literal("post")
                .executes(context -> withCiv(context, (civ, player) -> civ.community().showInbox(Players.caller(player))))
                .then(Commands.literal("lesen")
                        .then(Commands.argument(NUMBER, IntegerArgumentType.integer(1))
                                .executes(context -> withCiv(context, (civ, player) -> civ.community().readLetter(
                                        Players.caller(player), IntegerArgumentType.getInteger(context, NUMBER))))))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            Screens.open(player, new MailScreen(civ, player));
                            return null;
                        })));
    }

    private LiteralArgumentBuilder<CommandSourceStack> letterCommands() {
        return Commands.literal("brief")
                .then(Commands.argument(PLAYER, StringArgumentType.word())
                        .suggests(this::suggestFriends)
                        .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                .executes(context -> withCiv(context, (civ, player) -> civ.community().sendLetter(
                                        Players.caller(player), StringArgumentType.getString(context, PLAYER),
                                        StringArgumentType.getString(context, TEXT))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> friendCommands() {
        return Commands.literal("freund")
                .then(Commands.literal("annehmen")
                        .then(Commands.argument(PLAYER, StringArgumentType.word())
                                .suggests(this::suggestIncoming)
                                .executes(context -> withCiv(context, (civ, player) -> civ.community().befriend(
                                        Players.ref(player), Players.name(player),
                                        StringArgumentType.getString(context, PLAYER))))))
                .then(Commands.literal("ablehnen")
                        .then(Commands.argument(PLAYER, StringArgumentType.word())
                                .suggests(this::suggestIncoming)
                                .executes(context -> withCiv(context, (civ, player) -> civ.community().decline(
                                        Players.ref(player), StringArgumentType.getString(context, PLAYER))))))
                .then(Commands.literal("vertrauen")
                        .then(Commands.argument(PLAYER, StringArgumentType.word())
                                .suggests(this::suggestFriends)
                                .executes(context -> withCiv(context, (civ, player) -> civ.community().trust(
                                        Players.ref(player), StringArgumentType.getString(context, PLAYER), true)))))
                .then(Commands.literal("misstrauen")
                        .then(Commands.argument(PLAYER, StringArgumentType.word())
                                .suggests(this::suggestFriends)
                                .executes(context -> withCiv(context, (civ, player) -> civ.community().trust(
                                        Players.ref(player), StringArgumentType.getString(context, PLAYER), false)))))
                .then(Commands.literal("entfernen")
                        .then(Commands.argument(PLAYER, StringArgumentType.word())
                                .suggests(this::suggestFriends)
                                .executes(context -> withCiv(context, (civ, player) -> civ.community().unfriend(
                                        Players.ref(player), StringArgumentType.getString(context, PLAYER))))))
                .then(Commands.argument(PLAYER, StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                context.getSource().getOnlinePlayerNames(), builder))
                        .executes(context -> withCiv(context, (civ, player) -> civ.community().befriend(
                                Players.ref(player), Players.name(player),
                                StringArgumentType.getString(context, PLAYER)))));
    }

    private CompletableFuture<Suggestions> suggestIncoming(CommandContext<CommandSourceStack> context,
                                                           SuggestionsBuilder builder) {
        List<String> names = new java.util.ArrayList<>();
        ServerPlayer player = context.getSource().getPlayer();
        host.current().ifPresent(civ -> {
            if (player != null) {
                civ.community().friends(Players.ref(player)).stream()
                        .filter(friend -> friend.state().equals("incoming"))
                        .forEach(friend -> names.add(friend.name()));
            }
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private CompletableFuture<Suggestions> suggestFriends(CommandContext<CommandSourceStack> context,
                                                          SuggestionsBuilder builder) {
        List<String> names = new java.util.ArrayList<>();
        ServerPlayer player = context.getSource().getPlayer();
        host.current().ifPresent(civ -> {
            if (player != null) {
                civ.community().friends(Players.ref(player)).stream()
                        .filter(friend -> friend.state().equals("friend"))
                        .forEach(friend -> names.add(friend.name()));
            }
        });
        if (names.isEmpty()) {
            names.addAll(context.getSource().getOnlinePlayerNames());
        }
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private CompletableFuture<Suggestions> suggestStreets(CommandContext<CommandSourceStack> context,
                                                          SuggestionsBuilder builder) {
        List<String> names = new java.util.ArrayList<>();
        ServerPlayer player = context.getSource().getPlayer();
        host.current().ifPresent(civ -> {
            if (player != null) {
                civ.queries().municipalityOf(Players.ref(player))
                        .ifPresent(town -> town.streets().forEach(street -> names.add(street.name())));
            }
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private static final String COMPANY = "firma";
    private static final String PAPER = "zeitung";
    private static final String LIMIT = "limit";

    private LiteralArgumentBuilder<CommandSourceStack> companyCommands() {
        return Commands.literal("firma")
                .executes(context -> withCiv(context, (civ, player) -> civ.companies().info(Players.caller(player),
                        Optional.empty())))
                .then(Commands.literal("liste")
                        .executes(context -> withCiv(context, (civ, player) -> civ.companies().list())))
                .then(Commands.literal("info")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestCompanies)
                                .executes(context -> withCiv(context, (civ, player) -> civ.companies().info(
                                        Players.caller(player), Optional.of(name(context)))))))
                .then(Commands.literal("gruenden")
                        .then(Commands.argument(AMOUNT, StringArgumentType.word())
                                .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.companies().found(
                                                Players.caller(player), StringArgumentType.getString(context, AMOUNT),
                                                name(context)))))))
                .then(companyMemberCommand("aufnehmen", true))
                .then(companyMemberCommand("entlassen", false))
                .then(Commands.literal("einstellen")
                        .then(Commands.argument(COMPANY, StringArgumentType.string())
                                .suggests(this::suggestOwnCompanies)
                                .then(Commands.literal("dorfbewohner")
                                        .then(Commands.argument(PRICE, StringArgumentType.word())
                                                .executes(context -> withCiv(context, (civ, player) -> nearestVillager(
                                                        player).map(villager -> civ.companies().hire(
                                                                Players.caller(player),
                                                                StringArgumentType.getString(context, COMPANY), villager,
                                                                StringArgumentType.getString(context, PRICE)))
                                                        .orElseGet(() -> Reply.failure(Message.of(
                                                                MessageKey.VILLAGER_NONE_NEAR)))))))
                                .then(Commands.argument(PLAYER, StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                context.getSource().getOnlinePlayerNames(), builder))
                                        .then(Commands.argument(PRICE, StringArgumentType.word())
                                                .executes(context -> withCiv(context, (civ, player) -> civ.companies()
                                                        .hire(Players.caller(player),
                                                                StringArgumentType.getString(context, COMPANY),
                                                                StringArgumentType.getString(context, PLAYER),
                                                                StringArgumentType.getString(context, PRICE))))))))
                .then(Commands.literal("menue")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestCompanies)
                                .executes(context -> withCiv(context, (civ, player) -> {
                                    Screens.open(player, new de.etiennebader.civ.fabric.screen.CompanyScreen(civ, player,
                                            name(context).replace("\"", ""), Optional.empty()));
                                    return null;
                                }))))
                .then(Commands.literal("branche")
                        .then(Commands.argument(COMPANY, StringArgumentType.string())
                                .suggests(this::suggestOwnCompanies)
                                .then(Commands.argument(TYPE, StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                de.etiennebader.civ.domain.production.Branch.ids(), builder))
                                        .executes(context -> withCiv(context, (civ, player) -> civ.companies()
                                                .chooseBranch(Players.caller(player),
                                                        StringArgumentType.getString(context, COMPANY),
                                                        StringArgumentType.getString(context, TYPE)))))))
                .then(Commands.literal("investieren")
                        .then(Commands.argument(COMPANY, StringArgumentType.string())
                                .suggests(this::suggestOwnCompanies)
                                .then(Commands.argument(AMOUNT, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.companies()
                                                .invest(Players.caller(player),
                                                        StringArgumentType.getString(context, COMPANY),
                                                        StringArgumentType.getString(context, AMOUNT)))))))
                .then(Commands.literal("verkauf")
                        .then(Commands.argument(COMPANY, StringArgumentType.string())
                                .suggests(this::suggestOwnCompanies)
                                .then(Commands.argument(TYPE, StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("automatisch", "hand", "jetzt"), builder))
                                        .executes(context -> withCiv(context, (civ, player) -> civ.companies()
                                                .sell(Players.caller(player),
                                                        StringArgumentType.getString(context, COMPANY),
                                                        StringArgumentType.getString(context, TYPE)))))))
                .then(Commands.literal("lager")
                        .then(Commands.argument(COMPANY, StringArgumentType.string())
                                .suggests(this::suggestCompanies)
                                .executes(context -> withCiv(context, (civ, player) -> civ.companies()
                                        .stock(Players.caller(player), StringArgumentType.getString(context, COMPANY))))))
                .then(Commands.literal("abholen")
                        .then(Commands.argument(COMPANY, StringArgumentType.string())
                                .suggests(this::suggestOwnCompanies)
                                .then(Commands.argument(ITEM, StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(host.current()
                                                .map(civ -> civ.companies().stockItems(
                                                        StringArgumentType.getString(context, COMPANY)))
                                                .orElse(List.of()), builder))
                                        .executes(context -> withCiv(context, (civ, player) -> pickup(civ, player,
                                                StringArgumentType.getString(context, COMPANY),
                                                StringArgumentType.getString(context, ITEM), 64)))
                                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1, 64 * 36))
                                                .executes(context -> withCiv(context, (civ, player) -> pickup(civ, player,
                                                        StringArgumentType.getString(context, COMPANY),
                                                        StringArgumentType.getString(context, ITEM),
                                                        IntegerArgumentType.getInteger(context, COUNT))))))))
                .then(Commands.literal("kuendigen")
                        .then(Commands.argument(COMPANY, StringArgumentType.string())
                                .suggests(this::suggestOwnCompanies)
                                .then(Commands.literal("dorfbewohner")
                                        .executes(context -> withCiv(context, (civ, player) -> nearestVillager(player)
                                                .map(villager -> civ.companies().fire(Players.caller(player),
                                                        StringArgumentType.getString(context, COMPANY),
                                                        villager))
                                                .orElseGet(() -> Reply.failure(Message.of(
                                                        MessageKey.VILLAGER_NONE_NEAR))))))
                                .then(Commands.argument(PLAYER, StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                context.getSource().getOnlinePlayerNames(), builder))
                                        .executes(context -> withCiv(context, (civ, player) -> civ.companies()
                                                .fire(Players.caller(player),
                                                        StringArgumentType.getString(context, COMPANY),
                                                        StringArgumentType.getString(context, PLAYER)))))))
                .then(Commands.literal("dividende")
                        .then(Commands.argument(COMPANY, StringArgumentType.string())
                                .suggests(this::suggestOwnCompanies)
                                .then(Commands.argument(AMOUNT, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.companies()
                                                .dividend(Players.caller(player),
                                                        StringArgumentType.getString(context, COMPANY),
                                                        StringArgumentType.getString(context, AMOUNT)))))))
                .then(Commands.literal("ausgeben")
                        .then(Commands.argument(COMPANY, StringArgumentType.string())
                                .suggests(this::suggestOwnCompanies)
                                .then(Commands.argument(COUNT, IntegerArgumentType.integer(1, 1_000_000))
                                        .then(Commands.argument(PRICE, StringArgumentType.word())
                                                .executes(context -> withCiv(context, (civ, player) -> civ.companies()
                                                        .issue(Players.caller(player),
                                                                StringArgumentType.getString(context, COMPANY),
                                                                IntegerArgumentType.getInteger(context, COUNT),
                                                                StringArgumentType.getString(context, PRICE))))))))
                .then(Commands.literal("aufloesen")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestOwnCompanies)
                                .executes(context -> withCiv(context, (civ, player) -> civ.companies().dissolve(
                                        Players.caller(player), name(context))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> companyMemberCommand(String literal, boolean admit) {
        return Commands.literal(literal)
                .then(Commands.argument(COMPANY, StringArgumentType.string())
                        .suggests(this::suggestOwnCompanies)
                        .then(Commands.argument(PLAYER, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getOnlinePlayerNames(), builder))
                                .executes(context -> withCiv(context, (civ, player) -> admit
                                        ? civ.companies().admit(Players.caller(player),
                                        StringArgumentType.getString(context, COMPANY),
                                        StringArgumentType.getString(context, PLAYER))
                                        : civ.companies().dismiss(Players.caller(player),
                                        StringArgumentType.getString(context, COMPANY),
                                        StringArgumentType.getString(context, PLAYER))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> exchangeCommands() {
        return Commands.literal("boerse")
                .executes(context -> withCiv(context, (civ, player) -> civ.companies().list()))
                .then(Commands.literal("liste")
                        .executes(context -> withCiv(context, (civ, player) -> civ.companies().list())))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            Screens.open(player, new ExchangeScreen(civ, player, Optional.empty()));
                            return null;
                        })))
                .then(tradeCommand("kaufen", Side.BUY))
                .then(tradeCommand("verkaufen", Side.SELL))
                .then(Commands.literal("depot")
                        .executes(context -> withCiv(context, (civ, player) -> civ.companies().depot(
                                Players.caller(player)))))
                .then(Commands.literal("buch")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestCompanies)
                                .executes(context -> withCiv(context, (civ, player) -> civ.companies().book(
                                        name(context))))))
                .then(Commands.literal("streichen")
                        .then(Commands.argument(NUMBER, IntegerArgumentType.integer(1))
                                .executes(context -> withCiv(context, (civ, player) -> civ.companies().cancel(
                                        Players.caller(player), IntegerArgumentType.getInteger(context, NUMBER))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> tradeCommand(String literal, Side side) {
        return Commands.literal(literal)
                .then(Commands.argument(COMPANY, StringArgumentType.string())
                        .suggests(this::suggestCompanies)
                        .then(Commands.argument(COUNT, IntegerArgumentType.integer(1, 1_000_000))
                                .executes(context -> withCiv(context, (civ, player) -> civ.companies().trade(
                                        Players.caller(player), side, StringArgumentType.getString(context, COMPANY),
                                        IntegerArgumentType.getInteger(context, COUNT), Optional.empty())))
                                .then(Commands.argument(LIMIT, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.companies().trade(
                                                Players.caller(player), side,
                                                StringArgumentType.getString(context, COMPANY),
                                                IntegerArgumentType.getInteger(context, COUNT),
                                                Optional.of(StringArgumentType.getString(context, LIMIT))))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> pressCommands() {
        return Commands.literal("zeitung")
                .executes(context -> withCiv(context, (civ, player) -> civ.press().list()))
                .then(Commands.literal("liste")
                        .executes(context -> withCiv(context, (civ, player) -> civ.press().list())))
                .then(Commands.literal("menue")
                        .executes(context -> withCiv(context, (civ, player) -> {
                            Screens.open(player, new PressScreen(civ, player, Optional.empty()));
                            return null;
                        })))
                .then(Commands.literal("gruenden")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .executes(context -> withCiv(context, (civ, player) -> civ.press().found(
                                        Players.caller(player), name(context))))))
                .then(Commands.literal("redakteur")
                        .then(Commands.argument(PAPER, StringArgumentType.string())
                                .suggests(this::suggestOwnPapers)
                                .then(Commands.literal("dorfbewohner")
                                        .executes(context -> withCiv(context, (civ, player) -> nearestVillager(player)
                                                .map(villager -> civ.press().addEditor(Players.caller(player),
                                                        StringArgumentType.getString(context, PAPER), villager))
                                                .orElseGet(() -> Reply.failure(Message.of(
                                                        MessageKey.VILLAGER_NONE_NEAR))))))
                                .then(Commands.argument(PLAYER, StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                context.getSource().getOnlinePlayerNames(), builder))
                                        .executes(context -> withCiv(context, (civ, player) -> civ.press().addEditor(
                                                Players.caller(player), StringArgumentType.getString(context, PAPER),
                                                StringArgumentType.getString(context, PLAYER)))))))
                .then(Commands.literal("entlassen")
                        .then(Commands.argument(PAPER, StringArgumentType.string())
                                .suggests(this::suggestOwnPapers)
                                .then(Commands.argument(PLAYER, StringArgumentType.word())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.press().removeEditor(
                                                Players.caller(player), StringArgumentType.getString(context, PAPER),
                                                StringArgumentType.getString(context, PLAYER)))))))
                .then(Commands.literal("schreiben")
                        .then(Commands.argument(PAPER, StringArgumentType.string())
                                .suggests(this::suggestMyPapers)
                                .then(Commands.argument(TEXT, StringArgumentType.greedyString())
                                        .executes(context -> withCiv(context, (civ, player) -> civ.press().write(
                                                Players.caller(player), StringArgumentType.getString(context, PAPER),
                                                StringArgumentType.getString(context, TEXT)))))))
                .then(Commands.literal("veroeffentlichen")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestMyPapers)
                                .executes(context -> withCiv(context, (civ, player) -> publishBook(civ, player,
                                        name(context))))))
                .then(Commands.literal("lesen")
                        .executes(context -> withCiv(context, (civ, player) -> giveEdition(civ, player,
                                Optional.empty())))
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestPapers)
                                .executes(context -> withCiv(context, (civ, player) -> giveEdition(civ, player,
                                        Optional.of(name(context)))))))
                .then(Commands.literal("artikel")
                        .then(Commands.argument(NUMBER, IntegerArgumentType.integer(1))
                                .executes(context -> withCiv(context, (civ, player) -> civ.press().show(
                                        IntegerArgumentType.getInteger(context, NUMBER))))))
                .then(Commands.literal("aufloesen")
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .suggests(this::suggestOwnPapers)
                                .executes(context -> withCiv(context, (civ, player) -> civ.press().dissolve(
                                        Players.caller(player), name(context))))));
    }

    /** Veröffentlicht das Buch mit Feder in der Hand; die erste Zeile ist der Titel. */
    private static Reply publishBook(CivServer civ, ServerPlayer player, String paper) {
        Optional<LawBooks.Draft> draft = LawBooks.draftOf(player.getMainHandItem());
        if (draft.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.ARTICLE_FROM_BOOK));
        }
        String title = draft.get().title();
        String body = draft.get().text().equals(title) ? "" : draft.get().text();
        if (body.startsWith(title)) {
            body = body.substring(title.length()).strip();
        }
        Reply reply = civ.press().publish(Players.ref(player), paper, title, body);
        if (reply.success()) {
            player.getMainHandItem().shrink(1);
        }
        return reply;
    }

    private static Reply giveEdition(CivServer civ, ServerPlayer player, Optional<String> paper) {
        return civ.press().edition(paper).map(edition -> {
            player.getInventory().placeItemBackInInventory(Newspapers.edition(edition), Prediction.SERVER_ONLY);
            return Reply.success(Message.of(MessageKey.EDITION_GIVEN));
        }).orElseGet(() -> Reply.failure(Message.of(MessageKey.EDITION_NONE)));
    }

    /** Der nächste Dorfbewohner in Reichweite. */
    private static Optional<ActorRef> nearestVillager(ServerPlayer player) {
        AABB range = player.getBoundingBox().inflate(VILLAGER_REACH);
        return player.level().getEntitiesOfClass(Villager.class, range).stream()
                .min(java.util.Comparator.comparingDouble(villager -> villager.distanceToSqr(player)))
                .map(villager -> new PersonRef(new PersonId(villager.getUUID())));
    }

    private CompletableFuture<Suggestions> suggestCompanies(CommandContext<CommandSourceStack> context,
                                                            SuggestionsBuilder builder) {
        List<String> names = new java.util.ArrayList<>();
        host.current().ifPresent(civ -> civ.queries().companies().forEach(c -> names.add(quoted(c.name().value()))));
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private CompletableFuture<Suggestions> suggestOwnCompanies(CommandContext<CommandSourceStack> context,
                                                               SuggestionsBuilder builder) {
        List<String> names = new java.util.ArrayList<>();
        ServerPlayer player = context.getSource().getPlayer();
        host.current().ifPresent(civ -> {
            if (player != null) {
                civ.companies().led(Players.ref(player)).forEach(c -> names.add(quoted(c.name().value())));
            }
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private CompletableFuture<Suggestions> suggestPapers(CommandContext<CommandSourceStack> context,
                                                         SuggestionsBuilder builder) {
        List<String> names = new java.util.ArrayList<>();
        host.current().ifPresent(civ -> civ.queries().newspapers().forEach(p -> names.add(quoted(p.name().value()))));
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private CompletableFuture<Suggestions> suggestOwnPapers(CommandContext<CommandSourceStack> context,
                                                            SuggestionsBuilder builder) {
        List<String> names = new java.util.ArrayList<>();
        ServerPlayer player = context.getSource().getPlayer();
        host.current().ifPresent(civ -> {
            if (player != null) {
                civ.press().led(Players.ref(player)).forEach(p -> names.add(quoted(p.name().value())));
            }
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private CompletableFuture<Suggestions> suggestMyPapers(CommandContext<CommandSourceStack> context,
                                                           SuggestionsBuilder builder) {
        List<String> names = new java.util.ArrayList<>();
        ServerPlayer player = context.getSource().getPlayer();
        host.current().ifPresent(civ -> {
            if (player != null) {
                civ.press().papersOf(Players.ref(player)).forEach(p -> names.add(quoted(p.name().value())));
            }
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    /** Namen mit Leerzeichen für {@link StringArgumentType#string()} in Anführungszeichen. */
    private static String quoted(String name) {
        return name.contains(" ") ? "\"" + name + "\"" : name;
    }

    private static String name(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, NAME);
    }
}
