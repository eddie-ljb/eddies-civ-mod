package de.etiennebader.civ.fabric;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.function.Predicate;

/**
 * Wer als Admin gilt: Operatoren mit Spielleiter-Rechten (Stufe 2), im Einzelspieler bei
 * erlaubten Cheats. Einzige Stelle, die das Berechtigungssystem von Minecraft kennt.
 */
final class AdminPermission {

    /** Für {@code requires(...)} im Befehlsbaum. */
    static final Predicate<CommandSourceStack> REQUIRED = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    private AdminPermission() {
    }
}
