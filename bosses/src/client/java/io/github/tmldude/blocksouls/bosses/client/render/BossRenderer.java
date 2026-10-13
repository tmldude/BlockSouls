package io.github.tmldude.blocksouls.bosses.client.render;

import java.util.function.Predicate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Stage;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.mob.Affix;
import io.github.tmldude.blocksouls.common.mob.Affixes;

/**
 * Renderer for any boss: texture {@code textures/entity/<name>.png}, optional full-bright layers (each shown while a
 * predicate on the render state holds, e.g. "phase 2"), and a uniform scale. Fills {@link BossRenderState} from the
 * synced boss state so the model can animate.
 */
public class BossRenderer<T extends BossEntity, M extends BossModel> extends MobRenderer<T, BossRenderState, M> {
	private final String name;
	private final Identifier texture;
	private final float modelScale;

	public BossRenderer(EntityRendererProvider.Context context, M model, float shadow, String name, float modelScale) {
		super(context, model, shadow);
		this.name = name;
		this.texture = texture(name);
		this.modelScale = modelScale;
		addLayer(new RenderLayer<>(this) {
			@Override
			public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, BossRenderState state, float yRot, float xRot) {
				if (state.affix != null) {
					coloredCutoutModelCopyLayerRender(getParentModel(), texture, poseStack, collector, light, state, affixTint(state.affix), 1);
				}
			}
		});
	}

	/** Field-boss affix palette shift: the skin redrawn over itself, multiplied by a tint. */
	static int affixTint(Affix affix) {
		return switch (affix) {
			case BURNING -> 0xFFFF9A5A;
			case FROSTED -> 0xFF9AD4FF;
			case VOLATILE -> 0xFFE8FF7A;
			case MAGNETIC -> 0xFFC79AFF;
		};
	}

	/** Adds a cutout layer {@code textures/entity/<file>.png} drawn over the skin while {@code when} holds. */
	public BossRenderer<T, M> overlay(String file, Predicate<BossRenderState> when) {
		Identifier overlay = texture(file);
		addLayer(new RenderLayer<>(this) {
			@Override
			public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, BossRenderState state, float yRot, float xRot) {
				if (when.test(state)) {
					coloredCutoutModelCopyLayerRender(getParentModel(), overlay, poseStack, collector, light, state, -1, 1);
				}
			}
		});
		return this;
	}

	/**
	 * The texture swap for breakable part {@code index} ({@code BossDefinition.breaks()} order): {@code <name>_broken_<part>}
	 * redraws that part's UVs cracked and charred once it breaks.
	 */
	public BossRenderer<T, M> brokenPart(int index, String part) {
		return overlay(name + "_broken_" + part, state -> (state.brokenParts & (1 << index)) != 0);
	}

	/**
	 * Damage-state swaps: {@code <name>_damaged1} below the first {@code presentation.damageStates} share of health,
	 * {@code <name>_damaged2} below the second (the worse one replaces the lighter one).
	 */
	public BossRenderer<T, M> damageStates() {
		overlay(name + "_damaged1", state -> damageState(state) == 1);
		return overlay(name + "_damaged2", state -> damageState(state) == 2);
	}

	static int damageState(BossRenderState state) {
		double[] at = BossEntity.framework().presentation.damageStates;
		int level = 0;
		for (int i = 0; i < at.length && i < 2; i++) {
			if (state.healthShare < at[i]) {
				level = i + 1;
			}
		}
		return level;
	}

	/** Emissive cracks that spread each phase: {@code <name>_cracks<phase>} glows from phase {@code phase} (1-based) on. */
	public BossRenderer<T, M> cracks(int phases) {
		for (int p = 1; p < phases; p++) {
			int phase = p;
			glow(name + "_cracks" + phase, state -> state.phase == phase || (phase == phases - 1 && state.phase > phase));
		}
		return this;
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
		state.posture = entity.posture();
		state.brokenParts = entity.brokenParts();
		state.healthShare = entity.getMaxHealth() > 0 ? entity.getHealth() / entity.getMaxHealth() : 1;
		state.affix = Affixes.of(entity).orElse(null);
	}

	@Override
	protected void scale(BossRenderState state, PoseStack poseStack) {
		if (modelScale != 1f) {
			poseStack.scale(modelScale, modelScale, modelScale);
		}
		if (state.stage == Stage.BROKEN) {
			// Posture broken: buckle (sink and pitch forward over 0.25 s) with a fading tremble.
			float t = Math.min(1f, state.stageSeconds / 0.25f);
			float tremble = Mth.sin(state.ageInTicks * 2.6f) * 0.02f * Math.max(0, 1 - state.stageSeconds / 1.5f);
			poseStack.translate(tremble, -0.12f * t, 0);
			poseStack.rotate(Axis.XP.rotationDegrees(-10f * t));
		}
	}
}
