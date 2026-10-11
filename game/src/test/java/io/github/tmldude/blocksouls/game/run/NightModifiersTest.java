package io.github.tmldude.blocksouls.game.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class NightModifiersTest {
	private final RunConfig config = new RunConfig();

	@Test
	void everyFourthNightIsABloodMoon() {
		assertEquals(Set.of(RunIds.BLOOD_MOON_ID), NightModifiers.roll(4, 123, config));
		assertEquals(Set.of(RunIds.BLOOD_MOON_ID), NightModifiers.roll(8, 456, config));
		assertTrue(!NightModifiers.roll(3, 123, config).contains(RunIds.BLOOD_MOON_ID));
	}

	@Test
	void rollIsSeededAndDeterministic() {
		for (int night = 1; night < 20; night++) {
			assertEquals(NightModifiers.roll(night, 42, config), NightModifiers.roll(night, 42, config));
		}
	}

	@Test
	void roughlyFortyPercentOfOtherNightsGetOneModifier() {
		int withModifier = 0;
		int nights = 0;
		for (long seed = 0; seed < 500; seed++) {
			for (int night = 1; night <= 7; night++) {
				if (NightModifiers.isBloodMoon(night, config)) {
					continue;
				}
				Set<String> mods = NightModifiers.roll(night, seed, config);
				assertTrue(mods.size() <= 1);
				assertTrue(config.modifierPool.containsAll(mods));
				nights++;
				withModifier += mods.size();
			}
		}
		double share = withModifier / (double) nights;
		assertTrue(share > 0.35 && share < 0.45, "share " + share);
	}

	@Test
	void bloodMoonFactors() {
		assertEquals(1.5, NightModifiers.countFactor(Set.of(RunIds.BLOOD_MOON_ID), config), 1e-9);
		assertEquals(1.0, NightModifiers.countFactor(Set.of(RunIds.FOG_ID), config), 1e-9);
		assertEquals(1, NightModifiers.tierShift(Set.of(RunIds.BLOOD_MOON_ID)));
		assertEquals(0, NightModifiers.tierShift(Set.of()));
	}
}
