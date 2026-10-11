package io.github.tmldude.blocksouls.bosses;

/** Framework numbers shared by every boss ({@code config/blocksouls/bosses.json}). Each boss has its own file too. */
public class BossesConfig {
	// Health per player in the fight: common's party.json (PartyScaling.bossHealth).

	// Telegraph rule: hits worth more than this share of a player's health need this much windup.
	public double bigHitShareOfHealth = 0.3;
	public int bigHitMinWindupTicks = 15;

	// Fight flow
	/** Ticks between the end of one attack's recovery and the next windup. */
	public int attackGapTicks = 10;
	/** Weight multiplier for repeating the previous attack. */
	public double repeatPenalty = 0.35;
	/** Roar stagger on a phase change (invulnerable). */
	public int staggerTicks = 40;
	/** Re-evaluate the target this often. */
	public int retargetIntervalTicks = 40;
	/** Threat decay per re-evaluation (keeps old damage from pinning aggro forever). */
	public double threatDecay = 0.8;
	/** Threat a player at distance 0 gets from proximity (falls to 0 at {@code proximityRange}). */
	public double proximityThreat = 15;
	public double proximityRange = 16;
	/** A new target must beat the current one by this share to steal aggro. */
	public double aggroSwitchMargin = 0.25;

	// Engage / reset
	/** Everyone in the fight dead, downed or out of the arena this long → reset. */
	public int resetAfterTicks = 200;
	/** Extra room outside the fog wall radius that still counts as "in the arena". */
	public double arenaMargin = 1.5;
	/** Damage from attackers outside the arena while engaged is multiplied by this (no sniping through the fog). */
	public double outsideArenaDamageMult = 0.25;
	/** Fog wall band: blocks below and above the boss's home. */
	public int fogWallBelow = 4;
	public int fogWallAbove = 10;
	/** While resetting, teleport home if the walk takes longer than this. */
	public int returnHomeTimeoutTicks = 200;

	// Boss bar
	/** Players this close see the bar even if they aren't in the fight. */
	public double barViewRange = 48;
	/** Full bar resend interval (smaller updates go out whenever health or phase changes). */
	public int barKeepAliveTicks = 20;

	public Field field = new Field();

	/** The field-boss spawner (bosses appearing in the world during a run). */
	public static class Field {
		public boolean enabled = true;
		/** How often the spawner looks for a chance to spawn. */
		public int scanIntervalTicks = 100;
		/** No field boss closer than this to the world spawn (overworld only). */
		public double minDistanceFromSpawn = 200;
		/** Only spawn during an active run (not before it starts or after it's over). */
		public boolean requireActiveRun = true;
		/** "Unattended" = no player this close; field bosses leave after their rule's {@code leaveAfterTicks} of that. */
		public double leaveCheckRange = 96;
		/** Players this close hear the arrival cue. */
		public double announceRange = 192;
		/** Smoke column above an unengaged field boss, every this many ticks (0 = off). */
		public int beaconIntervalTicks = 4;
	}
}
