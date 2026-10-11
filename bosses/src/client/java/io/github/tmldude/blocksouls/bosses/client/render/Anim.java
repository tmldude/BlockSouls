package io.github.tmldude.blocksouls.bosses.client.render;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Short form for vanilla keyframe animations: {@code Anim.of(1.0f).rot("right_arm", 0, 0, 0, 0, 1, -160, 0, 0).build()}
 * means "rotate right_arm from (0,0,0)° at 0 s to (-160,0,0)° at 1 s". Values are groups of 4: time in seconds, then
 * x, y, z (degrees for {@link #rot}, pixels for {@link #pos}). Offsets add on top of the model's pose.
 */
public final class Anim {
	private final AnimationDefinition.Builder builder;
	private boolean smooth;

	private Anim(float seconds) {
		this.builder = AnimationDefinition.Builder.withLength(Math.max(0.05f, seconds));
	}

	public static Anim of(float seconds) {
		return new Anim(seconds);
	}

	/** Catmull-Rom interpolation for the channels added after this call (default linear). */
	public Anim smooth() {
		this.smooth = true;
		return this;
	}

	public Anim rot(String bone, float... frames) {
		return channel(bone, AnimationChannel.Targets.ROTATION, true, frames);
	}

	public Anim pos(String bone, float... frames) {
		return channel(bone, AnimationChannel.Targets.POSITION, false, frames);
	}

	private Anim channel(String bone, AnimationChannel.Target target, boolean degrees, float[] frames) {
		if (frames.length == 0 || frames.length % 4 != 0) {
			throw new IllegalArgumentException("keyframes come in groups of 4 (t, x, y, z) for " + bone);
		}
		Keyframe[] keyframes = new Keyframe[frames.length / 4];
		AnimationChannel.Interpolation interpolation = smooth ? AnimationChannel.Interpolations.CATMULLROM : AnimationChannel.Interpolations.LINEAR;
		for (int i = 0; i < keyframes.length; i++) {
			float t = frames[i * 4];
			float x = frames[i * 4 + 1];
			float y = frames[i * 4 + 2];
			float z = frames[i * 4 + 3];
			keyframes[i] = new Keyframe(t, degrees ? KeyframeAnimations.degreeVec(x, y, z) : KeyframeAnimations.posVec(x, y, z), interpolation);
		}
		builder.addAnimation(bone, new AnimationChannel(target, keyframes));
		return this;
	}

	public AnimationDefinition build() {
		return builder.build();
	}
}
