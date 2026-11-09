package de.etiennebader.civ.mixin;

import de.etiennebader.civ.fabric.WorldHooks;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Endermen nehmen im Gemeindegebiet keine Blöcke auf.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockGoalMixin {

    @Shadow
    @Final
    private Enderman enderman;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void civ$protectTerritory(CallbackInfoReturnable<Boolean> info) {
        if (WorldHooks.isClaimed(enderman.level(), enderman.blockPosition())) {
            info.setReturnValue(false);
        }
    }
}
