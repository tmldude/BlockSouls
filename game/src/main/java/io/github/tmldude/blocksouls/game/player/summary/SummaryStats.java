package io.github.tmldude.blocksouls.game.player.summary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player tallies for one run (pure: no Minecraft state), filled from game events by {@link PlayerRunSummary}.
 * Boons are scored so the card can name the best one: keystones over everything, then tier, then level.
 */
public final class SummaryStats {
	private final Map<UUID, Tally> tallies = new LinkedHashMap<>();
	/** Bosses killed this run (one per kill, however many players took part). */
	public int bossesFelled;

	public static final class Tally {
		public String name = "";
		public int revivesGiven;
		public long downedTicks;
		public int bossKills;
		public String bestBoon = "";
		public int bestBoonTier = -1;
		public int bestBoonScore = -1;
		public List<String> peakBuild = List.of();
		public int peakBuildStacks;
	}

	public Tally tally(UUID player, String name) {
		Tally tally = tallies.computeIfAbsent(player, id -> new Tally());
		if (name != null && !name.isEmpty()) {
			tally.name = name;
		}
		return tally;
	}

	public Tally get(UUID player) {
		return tallies.get(player);
	}

	public void clear() {
		tallies.clear();
		bossesFelled = 0;
	}

	public boolean isEmpty() {
		return tallies.isEmpty();
	}

	/** Keystones rank above every plain boon; then tier (0 common .. 2 legendary); then level. */
	public static int boonScore(int tier, boolean keystone, int level) {
		return (keystone ? 10_000 : 0) + tier * 100 + Math.max(0, level);
	}

	/** Keeps the higher-scoring boon (ties keep the first one taken). */
	public static void offerBoon(Tally tally, String boon, int tier, boolean keystone, int level) {
		int score = boonScore(tier, keystone, level);
		if (score > tally.bestBoonScore) {
			tally.bestBoonScore = score;
			tally.bestBoon = boon;
			tally.bestBoonTier = tier;
		}
	}

	/** Keeps the build line with the most total stacks seen this run (a downed player loses boons; the card shouldn't). */
	public static void offerBuild(Tally tally, List<String> tags, int totalStacks) {
		if (totalStacks > tally.peakBuildStacks) {
			tally.peakBuildStacks = totalStacks;
			tally.peakBuild = List.copyOf(tags);
		}
	}

	/** Index of the player who spent the most time downed, or -1 if nobody went down. Ties go to the first. */
	public static int mostDowned(List<Long> downedTicks) {
		int best = -1;
		long most = 0;
		for (int i = 0; i < downedTicks.size(); i++) {
			if (downedTicks.get(i) > most) {
				most = downedTicks.get(i);
				best = i;
			}
		}
		return best;
	}

	public List<UUID> players() {
		return new ArrayList<>(tallies.keySet());
	}
}
