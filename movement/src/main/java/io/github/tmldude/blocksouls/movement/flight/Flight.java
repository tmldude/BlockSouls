package io.github.tmldude.blocksouls.movement.flight;

/** Pure rules for the {@code FLIGHT} trait: double-tap jump in the air to fly while stamina lasts. */
public final class Flight {
	private Flight() {
	}

	/** Whether an active flight ends this tick. */
	public static boolean shouldEnd(boolean onGround, float stamina, boolean hasTrait, boolean downed, boolean stillFlying) {
		return onGround || stamina <= 0 || !hasTrait || downed || !stillFlying;
	}

	/** Detects a double tap of jump while airborne. One instance per client player. */
	public static final class DoubleTap {
		/** Ticks since the last airborne press, or -1 if there isn't one pending. */
		private int sincePress = -1;

		/** @return whether this press completes a double tap */
		public boolean tick(boolean pressed, boolean airborne, int windowTicks) {
			if (!airborne) {
				sincePress = -1;
				return false;
			}
			if (sincePress >= 0) {
				sincePress++;
				if (sincePress > windowTicks) {
					sincePress = -1;
				}
			}
			if (!pressed) {
				return false;
			}
			if (sincePress >= 0) {
				sincePress = -1;
				return true;
			}
			sincePress = 0;
			return false;
		}
	}
}
