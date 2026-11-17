package de.etiennebader.civ.fabric;

import de.etiennebader.civ.EddiesCivMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.function.Predicate;

/**
 * Berechtigungen des Mods über die Permission-API von Fabric. Rechte-Mods wie LuckPerms vergeben die Knoten; ohne
 * Rechte-Mod gilt die Operator-Stufe von Minecraft.
 *
 * <table>
 *   <caption>Knoten</caption>
 *   <tr><td>{@code eddies-civ-mod:use}</td><td>alle {@code /civ}-Befehle (Standard: alle Spieler)</td></tr>
 *   <tr><td>{@code eddies-civ-mod:web}</td><td>Anmeldung an der Webseite (Standard: alle)</td></tr>
 *   <tr><td>{@code eddies-civ-mod:found}</td><td>{@code /civ gruenden} ohne Gemeindehaus (Standard: Spielleiter)</td></tr>
 *   <tr><td>{@code eddies-civ-mod:admin}</td><td>{@code /civ admin …} (Standard: Spielleiter, Stufe 2)</td></tr>
 * </table>
 */
public final class AdminPermission {

    public static final Identifier USE = EddiesCivMod.id("use");
    public static final Identifier WEB = EddiesCivMod.id("web");
    public static final Identifier FOUND = EddiesCivMod.id("found");
    public static final Identifier ADMIN = EddiesCivMod.id("admin");

    /** Für {@code requires(...)} im Befehlsbaum: Admin-Befehle. */
    static final Predicate<CommandSourceStack> REQUIRED = require(ADMIN, PermissionLevel.GAMEMASTERS);

    private AdminPermission() {
    }

    /** Prüft einen Knoten; ohne Rechte-Mod entscheidet die Stufe. */
    public static Predicate<CommandSourceStack> require(Identifier node, PermissionLevel fallback) {
        return source -> source.checkPermission(node, fallback);
    }
}
