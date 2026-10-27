package de.etiennebader.civ.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.etiennebader.civ.EddiesCivMod;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.CivCommands;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

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
 *
 * Nur Admins:
 * /civ gruenden &lt;Name&gt;              Gemeinde ohne Gemeindehaus-Block gründen, Sitz ist der eigene Standort
 * /civ admin umgehen                Gebietsschutz für sich selbst aus- und wieder einschalten
 * </pre>
 *
 * Gegründet wird im Spiel normalerweise durch Setzen eines im Amboss benannten Gemeindehauses.
 */
public final class CivCommandTree {

    private static final String NAME = "name";
    private static final String LAW = "regel";
    private static final String VALUE = "wert";
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
                .then(Commands.literal("admin")
                        .requires(AdminPermission.REQUIRED)
                        .then(Commands.literal("umgehen")
                                .executes(context -> asPlayer(context, CivCommands::toggleProtectionBypass)))));
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
            MutableComponent text = Components.of(line);
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

    private static String name(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, NAME);
    }
}
