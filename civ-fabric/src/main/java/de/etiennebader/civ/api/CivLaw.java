package de.etiennebader.civ.api;

import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.fabric.Players;
import de.etiennebader.civ.server.protection.LawScope;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Ein von einer fremden Mod angemeldetes Gesetz: ein Schalter, den Gemeinden per Recht auf „erlaubt“ oder
 * „verboten“ stellen. Standard ist „erlaubt“, ein neu installiertes Mod-Gesetz verbietet also nichts.
 *
 * <p>Im Spiel heißt das Gesetz {@code /civ recht setzen <id> ja|nein}; {@link #id()} ist {@code modid.name}.
 * Der Anzeigename kommt aus der Sprachdatei der Mod unter dem Schlüssel {@code eddies-civ-mod.law.<id>}.
 */
public final class CivLaw {

    private final ParameterKey<Boolean> key;
    private final LawScope scope;

    CivLaw(ParameterKey<Boolean> key, LawScope scope) {
        this.key = key;
        this.scope = scope;
    }

    /** ID des Gesetzes, {@code modid.name}. */
    public String id() {
        return key.id();
    }

    /** Wessen Recht gilt: das des Orts oder das der Gemeinde des Spielers. */
    public Scope scope() {
        return Scope.of(scope);
    }

    /**
     * Ob die Handlung an diesem Ort erlaubt ist. Ohne laufenden Server, in der Wildnis, für Spieler ohne
     * Gemeinde (bei {@link Scope#CITIZENSHIP}) und für Admins mit {@code /civ admin umgehen} ist sie erlaubt.
     *
     * @param pos der Ort der Handlung, z. B. der Block des Geräts
     */
    public boolean permits(ServerPlayer player, Level level, BlockPos pos) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        return CivApi.server()
                .map(civ -> civ.protection().permits(Players.ref(player), key, scope, Players.position(level, pos)))
                .orElse(true);
    }

    /** Wie {@link #permits}, zeigt dem Spieler bei Verbot {@code denial} über der Hotbar. */
    public boolean permitsOrNotify(ServerPlayer player, Level level, BlockPos pos, Component denial) {
        boolean allowed = permits(player, level, pos);
        if (!allowed) {
            player.sendOverlayMessage(Objects.requireNonNull(denial, "denial"));
        }
        return allowed;
    }

    /** Wessen Recht für ein Mod-Gesetz gilt. */
    public enum Scope {
        /** Das Recht der Gemeinde, auf deren Gebiet die Handlung stattfindet. */
        TERRITORY(LawScope.TERRITORY),
        /** Das Recht der Gemeinde, der der Spieler angehört, überall. */
        CITIZENSHIP(LawScope.CITIZENSHIP);

        private final LawScope internal;

        Scope(LawScope internal) {
            this.internal = internal;
        }

        LawScope internal() {
            return internal;
        }

        static Scope of(LawScope internal) {
            return internal == LawScope.TERRITORY ? TERRITORY : CITIZENSHIP;
        }
    }
}
