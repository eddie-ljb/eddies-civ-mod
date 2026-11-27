package de.etiennebader.civ.fabric.look;

import de.etiennebader.civ.EddiesCivMod;
import de.etiennebader.civ.domain.population.Occupation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Teilt dem Client mit, welchen Beruf die Dorfbewohner in seiner Dimension haben, damit er sie passend einkleidet.
 * Nur Dorfbewohner mit Beruf stehen darin; wer fehlt, trägt das Vanilla-Gewand. Spieler ohne den Mod bekommen das
 * Paket nicht.
 *
 * @param occupations Beruf je Dorfbewohner
 */
public record VillagerOccupationsPayload(Map<UUID, Occupation> occupations) implements CustomPacketPayload {

    public static final Type<VillagerOccupationsPayload> TYPE = new Type<>(EddiesCivMod.id("villager_occupations"));

    /** Mehr Einträge nimmt der Client nicht an (Schutz vor kaputten Paketen). */
    static final int MAX_ENTRIES = 20_000;

    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerOccupationsPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.occupations.size());
                payload.occupations.forEach((id, occupation) -> {
                    buffer.writeUUID(id);
                    buffer.writeByte(occupation.code());
                });
            },
            buffer -> {
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Zu viele Berufe: " + size);
                }
                Map<UUID, Occupation> read = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    UUID id = buffer.readUUID();
                    read.put(id, Occupation.ofCode(buffer.readByte()));
                }
                return new VillagerOccupationsPayload(read);
            });

    public VillagerOccupationsPayload {
        occupations = Map.copyOf(occupations);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
