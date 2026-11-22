package de.etiennebader.civ.api;

import de.etiennebader.civ.domain.law.ExtensionParameters;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.fabric.CivServerHost;
import de.etiennebader.civ.server.CivServer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Einstieg der Third-Party-API (#51): fremde Mods melden hier Gesetze an, die Spieler in Eddies Civ Mod
 * beschließen können, und fragen mit {@link CivLaw#permits} ab, ob eine Handlung erlaubt ist.
 *
 * <pre>{@code
 * // Entrypoint "eddies-civ-mod:api"
 * public final class Ae2Compat implements CivModApi {
 *     public static CivLaw ME_SYSTEMS;
 *
 *     public void onCivApi() {
 *         ME_SYSTEMS = CivApi.registerLaw("ae2", "me_systems_allowed", CivLaw.Scope.TERRITORY);
 *     }
 * }
 * // an der Stelle im Mod-Code, z. B. beim Öffnen des ME-Terminals:
 * if (!Ae2Compat.ME_SYSTEMS.permits(player, level, pos)) return InteractionResult.FAIL;
 * }</pre>
 *
 * <p>Gesetze müssen vor dem Serverstart angemeldet werden. Spielstände überleben das Entfernen einer Mod:
 * Gesetze unbekannter Mod-Parameter werden beim Laden verworfen.
 */
public final class CivApi {

    /** Name des Fabric-Entrypoints, den fremde Mods in der {@code fabric.mod.json} eintragen. */
    public static final String ENTRYPOINT = "eddies-civ-mod:api";

    private static final Logger LOGGER = LoggerFactory.getLogger("eddies-civ-mod/api");

    private static volatile CivServerHost host;

    private CivApi() {
    }

    /**
     * Meldet ein Gesetz an, das eine Gemeinde per Recht auf erlaubt/verboten stellt. Standard: erlaubt.
     *
     * @param modId der Namensraum, meist die Mod-ID ({@code a-z}, {@code 0-9}, {@code _})
     * @param name  Name des Gesetzes im Namensraum, z. B. {@code me_systems_allowed}
     * @param scope wessen Recht gilt
     * @return Handle, mit dem die Mod abfragt; bei erneutem Aufruf mit denselben Angaben dasselbe Gesetz
     * @throws IllegalArgumentException bei ungültiger oder kollidierender ID
     * @throws IllegalStateException    wenn der Server schon gestartet ist
     */
    public static CivLaw registerLaw(String modId, String name, CivLaw.Scope scope) {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scope, "scope");
        ParameterKey<Boolean> key = ExtensionParameters.register(
                ParameterKey.of(modId + "." + name, Boolean.class, true));
        return new CivLaw(key, scope.internal());
    }

    /** Die laufende Zivilisation; leer, solange kein Server läuft. */
    static Optional<CivServer> server() {
        CivServerHost current = host;
        return current == null ? Optional.empty() : current.current();
    }

    /** Intern: verbindet die API mit dem Server-Host. */
    public static void bind(CivServerHost serverHost) {
        host = Objects.requireNonNull(serverHost, "serverHost");
    }

    /** Intern: ruft die Entrypoints aller Mods auf, damit sie ihre Gesetze anmelden. */
    public static void collectRegistrations() {
        for (var entrypoint : FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT, CivModApi.class)) {
            try {
                entrypoint.getEntrypoint().onCivApi();
            } catch (RuntimeException e) {
                // Eine fehlerhafte fremde Mod darf Eddies Civ Mod nicht am Start hindern.
                LOGGER.error("Civ-API von {} konnte nicht angemeldet werden",
                        entrypoint.getProvider().getMetadata().getId(), e);
            }
        }
    }
}
