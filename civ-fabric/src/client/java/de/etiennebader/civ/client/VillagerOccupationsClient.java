package de.etiennebader.civ.client;

import de.etiennebader.civ.domain.population.Occupation;
import de.etiennebader.civ.fabric.look.VillagerOccupationsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Map;
import java.util.UUID;

/** Merkt sich, welchen Beruf der Server den Dorfbewohnern der Dimension zuschreibt (Villager-Skins nach Beruf). */
public final class VillagerOccupationsClient {

    private static volatile Map<UUID, Occupation> known = Map.of();

    private VillagerOccupationsClient() {
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(VillagerOccupationsPayload.TYPE,
                (payload, context) -> known = payload.occupations());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> known = Map.of());
    }

    /** Beruf dieses Dorfbewohners; ohne Angabe des Servers (auch auf Servern ohne den Mod) keiner. */
    public static Occupation of(UUID villager) {
        return known.getOrDefault(villager, Occupation.CIVILIAN);
    }
}
