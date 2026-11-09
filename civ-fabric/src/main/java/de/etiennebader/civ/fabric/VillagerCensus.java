package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.server.CivServer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Meldet Dorfbewohner an die Zivilisation: beim Laden, regelmäßig mit ihrer Position und beim Tod.
 * Kinder zählen erst, wenn sie erwachsen sind. Dorfbewohner in nicht geladenen Chunks behalten ihre Gemeinde.
 */
final class VillagerCensus {

    /** Alle 10 Sekunden die Positionen aller geladenen Dorfbewohner prüfen. */
    private static final int INTERVAL = 200;

    private final CivServerHost host;
    private int ticks;

    VillagerCensus(CivServerHost host) {
        this.host = host;
    }

    void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Villager villager) {
                host.current().ifPresent(civ -> seen(civ, villager));
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Villager villager) {
                host.current().ifPresent(civ -> civ.villagers().gone(ref(villager), civ.calendar().today()));
            }
        });
    }

    void tick(MinecraftServer server, CivServer civ) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Villager villager : level.getEntities(EntityTypes.VILLAGER, villager -> villager.isAlive())) {
                seen(civ, villager);
            }
        }
    }

    private static void seen(CivServer civ, Villager villager) {
        if (villager.isBaby() || !villager.isAlive()) {
            return;
        }
        civ.villagers().seen(ref(villager), Players.position(villager.level(), villager.blockPosition()),
                civ.calendar().today());
    }

    private static PersonRef ref(Villager villager) {
        return new PersonRef(new PersonId(villager.getUUID()));
    }
}
