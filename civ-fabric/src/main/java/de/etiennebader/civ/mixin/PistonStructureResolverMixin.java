package de.etiennebader.civ.mixin;

import de.etiennebader.civ.fabric.WorldHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Kolben dürfen keine Blöcke über eine Gebietsgrenze schieben, ziehen oder zerstören. So lassen sich Gemeinden
 * nicht von außen mit Kolben beschädigen.
 */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private BlockPos pistonPos;

    @Shadow
    @Final
    private Direction pushDirection;

    @Shadow
    @Final
    private List<BlockPos> toPush;

    @Shadow
    @Final
    private List<BlockPos> toDestroy;

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void civ$protectBorders(CallbackInfoReturnable<Boolean> info) {
        if (Boolean.TRUE.equals(info.getReturnValue())
                && WorldHooks.pistonCrossesBorder(level, pistonPos, pushDirection, toPush, toDestroy)) {
            info.setReturnValue(false);
        }
    }
}
