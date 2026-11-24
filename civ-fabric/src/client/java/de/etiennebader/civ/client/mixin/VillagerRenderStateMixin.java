package de.etiennebader.civ.client.mixin;

import de.etiennebader.civ.client.CivVillagerState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(VillagerRenderState.class)
public abstract class VillagerRenderStateMixin implements CivVillagerState {

    @Unique
    private UUID civ$uuid;

    @Unique
    private float civ$scale = 1.0F;

    @Override
    public float civ$scale() {
        return civ$scale;
    }

    @Override
    public void civ$setScale(float scale) {
        this.civ$scale = scale;
    }

    @Override
    public UUID civ$uuid() {
        return civ$uuid;
    }

    @Override
    public void civ$setUuid(UUID uuid) {
        this.civ$uuid = uuid;
    }
}
