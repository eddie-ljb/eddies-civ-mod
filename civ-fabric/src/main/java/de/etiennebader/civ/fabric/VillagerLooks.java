package de.etiennebader.civ.fabric;

import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.population.Culture;
import de.etiennebader.civ.domain.shared.PersonId;
import de.etiennebader.civ.server.CivServer;
import de.etiennebader.civ.server.culture.CultureService;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerType;

/**
 * Aussehen der Dorfbewohner nach Kultur und Rasse ihrer Gemeinde: Die Kultur bestimmt die Kleidung (einer der sieben
 * Dorfbewohner-Stile des Spiels), die Rasse die Körpergröße. Beides sehen auch Spieler ohne den Mod; mit dem Mod färbt
 * die Rasse zusätzlich den Hautton ({@code VillagerSkins}). Dorfbewohner ohne Gemeinde bleiben, wie sie sind; Kinder
 * übernehmen das Aussehen erst als Erwachsene.
 */
final class VillagerLooks {

    private VillagerLooks() {
    }

    static void apply(CivServer civ, Villager villager) {
        if (villager.isBaby() || !villager.isAlive()) {
            return;
        }
        civ.culture().ofVillager(new PersonRef(new PersonId(villager.getUUID())))
                .ifPresent(character -> apply(villager, character));
    }

    private static void apply(Villager villager, CultureService.Character character) {
        ResourceKey<VillagerType> wanted = typeOf(character.culture());
        VillagerData data = villager.getVillagerData();
        if (!data.type().is(wanted)) {
            villager.setVillagerData(data.withType(villager.registryAccess(), wanted));
        }
        AttributeInstance scale = villager.getAttribute(Attributes.SCALE);
        if (scale != null && Math.abs(scale.getBaseValue() - character.race().scale()) > 0.001) {
            scale.setBaseValue(character.race().scale());
        }
    }

    static ResourceKey<VillagerType> typeOf(Culture culture) {
        return switch (culture) {
            case PLAINS -> VillagerType.PLAINS;
            case DESERT -> VillagerType.DESERT;
            case JUNGLE -> VillagerType.JUNGLE;
            case SAVANNA -> VillagerType.SAVANNA;
            case SNOW -> VillagerType.SNOW;
            case SWAMP -> VillagerType.SWAMP;
            case TAIGA -> VillagerType.TAIGA;
        };
    }
}
