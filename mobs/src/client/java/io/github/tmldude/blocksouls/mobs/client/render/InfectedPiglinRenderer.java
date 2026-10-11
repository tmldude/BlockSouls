package io.github.tmldude.blocksouls.mobs.client.render;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.mobs.client.model.InfectedPiglinModel;
import io.github.tmldude.blocksouls.mobs.entity.InfectedPiglin;

/** Humanoid renderer so the gold sword shows in its hand. */
public class InfectedPiglinRenderer extends HumanoidMobRenderer<InfectedPiglin, HumanoidRenderState, InfectedPiglinModel> {
	private static final Identifier TEXTURE = BlockSoulsMobRenderer.texture("infected_piglin");

	public InfectedPiglinRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer) {
		this(context, new InfectedPiglinModel(context.bakeLayer(layer)));
	}

	private InfectedPiglinRenderer(EntityRendererProvider.Context context, InfectedPiglinModel model) {
		super(context, model, model, 0.5f, PiglinRenderer.PIGLIN_CUSTOM_HEAD_TRANSFORMS);
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}
}
