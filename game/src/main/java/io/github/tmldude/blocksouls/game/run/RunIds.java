package io.github.tmldude.blocksouls.game.run;

/** String ids owned by the run loop (pure, so unit tests can use them). */
public final class RunIds {
	public static final String NAMESPACE = "blocksouls_game";
	public static final String BLOOD_MOON_ID = NAMESPACE + ":blood_moon";
	public static final String FOG_ID = NAMESPACE + ":fog";
	public static final String SPRINTER_ID = NAMESPACE + ":sprinter";

	/** Entity tags (scoreboard-style tags, saved with the entity) on wave mobs. */
	public static final String WAVE_MOB_TAG = "blocksouls_wave_mob";
	public static final String ELITE_TAG = "blocksouls_wave_elite";

	private RunIds() {
	}
}
