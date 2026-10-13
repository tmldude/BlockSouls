package io.github.tmldude.blocksouls.bosses.client.ashwyrm;

import net.minecraft.client.model.geom.ModelLayerLocation;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import io.github.tmldude.blocksouls.bosses.ashwyrm.Ashwyrm;
import io.github.tmldude.blocksouls.bosses.ashwyrm.Ashwyrms;
import io.github.tmldude.blocksouls.common.CommonIds;

/** Client registration for the Ashwyrm: model layer and renderer (ember cracks glow always, more of them when enraged). */
public final class AshwyrmClient {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(CommonIds.id(CommonIds.BOSSES, "ashwyrm"), "main");

	private AshwyrmClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, AshwyrmModel::createBodyLayer);
		EntityRendererRegistry.register(Ashwyrms.TYPE, ctx -> new AshwyrmRenderer(ctx, new AshwyrmModel(ctx.bakeLayer(LAYER)))
				.glow("ashwyrm_glow", state -> true)
				.glow("ashwyrm_enraged_glow", state -> state.phase >= Ashwyrm.ENRAGED)
				.damageStates()
				.brokenPart(0, "tail")
				.cracks(3));
	}
}
