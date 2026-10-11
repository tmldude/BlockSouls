package io.github.tmldude.blocksouls.game.run;

import java.util.List;

import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** The run's numbers as pure functions (no Minecraft classes), so they can be unit-tested. */
public final class WaveMath {
	public static final int DAY_TICKS = 24000;
	/** Vanilla's full day/night cycle in real minutes. */
	public static final double VANILLA_DAY_MINUTES = 20.0;

	public static final int COMMON = 0;
	public static final int RARE = 1;
	public static final int LEGENDARY = 2;

	private WaveMath() {
	}

	/** How many mobs a wave spawns in total (at least 1). Party size scales it through {@link PartyScaling#waveSize}. */
	public static int waveCount(int wave, int players, double difficultyWaveSize, double modifierFactor, RunConfig config) {
		double raw = (config.baseCount + config.countPerWave * wave) * PartyScaling.waveSize(players) * difficultyWaveSize * modifierFactor;
		return Math.max(1, (int) Math.round(raw));
	}

	public static double healthMultiplier(int wave, double difficultyHealth, RunConfig config) {
		return (1 + config.healthPerWave * Math.max(0, wave - 1)) * difficultyHealth;
	}

	public static double damageMultiplier(int wave, double difficultyDamage, RunConfig config) {
		return (1 + config.damagePerWave * Math.max(0, wave - 1)) * difficultyDamage;
	}

	/** Share of a wave's spawns that may break blocks. */
	public static double breakerShare(int wave, RunConfig config) {
		return Math.min(config.breakerBase + config.breakerPerWave * Math.max(0, wave - 1), config.breakerMax);
	}

	/** Elites allowed in a wave: grows with the wave and, through {@link PartyScaling#elites}, with the party. */
	public static int maxElites(int wave, int players, RunConfig config) {
		int base = config.eliteBase + (config.wavesPerExtraElite > 0 ? wave / config.wavesPerExtraElite : 0);
		return (int) Math.round(base * PartyScaling.elites(players));
	}

	/** How many waves night {@code night} (from 1) gets. */
	public static int wavesForNight(int night, boolean endless, RunConfig config) {
		List<Integer> list = config.wavesPerNight;
		int base = list.isEmpty() ? 1 : list.get(Math.min(Math.max(night, 1), list.size()) - 1);
		return Math.max(0, base + (endless ? config.endlessExtraWavesPerNight : 0));
	}

	/** Normalized {common, rare, legendary} odds for a wave. */
	public static double[] tierOdds(int wave, RunConfig config) {
		RunConfig.TierOdds pick = null;
		for (RunConfig.TierOdds odds : config.tierOdds) {
			if (odds.fromWave <= wave && (pick == null || odds.fromWave >= pick.fromWave)) {
				pick = odds;
			}
		}
		if (pick == null) {
			return new double[] {1, 0, 0};
		}
		double sum = pick.common + pick.rare + pick.legendary;
		if (sum <= 0) {
			return new double[] {1, 0, 0};
		}
		return new double[] {pick.common / sum, pick.rare / sum, pick.legendary / sum};
	}

	/**
	 * Picks a drop tier.
	 *
	 * @param roll      uniform in [0, 1)
	 * @param tierShift tiers to move up (Blood Moon: 1), capped at legendary
	 * @param elite     elites always drop rare or better
	 */
	public static int pickTier(int wave, double roll, int tierShift, boolean elite, RunConfig config) {
		double[] odds = tierOdds(wave, config);
		int tier = LEGENDARY;
		double acc = 0;
		for (int i = 0; i < odds.length; i++) {
			acc += odds[i];
			if (roll < acc) {
				tier = i;
				break;
			}
		}
		tier = Math.min(LEGENDARY, tier + Math.max(0, tierShift));
		if (elite) {
			tier = Math.max(RARE, tier);
		}
		return tier;
	}

	/** Clock rate (day-time ticks per game tick) that makes a full cycle last {@code dayLengthMinutes}. */
	public static float clockRate(double dayLengthMinutes) {
		if (dayLengthMinutes <= 0) {
			return 1.0F;
		}
		return (float) (VANILLA_DAY_MINUTES / dayLengthMinutes);
	}

	public static int timeOfDay(long dayTime) {
		return (int) Math.floorMod(dayTime, (long) DAY_TICKS);
	}

	/** Whether this day time is between dusk (inclusive) and dawn (exclusive). */
	public static boolean isNight(long dayTime, RunConfig config) {
		int t = timeOfDay(dayTime);
		if (config.duskTime <= config.dawnTime) {
			return t >= config.duskTime && t < config.dawnTime;
		}
		return t >= config.duskTime || t < config.dawnTime;
	}

	/** Day-time ticks from {@code dayTime} forward to the next occurrence of {@code target} (0 if already there). */
	public static int ticksUntil(long dayTime, int target) {
		return Math.floorMod(target - timeOfDay(dayTime), DAY_TICKS);
	}

	/** Real seconds until day time reaches {@code target}, given the clock rate. */
	public static double secondsUntil(long dayTime, int target, float rate) {
		return ticksUntil(dayTime, target) / (double) Math.max(rate, 1.0E-5F) / 20.0;
	}
}
