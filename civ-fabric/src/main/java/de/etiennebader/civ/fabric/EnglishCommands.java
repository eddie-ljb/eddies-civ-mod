package de.etiennebader.civ.fabric;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;

import java.util.Map;

/**
 * Englische Befehlsnamen: Unter {@code /civ} bekommt jeder deutsche Befehl einen Zwilling mit englischen Wörtern,
 * z. B. {@code /civ court case 3} neben {@code /civ gericht fall 3}. Argumentnamen und Ausführung bleiben dieselben.
 */
final class EnglishCommands {

    /** Deutsches Wort → englisches Wort; mit {@code eltern.wort} für Wörter, die je nach Befehl anders heißen. */
    static final Map<String, String> WORDS = Map.ofEntries(
            Map.entry("abheben", "withdraw"), Map.entry("abholen", "collect"), Map.entry("abbrechen", "cancel"), Map.entry("ablehnen", "decline"),
            Map.entry("abmelden", "logout"), Map.entry("abstimmen", "vote"), Map.entry("abstimmungen", "votes"),
            Map.entry("adresse", "address"), Map.entry("adressen", "addresses"), Map.entry("aemter", "offices"),
            Map.entry("akte", "file"), Map.entry("amt", "office"), Map.entry("anbieten", "offer"),
            Map.entry("anlegen", "create"), Map.entry("annehmen", "accept"), Map.entry("arbeit", "work"),
            Map.entry("artikel", "article"), Map.entry("aufheben", "repeal"), Map.entry("aufloesen", "dissolve"),
            Map.entry("aufnehmen", "admit"), Map.entry("planung", "planning"), Map.entry("kultur", "culture"), Map.entry("aus", "off"), Map.entry("vorschau", "preview"), Map.entry("ausgeben", "issue"), Map.entry("ausrufen", "call"),
            Map.entry("aussage", "testify"), Map.entry("aussenposten", "outpost"), Map.entry("austreten", "leave"),
            Map.entry("beanspruchen", "claim"), Map.entry("beeinflussen", "influence"),
            Map.entry("bau", "build"), Map.entry("beauftragen", "commission"), Map.entry("beitreten", "join"), Map.entry("berufung", "appeal"),
            Map.entry("beweis", "evidence"), Map.entry("beute", "spoils"), Map.entry("boerse", "exchange"), Map.entry("branche", "branch"),
            Map.entry("brief", "letter"), Map.entry("buch", "book"), Map.entry("buendnis", "alliance"),
            Map.entry("bund", "federation"), Map.entry("debatte", "debate"), Map.entry("depot", "portfolio"),
            Map.entry("dividende", "dividend"), Map.entry("dorfbewohner", "villager"), Map.entry("ebene", "level"),
            Map.entry("ebenen", "levels"), Map.entry("einladen", "invite"), Map.entry("einstellen", "hire"),
            Map.entry("gericht.einstellen", "dismiss"), Map.entry("einzahlen", "deposit"),
            Map.entry("entfernen", "remove"), Map.entry("entlassen", "release"), Map.entry("entnehmen", "take"),
            Map.entry("erlassen", "enact"), Map.entry("ernennen", "appoint"), Map.entry("eroeffnen", "open"),
            Map.entry("erstellen", "make"), Map.entry("faelle", "cases"), Map.entry("genehmigen", "approve"), Map.entry("plaene", "plans"), Map.entry("fall", "case"),
            Map.entry("firma", "company"), Map.entry("flagge", "flag"), Map.entry("frei", "custom"),
            Map.entry("freigeben", "free"), Map.entry("freund", "friend"), Map.entry("freunde", "friends"),
            Map.entry("gebiet", "area"), Map.entry("gebiet-entfernen", "area-remove"),
            Map.entry("geben", "give"), Map.entry("gemeinde", "town"),
            Map.entry("gemeinde-entfernen", "town-remove"),
            Map.entry("gericht", "court"), Map.entry("gesetz", "statute"), Map.entry("gesetze", "statutes"),
            Map.entry("grenzen", "borders"), Map.entry("gruenden", "found"), Map.entry("grundstueck", "plot"),
            Map.entry("heim", "home"), Map.entry("hilfe", "help"), Map.entry("handbuch", "handbook"), Map.entry("holen", "get"), Map.entry("hymne", "anthem"),
            Map.entry("investieren", "invest"), Map.entry("ja", "yes"), Map.entry("kandidieren", "run"),
            Map.entry("karte", "map"), Map.entry("kasse", "treasury"), Map.entry("kaufen", "buy"),
            Map.entry("klage", "sue"), Map.entry("konjunktur", "conjuncture"), Map.entry("konto", "account"),
            Map.entry("kuendigen", "terminate"),
            Map.entry("laden", "shop"), Map.entry("lager", "stock"), Map.entry("land", "state"),
            Map.entry("leeren", "clear"), Map.entry("lesen", "read"), Map.entry("liste", "list"),
            Map.entry("material", "material"), Map.entry("menue", "menu"), Map.entry("miete", "lease"), Map.entry("misstrauen", "distrust"),
            Map.entry("nachricht", "message"), Map.entry("nein", "no"), Map.entry("parlament", "parliament"),
            Map.entry("partei", "party"), Map.entry("plaedoyer", "plead"), Map.entry("post", "mail"),
            Map.entry("programmodus", "programmode"),
            Map.entry("praeambel", "preamble"), Map.entry("preis", "price"), Map.entry("pruefen", "check"),
            Map.entry("recht", "rules"), Map.entry("redakteur", "editor"), Map.entry("rede", "speech"),
            Map.entry("reden", "talk"),
            Map.entry("richter", "judge"), Map.entry("richtung", "direction"), Map.entry("schliessen", "close"),
            Map.entry("schreiben", "write"), Map.entry("schuldig", "guilty"),
            Map.entry("sicherung", "backup"), Map.entry("spielen", "play"), Map.entry("staatsanwalt", "prosecutor"),
            Map.entry("starten", "start"), Map.entry("stimme", "vote"), Map.entry("stimmen", "vote"),
            Map.entry("stimmung", "mood"), Map.entry("strasse", "street"), Map.entry("streichen", "strike"),
            Map.entry("tag", "day"), Map.entry("thema", "topic"),
            Map.entry("ueberweisen", "transfer"), Map.entry("umfrage", "poll"),
            Map.entry("umgehen", "bypass"), Map.entry("unschuldig", "innocent"), Map.entry("urteil", "verdict"),
            Map.entry("verfassung", "constitution"), Map.entry("vergeben", "assign"), Map.entry("verkauf", "selling"),
            Map.entry("verkaufen", "sell"), Map.entry("veroeffentlichen", "publish"), Map.entry("versprechen", "promise"),
            Map.entry("verteidiger", "defense"), Map.entry("vertrag", "contract"), Map.entry("vertrauen", "trust"),
            Map.entry("waehrung", "currency"), Map.entry("kredit", "loan"), Map.entry("zins", "rate"),
            Map.entry("leitzins", "baserate"), Map.entry("vorsitz", "chair"), Map.entry("umrechnen", "convert"),
            Map.entry("zurueckzahlen", "repay"), Map.entry("kurs", "exchangerate"),
            Map.entry("selbstbedienung", "selfservice"), Map.entry("als", "as"),
            Map.entry("wahl", "election"), Map.entry("wahlergebnis", "results"), Map.entry("wahlkreis", "district"),
            Map.entry("wappen", "crest"), Map.entry("zugang", "access"), Map.entry("weiter", "advance"), Map.entry("zeigen", "show"),
            Map.entry("zeitung", "newspaper"), Map.entry("bauen", "construct"), Map.entry("plakat", "poster"), Map.entry("reklame", "billboard"), Map.entry("schrift", "scripture"), Map.entry("wege", "roads"),
            Map.entry("segnen", "bless"), Map.entry("taufe", "baptize"), Map.entry("anhaengen", "append"),
            Map.entry("benennen", "rename"), Map.entry("festlegen", "define"), Map.entry("abschaffen", "abolish"),
            Map.entry("entziehen", "revoke"),
            Map.entry("schaufel", "shovel"), Map.entry("pflaster", "paving"), Map.entry("familie", "family"), Map.entry("kinder", "children"), Map.entry("zeuge", "witness"), Map.entry("zurueckziehen", "retract"));

    private EnglishCommands() {
    }

    /** Hängt an {@code root} für jeden deutschen Unterbefehl einen englischen Zwilling an. */
    static void addTo(LiteralCommandNode<CommandSourceStack> root) {
        for (CommandNode<CommandSourceStack> child : java.util.List.copyOf(root.getChildren())) {
            if (child instanceof LiteralCommandNode<CommandSourceStack> literal) {
                String english = translate(root.getName(), literal.getLiteral());
                if (!english.equals(literal.getLiteral()) && root.getChild(english) == null) {
                    root.addChild(copy(root.getName(), child).build());
                }
            }
        }
    }

    static String translate(String parent, String word) {
        return WORDS.getOrDefault(parent + "." + word, WORDS.getOrDefault(word, word));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentBuilder<CommandSourceStack, ?> copy(String parent, CommandNode<CommandSourceStack> node) {
        ArgumentBuilder<CommandSourceStack, ?> builder;
        String name;
        if (node instanceof LiteralCommandNode<CommandSourceStack> literal) {
            name = literal.getLiteral();
            builder = LiteralArgumentBuilder.literal(translate(parent, name));
        } else if (node instanceof ArgumentCommandNode argument) {
            name = argument.getName();
            RequiredArgumentBuilder required = RequiredArgumentBuilder.argument(argument.getName(), argument.getType());
            if (argument.getCustomSuggestions() != null) {
                required.suggests(argument.getCustomSuggestions());
            }
            builder = required;
        } else {
            throw new IllegalArgumentException("Unbekannter Knoten: " + node);
        }
        builder.requires(node.getRequirement());
        if (node.getCommand() != null) {
            builder.executes(node.getCommand());
        }
        if (node.getRedirect() != null) {
            builder.forward(node.getRedirect(), node.getRedirectModifier(), node.isFork());
        } else {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (CommandNode<CommandSourceStack> child : node.getChildren()) {
                String childName = child instanceof LiteralCommandNode<CommandSourceStack> literal
                        ? translate(name, literal.getLiteral()) : child.getName();
                if (seen.add(childName)) {
                    builder.then(copy(name, child));
                }
            }
        }
        return builder;
    }
}
