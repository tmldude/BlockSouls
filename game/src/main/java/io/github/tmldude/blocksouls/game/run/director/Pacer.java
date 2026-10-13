package io.github.tmldude.blocksouls.game.run.director;

import io.github.tmldude.blocksouls.common.party.PartyScaling;

/**
 * The Director's spawn levers (PLAN2 §3b): a mood (build-up, lull, spike) chosen from stress once a second,
 * and a token bucket that releases wave mobs at the mood's rate up to its burst size. Lulls when battered,
 * spikes when cruising, all within config bounds; the lull ceiling caps every lull. Pure.
 */
public final class Pacer {
	public enum Mood {
		BUILD,
		LULL,
		SPIKE
	}

	/** A mood change, for the decision log. */
	public record Change(Mood from, Mood to, double stress, int seconds) {
	}

	private Mood mood = Mood.BUILD;
	private long moodUntil;
	private long cooldownUntil;
	private double tokens;
	private double rate;
	private double cap;

	public Mood mood() {
		return mood;
	}

	public long moodUntil() {
		return moodUntil;
	}

	public double tokens() {
		return tokens;
	}

	/** Mobs per second right now. */
	public double rate() {
		return rate;
	}

	public void reset() {
		mood = Mood.BUILD;
		moodUntil = 0;
		cooldownUntil = 0;
		tokens = 0;
		rate = 0;
		cap = 0;
	}

	/** Once a second: re-read stress, maybe change mood. Returns the change, or null. */
	public Change update(long now, double stress, int players, DirectorConfig.Levers levers, DirectorConfig.Pacing pacing) {
		double burst = burstSize(players, levers);
		rate = levers.spawnsPerSecond * PartyScaling.directorBurst(players) * lerp(levers.rateAtCalm, levers.rateAtBattered, stress);
		cap = burst;
		Mood before = mood;
		if (mood == Mood.LULL && now >= moodUntil) {
			mood = Mood.BUILD;
			cooldownUntil = now + Math.max(0, levers.moodCooldownSeconds);
		} else if (mood == Mood.SPIKE) {
			mood = Mood.BUILD;
			cooldownUntil = now + Math.max(0, levers.moodCooldownSeconds);
		} else if (mood == Mood.BUILD && now >= cooldownUntil) {
			if (stress > levers.lullAbove) {
				return lull(now, stress, levers.lullSeconds, pacing.lullCeilingSeconds);
			}
			if (stress < levers.spikeBelow) {
				return spike(now, stress, players, levers);
			}
		}
		return before != mood ? new Change(before, mood, stress, 0) : null;
	}

	/** Starts a lull of {@code seconds}, capped by the lull ceiling. */
	public Change lull(long now, double stress, int seconds, int lullCeilingSeconds) {
		Mood before = mood;
		int length = PacingBudget.clampLull(seconds, lullCeilingSeconds);
		mood = Mood.LULL;
		moodUntil = now + length;
		tokens = 0;
		return new Change(before, Mood.LULL, stress, length);
	}

	/** A spike: an extra {@code spikeBurstMultiplier} bursts released at once. */
	public Change spike(long now, double stress, int players, DirectorConfig.Levers levers) {
		Mood before = mood;
		mood = Mood.SPIKE;
		moodUntil = now + 1;
		double burst = burstSize(players, levers);
		tokens = Math.max(tokens, burst * Math.max(1, levers.spikeBurstMultiplier));
		return new Change(before, Mood.SPIKE, stress, 1);
	}

	/** Ends a running lull now (the pacing budget overruled it). */
	public boolean endLull(long now, int cooldownSeconds) {
		if (mood != Mood.LULL) {
			return false;
		}
		mood = Mood.BUILD;
		moodUntil = now;
		cooldownUntil = now + Math.max(0, cooldownSeconds);
		return true;
	}

	/** Once a game tick: accrue tokens at the mood's rate (none during a lull), capped at the burst size. */
	public void tick() {
		if (mood == Mood.LULL) {
			tokens = 0;
			return;
		}
		if (tokens < cap) {
			tokens = Math.min(cap, tokens + rate / 20.0);
		}
	}

	/** Takes up to {@code want} whole mobs from the bucket. */
	public int take(int want) {
		int n = (int) Math.min(Math.max(0, want), Math.floor(tokens));
		tokens -= n;
		return n;
	}

	public static double burstSize(int players, DirectorConfig.Levers levers) {
		return Math.max(1, levers.burstSize * PartyScaling.directorBurst(players));
	}

	static double lerp(double atZero, double atOne, double t) {
		double clamped = StressModel.clamp(t);
		return atZero + (atOne - atZero) * clamped;
	}
}
