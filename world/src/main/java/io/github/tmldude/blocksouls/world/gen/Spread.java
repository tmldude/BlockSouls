package io.github.tmldude.blocksouls.world.gen;

import io.github.tmldude.blocksouls.world.WorldConfig;

/** A structure set's random-spread grid in chunks, from {@code world.json}. Pure, for {@link ConfigSpreadPlacement}. */
public record Spread(int spacing, int separation, boolean enabled) {
	/** Separation is clamped below spacing as vanilla requires; a spacing of 0 or less disables the structure. */
	public static Spread of(int spacing, int separation) {
		if (spacing <= 0) {
			return new Spread(2, 1, false);
		}
		int s = Math.max(2, spacing);
		return new Spread(s, Math.clamp(separation, 0, s - 1), true);
	}

	public static Spread forKey(WorldConfig.Gen gen, String key) {
		return switch (key) {
			case "wreck" -> of(gen.wreckSpacingChunks, gen.wreckSeparationChunks);
			case "checkpoint" -> of(gen.checkpointSpacingChunks, gen.checkpointSeparationChunks);
			default -> throw new IllegalArgumentException("Unknown config spread " + key);
		};
	}
}
