package io.github.tmldude.blocksouls.game.client.run;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.game.run.RunIds;
import io.github.tmldude.blocksouls.game.run.RunSyncPayload;
import io.github.tmldude.blocksouls.game.run.WaveMath;

/**
 * Sky, fog and moon cues for the overworld at night (show, don't tell): a slight dusky tint from the dusk cue to dawn, a
 * red sky and moon on Blood Moons, and close fog on fog nights. Read by the client mixins.
 */
public final class RunSkyEffects {
	/** Ramp length (day-time ticks) for fading the tint out before dawn. */
	private static final int DAWN_FADE = 1000;
	private static final Vector3fc DUSK_TINT = new Vector3f(0.30F, 0.16F, 0.28F);
	private static final Vector3fc BLOOD_TINT = new Vector3f(0.55F, 0.04F, 0.03F);
	private static final float DUSK_STRENGTH = 0.18F;
	private static final float BLOOD_STRENGTH = 0.6F;

	private static RunSyncPayload sync;

	private RunSkyEffects() {
	}

	static void update(RunSyncPayload payload) {
		sync = payload;
	}

	static void reset() {
		sync = null;
	}

	/** 0..1: how far into the dusk-to-dawn window the overworld is (0 outside it or without an active run). */
	public static float nightAmount() {
		RunSyncPayload s = sync;
		ClientLevel level = Minecraft.getInstance().level;
		if (s == null || level == null || !s.state().isActive() || level.dimension() != Level.OVERWORLD) {
			return 0;
		}
		int t = WaveMath.timeOfDay(level.getOverworldClockTime());
		int cue = s.duskCueTime();
		int dusk = s.duskTime();
		int dawn = s.dawnTime();
		if (t >= cue && t < dusk) {
			return (t - cue) / (float) Math.max(1, dusk - cue);
		}
		boolean night = dusk <= dawn ? t >= dusk && t < dawn : t >= dusk || t < dawn;
		if (!night) {
			return 0;
		}
		int untilDawn = WaveMath.ticksUntil(t, dawn);
		return untilDawn < DAWN_FADE ? untilDawn / (float) DAWN_FADE : 1;
	}

	private static boolean has(String modifier) {
		RunSyncPayload s = sync;
		return s != null && s.nightModifiers().contains(modifier);
	}

	public static boolean bloodMoon() {
		return has(RunIds.BLOOD_MOON_ID) && nightAmount() > 0;
	}

	/** Sky and fog colors drift toward dusk (or blood red). */
	public static Vector3fc tintColor(Vector3fc color) {
		float amount = nightAmount();
		if (amount <= 0) {
			return color;
		}
		boolean blood = has(RunIds.BLOOD_MOON_ID);
		Vector3fc target = blood ? BLOOD_TINT : DUSK_TINT;
		float strength = (blood ? BLOOD_STRENGTH : DUSK_STRENGTH) * amount;
		return new Vector3f(color).lerp(target, strength);
	}

	/** Fog nights: the fog closes in to ~24 blocks. */
	public static float fogEnd(float end) {
		float amount = has(RunIds.FOG_ID) ? nightAmount() : 0;
		if (amount <= 0) {
			return end;
		}
		return Mth.lerp(amount, end, Math.min(end, sync.fogDistance()));
	}

	public static float fogStart(float start) {
		float amount = has(RunIds.FOG_ID) ? nightAmount() : 0;
		if (amount <= 0) {
			return start;
		}
		return Mth.lerp(amount, start, Math.min(start, sync.fogDistance() * 0.15F));
	}

	/** Red moon on Blood Moon nights. */
	public static Vector4f moonColor(Vector4f color) {
		if (!bloodMoon()) {
			return color;
		}
		return new Vector4f(color.x(), color.y() * 0.25F, color.z() * 0.2F, color.w());
	}
}
