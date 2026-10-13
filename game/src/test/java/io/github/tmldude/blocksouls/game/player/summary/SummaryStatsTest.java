package io.github.tmldude.blocksouls.game.player.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SummaryStatsTest {
	@Test
	void keystonesBeatLegendariesBeatLevels() {
		assertTrue(SummaryStats.boonScore(0, true, 1) > SummaryStats.boonScore(2, false, 5));
		assertTrue(SummaryStats.boonScore(2, false, 1) > SummaryStats.boonScore(1, false, 9));
		assertTrue(SummaryStats.boonScore(1, false, 3) > SummaryStats.boonScore(1, false, 2));
	}

	@Test
	void bestBoonKeepsTheHighestAndTheFirstOnTies() {
		SummaryStats.Tally tally = new SummaryStats.Tally();
		SummaryStats.offerBoon(tally, "a:common", 0, false, 1);
		SummaryStats.offerBoon(tally, "a:rare", 1, false, 1);
		SummaryStats.offerBoon(tally, "a:rare_twin", 1, false, 1);
		SummaryStats.offerBoon(tally, "a:common_again", 0, false, 3);
		assertEquals("a:rare", tally.bestBoon);
		assertEquals(1, tally.bestBoonTier);
	}

	@Test
	void buildKeepsThePeakEvenAfterBoonsAreLost() {
		SummaryStats.Tally tally = new SummaryStats.Tally();
		SummaryStats.offerBuild(tally, List.of("blocksouls:fire", "blocksouls:on_kill"), 5);
		SummaryStats.offerBuild(tally, List.of(), 0);
		assertEquals(List.of("blocksouls:fire", "blocksouls:on_kill"), tally.peakBuild);
	}

	@Test
	void mostDownedAtOneAndEightPlayers() {
		assertEquals(-1, SummaryStats.mostDowned(List.of(0L)), "solo, never down: no badge");
		assertEquals(0, SummaryStats.mostDowned(List.of(40L)), "solo, went down: the badge is theirs");
		List<Long> party = new ArrayList<>(List.of(0L, 20L, 300L, 0L, 300L, 10L, 0L, 5L));
		assertEquals(2, SummaryStats.mostDowned(party), "eight players: most ticks, ties to the first");
		assertEquals(-1, SummaryStats.mostDowned(List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L)), "a clean run: no badge");
	}

	@Test
	void talliesResetBetweenRuns() {
		SummaryStats stats = new SummaryStats();
		UUID id = UUID.randomUUID();
		stats.tally(id, "Alex").revivesGiven = 3;
		stats.bossesFelled = 2;
		assertEquals("Alex", stats.tally(id, "").name, "an empty name keeps the known one");
		stats.clear();
		assertTrue(stats.isEmpty());
		assertEquals(0, stats.bossesFelled);
	}
}
