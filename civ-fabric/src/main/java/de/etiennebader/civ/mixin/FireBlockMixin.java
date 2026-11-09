package de.etiennebader.civ.mixin;

import de.etiennebader.civ.fabric.WorldHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feuer verbrennt im Gemeindegebiet keine Blöcke, solange das Recht es nicht erlaubt. Es brennt aus,
 * ohne den Block darunter oder daneben zu zerstören.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void civ$protectTerritory(Level level, BlockPos pos, int chance, RandomSource random, int age,
                                      CallbackInfo info) {
        if (!WorldHooks.fireMayBurn(level, pos)) {
            info.cancel();
        }
    }
}
