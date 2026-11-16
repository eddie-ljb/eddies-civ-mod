package de.etiennebader.civ.fabric;

import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.command.Message;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Setzt die Spielregeln aus dem Gesetzes-Baukasten im Gebiet durch: PvP, Schutz der Tiere vor Fremden und
 * feindliche Monster. Monster, die nicht bleiben dürfen, verschwinden alle paar Sekunden (benannte und an die Welt
 * gebundene ausgenommen).
 */
public final class TerritoryRules {

    private static final int SWEEP_INTERVAL = 100;

    private final CivServerHost host;
    private int ticks;

    public TerritoryRules(CivServerHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::allowDamage);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == target) {
            return true;
        }
        Optional<CivServer> civ = host.current();
        if (civ.isEmpty()) {
            return true;
        }
        if (target instanceof ServerPlayer) {
            Optional<Message> denied = civ.get().protection()
                    .checkPvp(Players.ref(attacker), Players.position(target.level(), target.blockPosition()));
            denied.ifPresent(message -> Components.showHint(attacker, message, false));
            return denied.isEmpty();
        }
        if (target instanceof Animal) {
            boolean allowed = civ.get().protection().mayHurtAnimal(Players.ref(attacker),
                    Players.position(target.level(), target.blockPosition()));
            if (!allowed) {
                Feedback.failure(attacker);
            }
            return allowed;
        }
        return true;
    }

    private void tick(MinecraftServer server) {
        if (++ticks < SWEEP_INTERVAL) {
            return;
        }
        ticks = 0;
        host.current().ifPresent(civ -> {
            for (ServerLevel level : server.getAllLevels()) {
                List<Entity> remove = new ArrayList<>();
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof Enemy && entity instanceof Mob mob && !mob.hasCustomName()
                            && !mob.requiresCustomPersistence() && !mob.isPersistenceRequired()
                            && !civ.protection().allowsHostileMobs(Players.position(level, mob.blockPosition()))) {
                        remove.add(entity);
                    }
                }
                remove.forEach(Entity::discard);
            }
        });
    }
}
