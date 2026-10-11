package io.github.tmldude.blocksouls.game.run;

import java.util.List;
import java.util.Random;
import java.util.Set;

/** Rolls each night's modifier (pure, seeded by the run). */
public final class NightModifiers {
	private NightModifiers() {
	}

	public static boolean isBloodMoon(int night, RunConfig config) {
		return config.bloodMoonEvery > 0 && night > 0 && night % config.bloodMoonEvery == 0;
	}

	/** Every 4th night is a Blood Moon; other nights roll one modifier from the pool with modifierChance. */
	public static Set<String> roll(int night, long runSeed, RunConfig config) {
		if (isBloodMoon(night, config)) {
			return Set.of(RunIds.BLOOD_MOON_ID);
		}
		Random random = new Random(RosterShuffle.mix(runSeed, night));
		List<String> pool = config.modifierPool;
		if (pool.isEmpty() || random.nextDouble() >= config.modifierChance) {
			return Set.of();
		}
		return Set.of(pool.get(random.nextInt(pool.size())));
	}

	/** Wave-size factor from tonight's modifiers. */
	public static double countFactor(Set<String> modifiers, RunConfig config) {
		return modifiers.contains(RunIds.BLOOD_MOON_ID) ? 1 + config.bloodMoonCountBonus : 1;
	}

	public static int tierShift(Set<String> modifiers) {
		return modifiers.contains(RunIds.BLOOD_MOON_ID) ? 1 : 0;
	}
}
