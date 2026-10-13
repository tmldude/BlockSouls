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
	/**
	 * The seal over the fog wall (server side, any height): participants inside can't leave, nobody else can enter.
	 * Blocks above and below the boss's home it reaches; 0 or less = unlimited.
	 */
	public int sealHeight = 0;
	/** Speed of the push (blocks/tick) someone gets when the seal puts them back; no damage. */
	public double pushStrength = 0.6;
	/** A move longer than this in one tick is a server teleport (commands, run transitions) and isn't undone. */
	public double sealMaxCatchDistance = 64;
	/** While resetting, teleport home if the walk takes longer than this. */
	public int returnHomeTimeoutTicks = 200;

	// Boss bar
	/** Players this close see the bar even if they aren't in the fight. */
	public double barViewRange = 48;
	/** Full bar resend interval (smaller updates go out whenever health or phase changes). */
	public int barKeepAliveTicks = 20;

	public Posture posture = new Posture();
	public Presentation presentation = new Presentation();
	public Picker picker = new Picker();
	public Reactions reactions = new Reactions();
	public AffixRules affixes = new AffixRules();

	/**
	 * Posture / stagger (PLAN2 §3h): hidden poise filled by precision hits and well-timed rolls; full poise opens a
	 * visceral window. Max poise scales with the party like boss health (common's party.json {@code bossHealth}).
	 */
	public static class Posture {
		public boolean enabled = true;
		/** Max poise for one player, unless the boss's definition overrides it. */
		public double basePoise = 100;
		/** Poise per precision hit (flat), plus {@code precisionDamagePoise} per point of its damage. */
		public double precisionHitPoise = 5;
		public double precisionDamagePoise = 0.4;
		/** Poise per melee/any hit landing on a weak-point part (a part whose damage multiplier is above 1). */
		public double weakPointHitPoise = 3;
		/** Poise per well-timed roll (once per player per attack). */
		public double rollPoise = 14;
		/** A roll counts from this many ticks before the attack's hitboxes go live until they end. */
		public int rollLeadTicks = 6;
		/** ...if the roller is within the attack's max range plus this. */
		public double rollRangeSlack = 4;
		/** Poise starts draining after this long without a gain... */
		public int decayDelayTicks = 60;
		/** ...at this much per tick. */
		public double decayPerTick = 0.6;
		/** The visceral window: the boss is down, and takes {@code visceralDamageMult} times damage. */
		public int visceralTicks = 60;
		public double visceralDamageMult = 2.0;
		/** After a visceral window poise doesn't fill for this long. */
		public int lockoutTicks = 100;
	}

	/** Boss presentation: name-splash, hit-stop, damage-state thresholds. */
	public static class Presentation {
		/** Name-splash length when the fight starts (1.5 s). */
		public int splashTicks = 30;
		/** Boss freezes this long on a heavy hit, given or taken. */
		public int hitStopTicks = 2;
		/** A hit on the boss is heavy at this share of its max health... */
		public double heavyHitShareOfBoss = 0.04;
		/** ...and a hit by the boss at this share of the victim's max health. */
		public double heavyHitShareOfPlayer = 0.3;
		/** Health fractions where the "damaged" texture overlay steps up (one step each). */
		public double[] damageStates = {0.66, 0.33};
	}

	/** Utility-scoring weights for the attack picker (PLAN2 §3k Tier 1). 0 turns a factor off. */
	public static class Picker {
		public boolean enabled = true;
		public double distance = 0.6;
		public double angle = 0.8;
		public double stamina = 0.8;
		public double rolled = 1.2;
		public double airborne = 1.2;
		/** An attack unused this long scores full freshness. */
		public double freshnessTicks = 200;
		/** "Just rolled" means within this many ticks. */
		public int justRolledTicks = 15;
	}

	/** Reactive moves (sidestep vs. aim). The reaction floor and cooldown are clamped by {@code Fairness}. */
	public static class Reactions {
		public boolean sidestep = true;
		/** Ticks a player must aim at the boss before it reacts (clamped to 6..8). */
		public int reactionTicks = 7;
		/** Minimum ticks between sidesteps (at least 40). */
		public int cooldownTicks = 100;
		/** Aim cone half-angle, degrees. */
		public double aimConeDegrees = 6;
		/** Only within this range of the aiming player. */
		public double range = 24;
		/** Chance a qualifying aim actually makes it sidestep. */
		public double chance = 0.5;
		/** Sideways push, blocks/tick. */
		public double speed = 0.9;
	}

	/** Field-boss affixes (Expedition only). */
	public static class AffixRules {
		public boolean enabled = true;
		/** Field bosses roll an affix from this floor on. */
		public int fromFloor = 2;
		/** Chance a field boss on such a floor carries one. */
		public double chance = 1.0;
		/** Burning: attack hits set the victim on fire this many ticks. */
		public int burningFireTicks = 60;
		/** Frosted: attack hits slow (Slowness amplifier 1) for this many ticks. */
		public int frostedSlowTicks = 60;
		/** Volatile: on death a telegraphed ring detonates after this many ticks, dealing this damage within this radius. */
		public int volatileFuseTicks = 30;
		public double volatileDamage = 8;
		public double volatileRadius = 6;
		/** Magnetic: pulls players within this radius toward it, this hard, every {@code magneticIntervalTicks}. */
		public double magneticRadius = 12;
		public double magneticPull = 0.25;
		public int magneticIntervalTicks = 40;
	}

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
