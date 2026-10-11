package io.github.tmldude.blocksouls.mobs.logic;

/**
 * Per-player Nether dread. Rises while in the Nether, decays outside. Past {@code howlAt} the player hears
 * distant howls; crossing {@code firstPackAt} sends an Ash Hound pack, then another every
 * {@code packEverySeconds} spent in the Nether, each one bigger. Leaving the Nether resets the pack timer and
 * the pack growth, not the meter: coming back with high dread means a pack after a full interval.
 *
 * <p>Pure state so it can be unit-tested; the server ticks it once per second.
 */
public final class DreadMeter {
	/** What the server should do after a tick. */
	public record Result(boolean howl, int packSize) {
		public static final Result NONE = new Result(false, 0);
	}

	/** The tunables, decoupled from the config class. */
	public record Settings(double risePerSecond, double decayPerSecond, double howlAt, double firstPackAt,
			double packEverySeconds, int packGrowth, int packCap) {
	}

	private double dread;
	/** Seconds in the Nether since the last pack (or since dread passed the threshold this visit). */
	private double packTimer;
	/** Packs sent this visit. */
	private int packs;
	/** True once dread passed {@code firstPackAt} during this visit. */
	private boolean armed;

	public DreadMeter(double dread) {
		this.dread = Math.max(0, dread);
	}

	public double dread() {
		return dread;
	}

	public void setDread(double dread) {
		this.dread = Math.max(0, dread);
		armed = false;
		packTimer = 0;
	}

	public int packsThisVisit() {
		return packs;
	}

	/**
	 * Advances by {@code seconds}.
	 *
	 * @param basePackSize size of a first pack (the caller rolls it), later packs add {@code packGrowth} each
	 * @param howlRoll     a random number in [0, 1); a howl plays when it's below the per-tick howl chance
	 * @param howlChance   chance to howl this tick once past {@code howlAt}
	 */
	public Result tick(boolean inNether, double seconds, Settings s, int basePackSize, double howlRoll, double howlChance) {
		if (!inNether) {
			dread = Math.max(0, dread - s.decayPerSecond() * seconds);
			armed = false;
			packTimer = 0;
			packs = 0;
			return Result.NONE;
		}

		double before = dread;
		dread += s.risePerSecond() * seconds;
		boolean howl = dread >= s.howlAt() && howlRoll < howlChance;
		int pack = 0;

		if (dread >= s.firstPackAt()) {
			if (!armed) {
				armed = true;
				// Crossing the threshold sends a pack now; arriving already past it waits a full interval.
				if (before < s.firstPackAt()) {
					packTimer = 0;
					pack = packSize(basePackSize, s);
				} else {
					packTimer = seconds;
				}
			} else {
				packTimer += seconds;
				if (packTimer >= s.packEverySeconds()) {
					packTimer -= s.packEverySeconds();
					pack = packSize(basePackSize, s);
				}
			}
		}

		return pack > 0 || howl ? new Result(howl, pack) : Result.NONE;
	}

	private int packSize(int base, Settings s) {
		int size = Math.min(s.packCap(), base + packs * s.packGrowth());
		packs++;
		return size;
	}

	/** 0..1 for how strongly the ash particles show, saturating at the first pack threshold. */
	public static double intensity(double dread, Settings s) {
		if (dread < s.howlAt() / 2) {
			return 0;
		}
		return Math.clamp(dread / s.firstPackAt(), 0.0, 1.0);
	}
}
