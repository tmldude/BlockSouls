package io.github.tmldude.blocksouls.bosses.palerider;

import io.github.tmldude.blocksouls.bosses.api.AttackStats;
import io.github.tmldude.blocksouls.bosses.field.FieldBossSettings;

/** {@code config/blocksouls/boss-pale_rider.json}. Ticks are 1/20 s; damage is at normal difficulty, phase 1. */
public class PaleRiderConfig {
	public double health = 360;
	public double armor = 6;
	/** Mounted: fast. Unhorsed (phase 2) walks at {@code phase2Speed} of this. */
	public double speed = 0.34;
	/** Big enough for a 20-block lance run plus room to dodge it. */
	public int arenaRadius = 26;
	public int xpReward = 120;

	/** Lance charge: a long lane, a run straight through it, a turn and a second, re-aimed pass. */
	public AttackStats lance = new AttackStats(20, 56, 30, 140, 5, 22, 3, 13);
	public double lanceLength = 20;
	public double lanceHalfWidth = 1.3;
	/** Ticks one pass takes (20 blocks in 16 ticks: a gallop). */
	public int lancePassTicks = 16;
	/** Ticks between the passes: the rider wheels around and the return lane is telegraphed (≥ 15). */
	public int lanceTurnTicks = 24;
	public double lanceKnockback = 1.4;

	/** Spectral sweep: the lance swings in a wide arc around the rider. */
	public AttackStats sweep = new AttackStats(16, 4, 22, 70, 0, 5, 3, 9);
	public double sweepRadius = 5;
	public double sweepAngle = 240;
	/** Height of the swept band; jumping clears it. */
	public double sweepHeight = 1.2;

	/** Rearing stomp: the horse rears and a ring shockwave rolls out. */
	public AttackStats stomp = new AttackStats(20, 8, 24, 90, 0, 4.5, 2, 10);
	public double stompRadius = 4;
	/** The ring is low: a well-timed jump clears it. */
	public double stompHeight = 0.8;
	public double stompKnockup = 0.5;

	/** Wraith call: summons spectral skeleton riders, once per phase. */
	public AttackStats wraithCall = new AttackStats(30, 4, 20, 20 * 30, 0, 30, 4, 0);
	public int wraithCallRiders = 3;
	/** Speed amplifier on the summoned skeleton horses (1 = Speed II). */
	public int wraithCallSpeedAmplifier = 1;

	/** Phase 2: three spectral spears thrown in a fan, each along a telegraphed line. */
	public AttackStats spears = new AttackStats(22, 10, 22, 80, 3, 18, 3, 8);
	public int spearCount = 3;
	/** Degrees between neighbouring spears. */
	public double spearSpread = 20;
	public double spearLength = 18;
	public double spearHalfWidth = 0.6;

	/** Phase 2: shimmers, then blinks behind its target and cuts. */
	public AttackStats blink = new AttackStats(16, 4, 26, 100, 2, 16, 2.5, 8);
	/** How far behind the target it reappears. */
	public double blinkBehind = 2.2;
	public double blinkSlashRadius = 3.2;
	public double blinkSlashAngle = 150;

	/** Phase 2: a short melee swipe on foot (filler between the bigger moves). */
	public AttackStats slash = new AttackStats(15, 3, 16, 40, 0, 3.5, 2, 6);
	public double slashRadius = 3.2;
	public double slashAngle = 120;

	/** Phase 2: the spectral horse charges across the arena on its own every so often. */
	public int phantomChargeIntervalTicks = 20 * 20;
	/** Lane telegraph before the horse comes through (≥ 15). */
	public int phantomChargeWindupTicks = 30;
	public int phantomChargeTicks = 14;
	public double phantomChargeHalfWidth = 1.4;
	public double phantomChargeDamage = 10;

	/** Phase 2 (unhorsed) floats a few blocks toward its target when it is far away. */
	public int floatIntervalTicks = 50;
	public double floatMinDistance = 7;
	public double floatSpeed = 0.9;

	/** Phase 2 starts below this share of health: the horse dissolves. */
	public double phase2Threshold = 0.55;
	public double phase2Damage = 1.15;
	public double phase2Recovery = 0.85;
	public double phase2Cooldowns = 0.8;
	/** Chase speed on foot, relative to mounted. */
	public double phase2Speed = 0.8;

	/** Field spawn: plains, meadows and savannas at night. Leaves at dawn if nobody engaged it. */
	public FieldBossSettings field = new FieldBossSettings(0.3, 20 * 60 * 10, 20 * 60 * 10);
	public double spawnMinDistance = 40;
	public double spawnMaxDistance = 60;
	/** Distant hoofbeats while it roams unengaged. */
	public int hoofbeatIntervalTicks = 60;

	/** The Wraith Steed (its whistle drops from the boss). */
	public Steed steed = new Steed();

	public static class Steed {
		public double health = 30;
		/** Best vanilla horse: 0.3375. */
		public double speed = 0.42;
		/** Vanilla horses: 0.4–1.0. */
		public double jumpStrength = 0.9;
		/** Jumps in mid-air before it has to touch the ground again. */
		public int airJumps = 1;
		/** An air jump uses at least this much of a full charge, so a quick tap still lifts. */
		public double airJumpMinCharge = 0.75;
		/** Unridden this long → it fades. */
		public int fadeTicks = 100;
		/** Whistle cooldown after the steed is killed. */
		public int deathCooldownTicks = 20 * 60;
		/** Whistle cooldown after a normal use (stops spam). */
		public int useCooldownTicks = 20;
	}
}
