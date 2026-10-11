package io.github.tmldude.blocksouls.bosses.api;

/** Where a boss is in its current action. Synced to clients so models can animate it. */
public enum Stage {
	/** Walking, idling or choosing the next attack. */
	NONE,
	/** Telegraph: particles, sound, pose. Nothing hurts yet. */
	WINDUP,
	/** Hitboxes live. */
	ACTIVE,
	/** Punish window. */
	RECOVERY,
	/** Phase-change roar; the boss can't be hurt. */
	STAGGER;

	private static final Stage[] VALUES = values();

	public static Stage byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
	}
}
