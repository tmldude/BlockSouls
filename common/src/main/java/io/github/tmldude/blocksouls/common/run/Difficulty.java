package io.github.tmldude.blocksouls.common.run;

/** A difficulty preset picked when a run starts. Multipliers apply to wave mobs. */
public record Difficulty(String id, double waveSize, double mobHealth, double mobDamage) {
	public static final Difficulty NORMAL = new Difficulty("normal", 1.0, 1.0, 1.0);
}
