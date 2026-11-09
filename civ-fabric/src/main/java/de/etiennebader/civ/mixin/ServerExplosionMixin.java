package de.etiennebader.civ.mixin;

import de.etiennebader.civ.fabric.WorldHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Explosionen zerstören im Gemeindegebiet keine Blöcke, solange das Recht es nicht erlaubt. Die Liste der
 * betroffenen Blöcke wird gefiltert, bevor die Explosion sie abbaut oder anzündet.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void civ$protectTerritory(List<BlockPos> blocks, CallbackInfo info) {
        WorldHooks.filterExplosion(level, blocks);
    }
}
