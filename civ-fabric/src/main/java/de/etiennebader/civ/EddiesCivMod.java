package de.etiennebader.civ;

import de.etiennebader.civ.fabric.CivCommandTree;
import de.etiennebader.civ.fabric.CivServerHost;
import de.etiennebader.civ.fabric.ProtectionListener;
import de.etiennebader.civ.fabric.TownHallPlacement;
import de.etiennebader.civ.fabric.content.CivContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Einstieg des Mods auf Server- und Client-Seite: verbindet die Zivilisation mit den
 * Lebenszyklus-Ereignissen des Servers, registriert das Gemeindehaus, den Gebietsschutz und
 * die {@code /civ}-Befehle.
 */
public class EddiesCivMod implements ModInitializer {

    public static final String MOD_ID = "eddies-civ-mod";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CivServerHost host = new CivServerHost();
        host.register();
        CivContent content = CivContent.register(new TownHallPlacement(host));
        new ProtectionListener(host, content.townHall()).register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                new CivCommandTree(host).register(dispatcher));
        LOGGER.info("Eddies Civ Mod initialisiert");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
