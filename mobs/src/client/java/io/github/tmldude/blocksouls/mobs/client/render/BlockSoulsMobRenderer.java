package io.github.tmldude.blocksouls.mobs.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.mobs.entity.HostileMob;
import io.github.tmldude.blocksouls.mobs.entity.WaveMob;

/**
 * Renderer shared by our custom-model mobs: one texture at {@code textures/entity/<name>.png}, an optional
 * full-bright layer at {@code <name>_glow.png} (veins, eyes), and an optional uniform scale.
 */
public class BlockSoulsMobRenderer<T extends HostileMob, M extends EntityModel<MobRenderState>> extends MobRenderer<T, MobRenderState, M> {
	private final Identifier texture;
	private final float modelScale;

	public BlockSoulsMobRenderer(EntityRendererProvider.Context context, M model, float shadow, String name, float modelScale, boolean glow) {
		super(context, model, shadow);
		this.texture = texture(name);
		this.modelScale = modelScale;
		if (glow) {
			RenderType glowType = RenderTypes.eyes(texture(name + "_glow"));
			addLayer(new EyesLayer<>(this) {
				@Override
				public RenderType renderType() {
					return glowType;
				}
			});
		}
	}

	public static Identifier texture(String name) {
		return CommonIds.id(CommonIds.MOBS, "textures/entity/" + name + ".png");
	}

	@Override
	public Identifier getTextureLocation(MobRenderState state) {
		return texture;
	}

	@Override
	public MobRenderState createRenderState() {
		return new MobRenderState();
	}

	@Override
	public void extractRenderState(T entity, MobRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.swing = entity.getSwingAnimation(partialTicks);
		state.aggressive = entity.isAggressive();
		if (entity instanceof WaveMob mob && mob.action() != WaveMob.ACTION_NONE) {
			state.action = mob.action();
			state.actionTicks = mob.actionTicks() + partialTicks;
		} else {
			state.action = WaveMob.ACTION_NONE;
			state.actionTicks = 0;
		}
	}

	@Override
	protected void scale(MobRenderState state, PoseStack poseStack) {
		if (modelScale != 1f) {
			poseStack.scale(modelScale, modelScale, modelScale);
		}
	}
}
