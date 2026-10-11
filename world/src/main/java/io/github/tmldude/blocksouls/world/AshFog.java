package io.github.tmldude.blocksouls.world;

/** Pure math for the Nether's ash fog (the client mixin applies it). */
public final class AshFog {
	/** Ash gray, slightly warm. */
	public static final float[] ASH = {0.42F, 0.38F, 0.36F};

	private AshFog() {
	}

	/** Linear blend of {@code rgb} toward {@link #ASH} by {@code t} (0..1). */
	public static float[] blend(float r, float g, float b, float t) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		return new float[] {r + (ASH[0] - r) * k, g + (ASH[1] - g) * k, b + (ASH[2] - b) * k};
	}

	/** Thickens fog: never pushes it further out than vanilla had it. Returns {start, end}. */
	public static float[] thicken(float start, float end, float ashStart, float ashEnd) {
		float newEnd = Math.min(end, ashEnd);
		float newStart = Math.min(Math.min(start, ashStart), newEnd);
		return new float[] {newStart, newEnd};
	}
}
