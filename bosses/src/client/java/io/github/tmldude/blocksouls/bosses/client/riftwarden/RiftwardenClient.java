package io.github.tmldude.blocksouls.bosses.client.riftwarden;

import net.minecraft.client.model.geom.ModelLayerLocation;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import io.github.tmldude.blocksouls.bosses.client.render.BossRenderer;
import io.github.tmldude.blocksouls.bosses.riftwarden.Riftwardens;
import io.github.tmldude.blocksouls.common.CommonIds;

/** Client registration for the Riftwarden: model layer and renderer (eyes and core always lit, rift seams from phase 2). */
public final class RiftwardenClient {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(CommonIds.RIFTWARDEN, "main");

	private RiftwardenClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, RiftwardenModel::createBodyLayer);
		EntityRendererRegistry.register(Riftwardens.TYPE, ctx ->
				new BossRenderer<>(ctx, new RiftwardenModel(ctx.bakeLayer(LAYER)), 1.4f, "riftwarden", 1.3f)
						.glow("riftwarden_glow", state -> true)
						.glow("riftwarden_rift_glow", state -> state.phase >= 1)
						.damageStates()
						.brokenPart(0, "core")
						.cracks(3));
	}
}
