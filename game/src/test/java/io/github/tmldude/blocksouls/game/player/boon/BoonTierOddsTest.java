package io.github.tmldude.blocksouls.game.player.boon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.game.player.PlayersConfig;

class BoonTierOddsTest {
	private static final List<PlayersConfig.TierOddsBracket> DEFAULTS = new PlayersConfig().boons.tierOdds;

	@Test
	void bracketsByWave() {
		assertArrayEquals(new double[] {85, 15, 0}, TierOdds.weights(DEFAULTS, 0));
		assertArrayEquals(new double[] {85, 15, 0}, TierOdds.weights(DEFAULTS, 1));
		assertArrayEquals(new double[] {85, 15, 0}, TierOdds.weights(DEFAULTS, 3));
		assertArrayEquals(new double[] {65, 30, 5}, TierOdds.weights(DEFAULTS, 4));
		assertArrayEquals(new double[] {65, 30, 5}, TierOdds.weights(DEFAULTS, 6));
		assertArrayEquals(new double[] {45, 40, 15}, TierOdds.weights(DEFAULTS, 7));
		assertArrayEquals(new double[] {45, 40, 15}, TierOdds.weights(DEFAULTS, 50));
	}

	@Test
	void rollMatchesTheOdds() {
		Random random = new Random(42);
		int[] counts = new int[3];
		int rolls = 100_000;
		for (int i = 0; i < rolls; i++) {
			counts[TierOdds.roll(DEFAULTS, 8, random).ordinal()]++;
		}
		assertEquals(0.45, counts[0] / (double) rolls, 0.01);
		assertEquals(0.40, counts[1] / (double) rolls, 0.01);
		assertEquals(0.15, counts[2] / (double) rolls, 0.01);
	}

	@Test
	void earlyWavesNeverRollLegendary() {
		Random random = new Random(7);
		for (int i = 0; i < 10_000; i++) {
			assertTrue(TierOdds.roll(DEFAULTS, 2, random) != BoonTier.LEGENDARY);
		}
	}

	@Test
	void degenerateConfigsFallBackToCommon() {
		assertEquals(BoonTier.COMMON, TierOdds.roll(List.of(), 5, new Random(1)));
		assertEquals(BoonTier.COMMON, TierOdds.roll(List.of(new PlayersConfig.TierOddsBracket(1, 0, 0, 0)), 5, new Random(1)));
		assertEquals(BoonTier.LEGENDARY, TierOdds.roll(List.of(new PlayersConfig.TierOddsBracket(3, 0, 0, 1)), 1, new Random(1)),
				"below every bracket the lowest bracket applies");
	}
}
