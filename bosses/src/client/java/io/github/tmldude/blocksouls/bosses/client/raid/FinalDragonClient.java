package io.github.tmldude.blocksouls.bosses.client.raid;

import net.minecraft.client.model.geom.ModelLayerLocation;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import io.github.tmldude.blocksouls.bosses.raid.FinalRaids;
import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * Client registration for the final raid: the dragon (cracks glow violet always; brighter, white-hot cracks in the
 * final stand and the soft enrage) and the crystals of the order.
 */
public final class FinalDragonClient {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(CommonIds.id(CommonIds.BOSSES, "final_dragon"), "main");

	private FinalDragonClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, FinalDragonModel::createBodyLayer);
		EntityRendererRegistry.register(FinalRaids.TYPE, ctx -> new FinalDragonRenderer(ctx)
				.glow("final_dragon_glow", state -> true)
				.glow("final_dragon_enraged_glow", state -> state.phase >= 1 || state instanceof FinalDragonRenderState s && s.enraged)
				.damageStates()
				.cracks(2));
		EntityRendererRegistry.register(FinalRaids.CRYSTAL, RaidCrystalRenderer::new);
	}
}
