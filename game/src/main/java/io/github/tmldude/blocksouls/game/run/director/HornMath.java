package io.github.tmldude.blocksouls.game.run.director;

/**
 * The camp horn's buff (PLAN2 §1): the earlier it blows, the bigger tonight's reward — souls per minute
 * skipped, then a drop-tier bump, then a free relic. Pure.
 */
public final class HornMath {
	private HornMath() {
	}

	public record Buff(int souls, int tierShift, boolean freeRelic, double skippedShare) {
	}

	public static Buff buff(int secondsSkipped, int daySeconds, DirectorConfig.Horn horn) {
		int skipped = Math.max(0, Math.min(secondsSkipped, Math.max(0, daySeconds)));
		double share = daySeconds <= 0 ? 0 : skipped / (double) daySeconds;
		int souls = (int) Math.floor(horn.soulsPerMinute * skipped / 60.0);
		int tier = share >= horn.rarityAt && horn.rarityAt > 0 ? 1 : 0;
		boolean relic = share >= horn.boonAt && horn.boonAt > 0;
		return new Buff(souls, tier, relic, share);
	}
}
