package io.github.tmldude.blocksouls.game.player.downed;

/** Pure rules for the solo Last Stand: when it may start and how the crawl timer resolves. */
public final class LastStandRule {
	private LastStandRule() {
	}

	/** Last Stand is a solo rule: exactly one player counts toward the wipe (parties revive each other instead). */
	public static boolean isSolo(int countingPlayers) {
		return countingPlayers == 1;
	}

	/** @param usedThisSiege whether this siege's one Last Stand was spent (resets at dusk) */
	public static boolean canStart(boolean enabled, boolean solo, boolean usedThisSiege, boolean oncePerSiege) {
		return enabled && solo && (!oncePerSiege || !usedThisSiege);
	}

	/** The crawl: a countdown and a kill tally. Reaching the kills first revives; the timer running out ends the run. */
	public static final class Timer {
		private int ticksLeft;
		private int kills;
		private final int killsNeeded;

		public Timer(int ticks, int killsNeeded) {
			this.ticksLeft = Math.max(1, ticks);
			this.killsNeeded = Math.max(1, killsNeeded);
		}

		/** One tick; returns true the tick the timer runs out. */
		public boolean tick() {
			if (ticksLeft <= 0) {
				return false;
			}
			ticksLeft--;
			return ticksLeft == 0;
		}

		/** A kill while down; returns true the kill that earns the self-revive. */
		public boolean onKill() {
			if (revives() || expired()) {
				return false;
			}
			kills++;
			return revives();
		}

		public boolean revives() {
			return kills >= killsNeeded;
		}

		public boolean expired() {
			return ticksLeft <= 0;
		}

		public int ticksLeft() {
			return ticksLeft;
		}

		public int kills() {
			return kills;
		}

		public int killsNeeded() {
			return killsNeeded;
		}
	}
}
