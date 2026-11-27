package de.etiennebader.civ.fabric.content;

import net.minecraft.world.level.block.FenceBlock;

/**
 * Die Bauabsperrung: ein Zaun in Warnfarben, der eine Baustelle umgibt. Er zählt zu den Zäunen ({@code minecraft:fences}),
 * deshalb gehen Dorfbewohner nicht hindurch und kommen nicht auf den Bauplatz. Die Bauleitung stellt ihn selbst auf und
 * baut ihn nach der Fertigstellung wieder ab; man kann ihn auch von Hand herstellen und setzen.
 */
public final class BarrierBlock extends FenceBlock {

    public BarrierBlock(Properties properties) {
        super(properties);
    }
}
