package io.github.tmldude.blocksouls.mobs.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.mobs.MobsConfig;

class AffixRollTest {
	/** {@code Affix} order. */
	private static final List<String> NAMES = List.of("burning", "frosted", "volatile", "magnetic");
	private final MobsConfig.AffixConfig config = new MobsConfig().affixes;

	@Test
	void classicHasNoAffixesUnlessEnabled() {
		assertEquals(0, AffixRoll.chance(0, false, false, config));
		assertEquals(config.classicChance, AffixRoll.chance(0, false, true, config));
		assertTrue(AffixRoll.roll(0.0, 0.0, 0, false, false, NAMES, config).isEmpty());
	}

	@Test
	void firstFloorStaysPlain() {
		assertEquals(0, AffixRoll.chance(1, false, false, config));
		assertEquals(config.chance, AffixRoll.chance(2, false, false, config));
	}

	@Test
	void escalatedFloorsAlwaysAffix() {
		assertEquals(1.0, AffixRoll.chance(5, true, false, config));
		assertTrue(AffixRoll.roll(0.999, 0.1, 5, true, false, NAMES, config).isPresent());
	}

	@Test
	void pickFollowsWeights() {
		Map<String, Integer> weights = Map.of("burning", 1, "frosted", 0, "volatile", 3, "magnetic", 0);
		assertEquals(Optional.of("burning"), AffixRoll.pick(0.0, NAMES, weights));
		assertEquals(Optional.of("burning"), AffixRoll.pick(0.24, NAMES, weights));
		assertEquals(Optional.of("volatile"), AffixRoll.pick(0.26, NAMES, weights));
		assertEquals(Optional.of("volatile"), AffixRoll.pick(0.999, NAMES, weights));
	}

	@Test
	void allZeroWeightsMeansNoAffix() {
		assertTrue(AffixRoll.pick(0.5, NAMES, Map.of()).isEmpty());
	}

	@Test
	void everyAffixCanRollByDefault() {
		for (int i = 0; i < 4; i++) {
			assertEquals(NAMES.get(i), AffixRoll.pick(i / 4.0 + 0.01, NAMES, config.weights).orElseThrow());
		}
	}
}
