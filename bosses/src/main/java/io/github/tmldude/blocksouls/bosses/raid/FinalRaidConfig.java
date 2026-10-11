package io.github.tmldude.blocksouls.bosses.raid;

import io.github.tmldude.blocksouls.bosses.api.AttackStats;

/**
 * {@code config/blocksouls/boss-final_dragon.json}: the final raid. Ticks are 1/20 s; damage is at normal difficulty in
 * phase 1 (the framework multiplies by the phase and the run's difficulty).
 */
public class FinalRaidConfig {
	/** Replace the vanilla Ender Dragon with the final raid (and disable re-summoning it). */
	public boolean replaceVanillaDragon = true;

	public double health = 1000;
	public double armor = 12;
	/** The fight covers the whole main island: this far (horizontally) from its center counts as "in the arena". */
	public int arenaRadius = 110;
	/** ...and this far below / above the island surface (the arrival platform is ~15 below). */
	public int arenaBelow = 60;
	public int arenaAbove = 100;

	// Phases
	/** P2 "Grounding" starts below this share of health. */
	public double phase2Threshold = 0.7;
	/** P3 "Fall" starts below this share of health. */
	public double phase3Threshold = 0.3;
	public double phase3Damage = 1.25;
	public double phase3Cooldowns = 0.7;
	public double phase3Recovery = 0.8;
	public double phase3Speed = 1.3;
	/** Soft enrage after this long in the fight (12 minutes): all damage × {@link #softEnrageDamage}. 0 = off. */
	public int softEnrageTicks = 20 * 60 * 12;
	public double softEnrageDamage = 1.5;

	// P1: the Crystal Order
	/** Crystals in the order: this many solo, plus {@link #crystalsPerExtraPlayer} per extra player, at most {@link #crystalsMax}. */
	public int crystalsSolo = 5;
	public int crystalsPerExtraPlayer = 1;
	public int crystalsMax = 8;
	/** Damage taken while any crystal lives (the shield). The shield also stops the dragon from leaving phase 1. */
	public double shieldDamageMult = 0.15;
	/** A wrong crystal heals the dragon by this share of its max health. */
	public double wrongCrystalHeal = 0.05;
	/** A wrong crystal re-forms on its pillar after this long. */
	public int crystalReformTicks = 100;
	/** The obelisk shows the order one crystal at a time, this long each... */
	public int showStepTicks = 26;
	/** ...then pauses this long before showing it again. */
	public int showPauseTicks = 70;

	// Flight
	/** P1 strafing: circles this high above the island, this far from its center. */
	public double cruiseHeight = 20;
	public double cruiseRadius = 32;
	/** P2/P3 out of reach: higher and wider. */
	public double highHeight = 36;
	public double highRadius = 46;
	/** Blocks per tick while circling (attacks move it faster). */
	public double cruiseSpeed = 0.9;
	/** P2/P3: damage taken while airborne (it's out of reach; the perch is the damage window). */
	public double airborneDamageMult = 0.35;

	// P2: Grounding plates and the perch
	/** Ticks the required plates must be held together to pull the dragon down. */
	public int plateHoldTicks = 60;
	public int plateHoldTicksPhase3 = 40;
	/** After the dragon takes off again, the plates stay dark this long. */
	public int plateRearmTicks = 240;
	public int plateRearmTicksPhase3 = 140;
	/** Plate radius (a 3×3 of glowing obsidian at 1). */
	public int plateRadius = 1;
	/** How far from the island center the plates are placed. */
	public double plateDistance = 20;
	/** Chains of light drag the dragon to its perch for this long. */
	public int pullTicks = 40;
	/** The DPS window: perched this long... */
	public int perchTicks = 160;
	/** ...and shorter, but more often, in P3. */
	public int perchTicksPhase3 = 110;
	public int takeoffTicks = 30;
	/** Perched: all damage × this; hits on the head × {@link #headDamageMult} on top. */
	public double perchDamageMult = 1.5;
	public double headDamageMult = 2.0;

	// Attacks. AttackStats(windup, active, recovery, cooldown, minRange, maxRange, weight, damage)
	/** Breath line (flying): a telegraphed lane, then the dragon flies along it breathing void fire. Damage per hit, every 10 ticks. */
	public AttackStats breathLine = new AttackStats(40, 40, 20, 160, 0, 80, 3, 4);
	public double breathLineLength = 26;
	public double breathLineHalfWidth = 2.5;
	public int breathHitIntervalTicks = 10;
	/** Dive (flying): 1.2 s screech + a shadow on the ground, then a straight dive through the shadow. */
	public AttackStats dive = new AttackStats(24, 16, 24, 220, 0, 80, 2, 12);
	public double diveRadius = 3;
	public double diveLength = 14;
	public double diveKnockback = 1.4;
	/** Void orbs (P2+, flying or perched): slow homing orbs that pop on contact. */
	public AttackStats voidOrbs = new AttackStats(30, 6, 16, 240, 0, 80, 2, 8);
	public int voidOrbsBase = 2;
	public int voidOrbLifetimeTicks = 220;
	public double voidOrbSpeed = 0.17;
	public double voidOrbRadius = 1.1;
	/** Tail sweep (perched): a low sweep around everything but the head. Jump it, or stand at the head. */
	public AttackStats tailSweep = new AttackStats(20, 4, 16, 90, 0, 12, 3, 10);
	public double tailSweepRadius = 11;
	public double tailSweepAngle = 280;
	public double tailSweepHeight = 1.1;
	/** Wing gust (perched): a cone of wind toward the void edge. Telegraphed by the wings rising. */
	public AttackStats wingGust = new AttackStats(30, 6, 20, 150, 0, 16, 2, 4);
	public double wingGustRadius = 16;
	public double wingGustAngle = 140;
	public double wingGustPush = 2.2;
	/** Meteor shower (P3, flying): circles under every player and around them, then meteors land. */
	public AttackStats meteorShower = new AttackStats(30, 20, 20, 220, 0, 90, 3, 10);
	public int meteorsPerPlayer = 2;
	public int meteorsExtra = 3;
	public double meteorRadius = 2.5;
	public double meteorScatter = 9;
	/** Breath sweep (P3, flying): a breath line across the whole island. */
	public AttackStats breathSweep = new AttackStats(36, 50, 20, 260, 0, 90, 2, 5);
	public double breathSweepLength = 60;
	public double breathSweepHalfWidth = 3;

	/** Experience on death (the vanilla dragon's first kill). */
	public int experience = 12000;
}
