package io.github.tmldude.blocksouls.bosses.client.palerider;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import io.github.tmldude.blocksouls.bosses.client.render.BossRenderState;
import io.github.tmldude.blocksouls.bosses.client.render.BossRenderer;
import io.github.tmldude.blocksouls.bosses.palerider.PaleRider;
import io.github.tmldude.blocksouls.bosses.palerider.PaleRiders;
import io.github.tmldude.blocksouls.bosses.palerider.WraithSteed;
import io.github.tmldude.blocksouls.bosses.palerider.WraithSteeds;
import io.github.tmldude.blocksouls.common.CommonIds;

/** Client registration for the Pale Rider and the Wraith Steed: translucent pale bodies with full-bright eyes. */
public final class PaleRiderClient {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(CommonIds.id(CommonIds.BOSSES, "pale_rider"), "main");
	public static final ModelLayerLocation STEED_LAYER = new ModelLayerLocation(CommonIds.id(CommonIds.BOSSES, "wraith_steed"), "main");
	/** Ghostly: ~80% opaque. */
	private static final int TINT = 0xCCFFFFFF;

	private PaleRiderClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PaleRiderModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(STEED_LAYER, WraithSteedModel::createBodyLayer);
		EntityRendererRegistry.register(PaleRiders.TYPE, ctx ->
				new Renderer(ctx, new PaleRiderModel(ctx.bakeLayer(LAYER)))
						.glow("pale_rider_glow", state -> true));
		EntityRendererRegistry.register(WraithSteeds.TYPE, SteedRenderer::new);
	}

	/** The boss renderer, drawn translucent. */
	private static final class Renderer extends BossRenderer<PaleRider, PaleRiderModel> {
		Renderer(EntityRendererProvider.Context context, PaleRiderModel model) {
			super(context, model, 1.0f, "pale_rider", 1.25f);
		}

		@Override
		protected RenderType getRenderType(BossRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
			return isBodyVisible || forceTransparent ? RenderTypes.entityTranslucent(getTextureLocation(state))
					: super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
		}

		@Override
		protected int getModelTint(BossRenderState state) {
			return TINT;
		}
	}

	/** The steed: the boss's horse texture, translucent, glowing eyes. */
	private static final class SteedRenderer extends MobRenderer<WraithSteed, LivingEntityRenderState, WraithSteedModel> {
		private static final Identifier TEXTURE = BossRenderer.texture("wraith_steed");
		private static final RenderType GLOW = RenderTypes.eyes(BossRenderer.texture("wraith_steed_glow"));

		SteedRenderer(EntityRendererProvider.Context context) {
			super(context, new WraithSteedModel(context.bakeLayer(STEED_LAYER)), 0.75f);
			addLayer(new EyesLayer<>(this) {
				@Override
				public RenderType renderType() {
					return GLOW;
				}

			});
		}

		@Override
		public LivingEntityRenderState createRenderState() {
			return new LivingEntityRenderState();
		}

		@Override
		public Identifier getTextureLocation(LivingEntityRenderState state) {
			return TEXTURE;
		}

		@Override
		protected RenderType getRenderType(LivingEntityRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
			return isBodyVisible || forceTransparent ? RenderTypes.entityTranslucent(TEXTURE)
					: super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
		}

		@Override
		protected int getModelTint(LivingEntityRenderState state) {
			return TINT;
		}
	}
}
