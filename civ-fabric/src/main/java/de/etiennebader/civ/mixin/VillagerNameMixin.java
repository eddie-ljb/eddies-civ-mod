package de.etiennebader.civ.mixin;

import de.etiennebader.civ.domain.population.VillagerIdentity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nur der Vanilla-Standardname ändert sich. Namensschilder und gespeicherte Entitätsdaten bleiben unangetastet. */
@Mixin(Entity.class)
public abstract class VillagerNameMixin {

    @Inject(method = "getTypeName", at = @At("HEAD"), cancellable = true)
    private void civ$villagerName(CallbackInfoReturnable<Component> info) {
        if ((Object) this instanceof Villager villager) {
            info.setReturnValue(Component.literal(VillagerIdentity.nameOf(villager.getUUID())));
        }
    }
}
