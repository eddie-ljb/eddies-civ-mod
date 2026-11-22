package de.etiennebader.civ.client.mixin;

import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(VillagerRenderState.class)
public abstract class VillagerRenderStateMixin implements CivVillagerState {

    @Unique
    private UUID civ$uuid;

    @Override
    public UUID civ$uuid() {
        return civ$uuid;
    }

    @Override
    public void civ$setUuid(UUID uuid) {
        this.civ$uuid = uuid;
    }
}
