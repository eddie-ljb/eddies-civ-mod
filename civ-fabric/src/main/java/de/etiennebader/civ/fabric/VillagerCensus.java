package de.etiennebader.civ.fabric;

import de.etiennebader.civ.application.population.Satisfaction;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.family.FamilyRegistry;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.villager.VillagerService;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Optional;

/**
 * Meldet Dorfbewohner an die Zivilisation: beim Laden, regelmäßig mit ihrer Position und beim Tod.
 * Kinder zählen erst, wenn sie erwachsen sind. Dorfbewohner in nicht geladenen Chunks behalten ihre Gemeinde.
 *
 * <p>Zeigt außerdem die Stimmung: Zufriedene lassen ab und zu grüne Funken aufsteigen, Unzufriedene Gewitterwolken.
 * Wer schleicht und einen Dorfbewohner mit leerer Hand anklickt, öffnet sein Menü (Auskunft, reden, beeinflussen, Post);
 * dieselbe Auskunft gibt {@code /civ dorfbewohner info} im Chat.
 * Abgewanderte holt der Census beim nächsten Durchgang ab, sobald ihr Chunk geladen ist.
 */
final class VillagerCensus {

    /** Alle 10 Sekunden die Positionen aller geladenen Dorfbewohner prüfen. */
    private static final int INTERVAL = 200;

    private final CivServerHost host;
    private final RandomSource random = RandomSource.create();
    private int ticks;

    VillagerCensus(CivServerHost host) {
        this.host = host;
    }

    void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof Villager villager) || !player.isSecondaryUseActive()
                    || hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                host.current().ifPresent(civ -> MenuOpener.villager(civ, serverPlayer, ref(villager)));
            }
            return InteractionResult.SUCCESS;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Villager villager) {
                host.current().ifPresent(civ -> seen(civ, villager));
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Villager villager) {
                host.current().ifPresent(civ -> {
                    civ.villagers().gone(ref(villager), civ.calendar().today());
                    civ.religion().gone(ref(villager));
                });
            }
        });
    }

    /** Alle 2 Sekunden die Angestellten zu ihren Maschinen schicken. */
    private static final int COMMUTE_INTERVAL = 40;
    private int commuteTicks;

    void tick(MinecraftServer server, CivServer civ) {
        if (++commuteTicks >= COMMUTE_INTERVAL) {
            commuteTicks = 0;
            MachineWork.refreshWorkplaces(civ, COMMUTE_INTERVAL);
            if (MachineWork.hasWorkplaces()) {
                for (ServerLevel level : server.getAllLevels()) {
                    for (Villager villager : level.getEntities(EntityTypes.VILLAGER, found -> found.isAlive())) {
                            }
                }
            }
        }
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        MachineWork.verify(server, civ);
        List<FamilyRegistry.Child> births = civ.family().pendingBirths();
        for (ServerLevel level : server.getAllLevels()) {
            List<Villager> loaded = new java.util.ArrayList<>(level.getEntities(EntityTypes.VILLAGER, villager -> villager.isAlive()));
            if (!births.isEmpty()) {
                deliver(civ, level, loaded, births);
            }
            for (Villager villager : loaded) {
                Optional<VillagerService.PendingDeparture> departure = civ.villagers().hasDepartures()
                        ? civ.villagers().takeDeparture(ref(villager))
                        : Optional.empty();
                if (departure.isPresent()) {
                    depart(level, villager, departure.get());
                    continue;
                }
                seen(civ, villager);
                MachineWork.commute(civ, level, villager, ref(villager));
                showMood(civ, level, villager);
            }
        }
    }

    /**
     * Bringt Kinder auf die Welt: ein Baby-Dorfbewohner mit der Kennung des Kindes erscheint neben einem Elternteil,
     * dessen Chunk geladen ist. Ist beides unauffindbar, bleibt die Geburt für später vorgemerkt. Das Baby wächst wie
     * jedes Dorfbewohner-Kind heran und wird danach als Einwohner gemeldet ({@link #seen}).
     */
    private void deliver(CivServer civ, ServerLevel level, List<Villager> loaded, List<FamilyRegistry.Child> births) {
        for (FamilyRegistry.Child child : births) {
            if (loaded.stream().anyMatch(villager -> villager.getUUID().equals(child.id().value()))) {
                civ.family().born(child.id());
                continue;
            }
            Optional<Villager> parent = loaded.stream().filter(villager -> !villager.isBaby()
                    && (villager.getUUID().equals(child.parent1().uuid())
                    || villager.getUUID().equals(child.parent2().uuid()))).findFirst();
            if (parent.isEmpty()) {
                continue;
            }
            Villager baby = EntityTypes.VILLAGER.create(level, EntitySpawnReason.BREEDING);
            if (baby == null) {
                continue;
            }
            baby.setUUID(child.id().value());
            baby.setBaby(true);
            baby.snapTo(parent.get().getX(), parent.get().getY(), parent.get().getZ(), 0.0F, 0.0F);
            if (level.addFreshEntity(baby)) {
                civ.family().born(child.id());
                level.sendParticles(ParticleTypes.HEART, baby.getX(), baby.getY() + 1.2, baby.getZ(), 6, 0.3, 0.3, 0.3,
                        0.0);
            }
        }
    }

    private static void seen(CivServer civ, Villager villager) {
        if (!villager.isAlive()) {
            return;
        }
        if (villager.isBaby()) {
            civ.villagers().childSeen(ref(villager), Players.position(villager.level(), villager.blockPosition()));
            return;
        }
        civ.villagers().grownUp(ref(villager));
        civ.villagers().seen(ref(villager), Players.position(villager.level(), villager.blockPosition()),
                civ.calendar().today());
    }

    /**
     * Bringt einen abgewanderten Dorfbewohner fort: zum Gemeindehaus seiner neuen Gemeinde, wenn es in derselben
     * Dimension liegt, sonst verschwindet er in die Wildnis.
     */
    private void depart(ServerLevel level, Villager villager, VillagerService.PendingDeparture departure) {
        level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 1, villager.getZ(), 12, 0.3, 0.5, 0.3,
                0.02);
        Optional<BlockPosition> seat = departure.destination()
                .filter(target -> target.dimension().equals(Players.position(level, villager.blockPosition()).dimension()));
        if (seat.isEmpty()) {
            villager.discard();
            return;
        }
        int x = seat.get().x() + random.nextIntBetweenInclusive(-4, 4);
        int z = seat.get().z() + (random.nextBoolean() ? 3 : -3) + random.nextIntBetweenInclusive(-1, 1);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        villager.teleportTo(x + 0.5, y, z + 0.5);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + 0.5, y + 1, z + 0.5, 10, 0.4, 0.6, 0.4, 0.0);
    }

    /** Jeder dritte Durchgang im Schnitt: so blinkt nicht ständig das ganze Dorf. */
    private void showMood(CivServer civ, ServerLevel level, Villager villager) {
        if (villager.isBaby() || random.nextInt(3) != 0) {
            return;
        }
        civ.villagers().satisfactionOf(ref(villager)).ifPresent(satisfaction -> {
            int score = satisfaction.score();
            if (score >= Satisfaction.CONTENT) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 2.1, villager.getZ(),
                        4, 0.3, 0.2, 0.3, 0.0);
            } else if (score < -Satisfaction.CONTENT) {
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getY() + 2.1, villager.getZ(),
                        1, 0.2, 0.1, 0.2, 0.0);
            }
        });
    }

    private static PersonRef ref(Villager villager) {
        return new PersonRef(new PersonId(villager.getUUID()));
    }
}
