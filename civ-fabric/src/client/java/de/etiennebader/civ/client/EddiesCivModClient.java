package de.etiennebader.civ.client;

import de.etiennebader.civ.fabric.content.CivContent;
import de.etiennebader.civ.fabric.content.LabelBlockEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class EddiesCivModClient implements ClientModInitializer {
	@Override
	@SuppressWarnings("unchecked")
	public void onInitializeClient() {
		var type = (BlockEntityType<LabelBlockEntity>) BuiltInRegistries.BLOCK_ENTITY_TYPE
				.getValue(CivContent.LABEL_BLOCK_ENTITY_ID);
		BlockEntityRendererRegistry.register(type, LabelRenderer::new);
	}
}
