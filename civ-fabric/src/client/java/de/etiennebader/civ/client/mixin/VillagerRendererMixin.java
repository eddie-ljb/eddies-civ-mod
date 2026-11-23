package de.etiennebader.civ.client.mixin;

import de.etiennebader.civ.client.VillagerSkins;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Erwachsene Dorfbewohner bekommen eine individuelle Grundtextur; Kinder bleiben Vanilla. */
@Mixin(VillagerRenderer.class)
public abstract class VillagerRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/npc/villager/Villager;"
            + "Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;F)V", at = @At("TAIL"))
    private void civ$rememberUuid(Villager villager, VillagerRenderState state, float partialTick, CallbackInfo info) {
        ((CivVillagerState) state).civ$setUuid(villager.getUUID());
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)"
            + "Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
    private void civ$individualSkin(VillagerRenderState state, CallbackInfoReturnable<Identifier> info) {
        var uuid = ((CivVillagerState) state).civ$uuid();
        if (uuid != null && !state.isBaby) {
            VillagerSkins.textureOf(uuid).ifPresent(info::setReturnValue);
        }
    }
}
