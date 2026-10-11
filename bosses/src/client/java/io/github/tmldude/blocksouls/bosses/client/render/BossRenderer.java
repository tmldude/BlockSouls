package io.github.tmldude.blocksouls.bosses.client.render;

import java.util.function.Predicate;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * Renderer for any boss: texture {@code textures/entity/<name>.png}, optional full-bright layers (each shown while a
 * predicate on the render state holds, e.g. "phase 2"), and a uniform scale. Fills {@link BossRenderState} from the
 * synced boss state so the model can animate.
 */
public class BossRenderer<T extends BossEntity, M extends BossModel> extends MobRenderer<T, BossRenderState, M> {
	private final Identifier texture;
	private final float modelScale;

	public BossRenderer(EntityRendererProvider.Context context, M model, float shadow, String name, float modelScale) {
		super(context, model, shadow);
		this.texture = texture(name);
		this.modelScale = modelScale;
	}

	public static Identifier texture(String name) {
		return CommonIds.id(CommonIds.BOSSES, "textures/entity/" + name + ".png");
	}

	/** Adds a full-bright layer {@code textures/entity/<file>.png} drawn while {@code when} holds. */
	public BossRenderer<T, M> glow(String file, Predicate<BossRenderState> when) {
		RenderType type = RenderTypes.eyes(texture(file));
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return type;
			}

			@Override
			public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, BossRenderState state, float yRot, float xRot) {
				if (when.test(state)) {
					super.submit(poseStack, collector, light, state, yRot, xRot);
				}
			}
		});
		return this;
	}

	@Override
	public Identifier getTextureLocation(BossRenderState state) {
		return texture;
	}

	@Override
	public BossRenderState createRenderState() {
		return new BossRenderState();
	}

	@Override
	public void extractRenderState(T entity, BossRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.stage = entity.stage();
		Attack attack = entity.currentAttack();
		state.attack = attack == null ? null : attack.name();
		state.stageSeconds = (entity.ticksInStage() + partialTicks) / 20f;
		state.phase = entity.phase();
		state.engaged = entity.isEngaged();
	}

	@Override
	protected void scale(BossRenderState state, PoseStack poseStack) {
		if (modelScale != 1f) {
			poseStack.scale(modelScale, modelScale, modelScale);
		}
	}
}
