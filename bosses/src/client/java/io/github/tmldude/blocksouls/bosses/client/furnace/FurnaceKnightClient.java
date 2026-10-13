package io.github.tmldude.blocksouls.bosses.client.furnace;

import net.minecraft.client.model.geom.ModelLayerLocation;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import io.github.tmldude.blocksouls.bosses.client.render.BossRenderer;
import io.github.tmldude.blocksouls.bosses.furnace.FurnaceKnights;
import io.github.tmldude.blocksouls.common.CommonIds;

/** Client registration for the Furnace Knight: model layer and renderer (grate glow always, blade glow in phase 2). */
public final class FurnaceKnightClient {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(CommonIds.id(CommonIds.BOSSES, "furnace_knight"), "main");

	private FurnaceKnightClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, FurnaceKnightModel::createBodyLayer);
		EntityRendererRegistry.register(FurnaceKnights.TYPE, ctx ->
				new BossRenderer<>(ctx, new FurnaceKnightModel(ctx.bakeLayer(LAYER)), 1.2f, "furnace_knight", 0.95f)
						.glow("furnace_knight_glow", state -> true)
						.glow("furnace_knight_blade_glow", state -> state.phase >= 1)
						.damageStates()
						.cracks(2));
	}
}
