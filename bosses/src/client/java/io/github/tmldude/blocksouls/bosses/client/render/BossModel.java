package io.github.tmldude.blocksouls.bosses.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

import io.github.tmldude.blocksouls.bosses.api.Stage;

/**
 * Base model for bosses: vanilla Java model parts plus vanilla keyframe animations, one per attack stage. A boss model
 * builds its {@code LayerDefinition}, then supplies keyframes through {@link #animation}; this class bakes them once
 * and plays the right one from the synced stage, so a boss never writes animation-timing code.
 *
 * <p>Order each frame: pose reset → {@link #idle} (walk cycle, breathing) → the stage animation on top.
 */
public abstract class BossModel extends EntityModel<BossRenderState> {
	private final Map<String, Optional<KeyframeAnimation>> baked = new HashMap<>();

	protected BossModel(ModelPart root) {
		super(root);
	}

	/**
	 * Keyframes for {@code attack} (null for the stagger roar) in {@code stage}, or null for none. Time 0 is the start
	 * of the stage; use {@code AnimationDefinition.Builder.withLength(stageSeconds)} without looping so the last
	 * frame holds if the stage runs long.
	 */
	protected abstract @Nullable AnimationDefinition animation(@Nullable String attack, Stage stage);

	/** Procedural idle / walk pose (called after the reset, before the stage animation). */
	protected void idle(BossRenderState state) {
	}

	@Override
	public void setupAnim(BossRenderState state) {
		super.setupAnim(state);
		idle(state);
		if (state.stage == Stage.NONE) {
			return;
		}
		String attack = state.stage == Stage.STAGGER ? null : state.attack;
		String key = attack + "/" + state.stage;
		Optional<KeyframeAnimation> animation = baked.computeIfAbsent(key, k -> {
			AnimationDefinition definition = animation(attack, state.stage);
			return Optional.ofNullable(definition == null ? null : definition.bake(root()));
		});
		animation.ifPresent(a -> a.apply((long) (state.stageSeconds * 1000), 1f));
	}
}
