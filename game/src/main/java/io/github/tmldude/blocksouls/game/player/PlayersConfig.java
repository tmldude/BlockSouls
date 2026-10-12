package io.github.tmldude.blocksouls.game.player;

import java.util.ArrayList;
import java.util.List;

import io.github.tmldude.blocksouls.game.player.origin.OriginsConfig;

/** {@code config/blocksouls/game-players.json}. Times are ticks unless named otherwise. */
public class PlayersConfig {
	/** Bumped when a shipped default changes, so live servers migrate that default while hand-tuned values stay. */
	public int defaultsVersion = 1;
	public Downed downed = new Downed();
	public Boons boons = new Boons();
	public Hud hud = new Hud();
	public OriginsConfig origins = new OriginsConfig();

	public static class Downed {
		/** Movement speed change while downed (−0.85 = 85% slower). */
		public double speedPenalty = -0.85;
		/** Ticks a teammate must hold use to revive. */
		public int reviveTicks = 60;
		/** Max distance (blocks) between reviver and downed player. */
		public double reviveRange = 2.5;
		/** Health after being revived. */
		public float reviveHealth = 6.0F;
		/** Resistance after being revived: duration and amplifier (1 = Resistance II). */
		public int reviveResistanceTicks = 60;
		public int reviveResistanceAmplifier = 1;
		/** How often (ticks) the server re-checks for a team wipe, e.g. after a standing player disconnects. */
		public int wipeCheckIntervalTicks = 20;
	}

	public static class Boons {
		/** Relic tier odds by wave; the bracket with the highest {@code fromWave} ≤ the current wave applies. */
		public List<TierOddsBracket> tierOdds = new ArrayList<>(List.of(
				new TierOddsBracket(1, 85, 15, 0),
				new TierOddsBracket(4, 65, 30, 5),
				new TierOddsBracket(7, 45, 40, 15)));
		/** Chance a relic replaces one card with a Wildcard. */
		public double wildcardChance = 0.10;
		public int cardsPerRelic = 3;
		/** Ticks between re-applying boon effects that were removed (milk, etc.). */
		public int reapplyIntervalTicks = 20;
		/** Combo cards may come from a relic of any tier once their requirements are owned. */
		public boolean combosAnyTier = true;
	}

	public static class TierOddsBracket {
		public int fromWave;
		public double common;
		public double rare;
		public double legendary;

		public TierOddsBracket() {
		}

		public TierOddsBracket(int fromWave, double common, double rare, double legendary) {
			this.fromWave = fromWave;
			this.common = common;
			this.rare = rare;
			this.legendary = legendary;
		}
	}

	/** Client-side toggles; each client reads its own file. */
	public static class Hud {
		public boolean showRunInfo = true;
		public boolean showBoons = true;
		public boolean showDownedOverlay = true;
		public boolean showTeammateMarkers = true;
		public boolean showReviveBar = true;
		/** Teammate markers beyond this distance (blocks) are hidden. */
		public double markerMaxDistance = 128;
	}
}
