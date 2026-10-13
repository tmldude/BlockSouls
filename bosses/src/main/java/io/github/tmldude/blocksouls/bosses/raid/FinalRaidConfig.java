package io.github.tmldude.blocksouls.bosses.raid;

import java.util.List;

import io.github.tmldude.blocksouls.bosses.api.AttackStats;
import io.github.tmldude.blocksouls.bosses.archetype.ArchetypeParams;

/**
 * {@code config/blocksouls/boss-final_dragon.json}: the final raid. Ticks are 1/20 s; damage is at normal difficulty
 * (the framework multiplies by the phase and the run's difficulty).
 *
 * <p>The raid is three phases: <b>1</b> the vanilla Ender Dragon fight, untouched; <b>2 Grounded</b> (our dragon,
 * 100% → {@link #finalStandThreshold}) on foot, with a zombie wave and a herald boss; <b>3 Final Stand</b> (below
 * {@link #finalStandThreshold}) the Crystal Order and the cast damage check.
 */
public class FinalRaidConfig {
	/** Run the final raid in the End (phase 1 stays vanilla; its death starts phase 2). Off = the plain vanilla fight. */
	public boolean replaceVanillaDragon = true;

	public double health = 1000;
	public double armor = 12;
	// Mechanics (crystals, plates, wave size) scale with players through common's party.json (PartyScaling), 1–8.
	/** The fight covers the whole main island: this far (horizontally) from its center counts as "in the arena". */
	public int arenaRadius = 110;
	/** ...and this far below / above the island surface (the arrival platform is ~15 below). */
	public int arenaBelow = 60;
	public int arenaAbove = 100;

	// The fall (end of phase 1)
	/** Lying stunned (and untouchable) after it hits the ground, before phase 2 starts. */
	public int dazedTicks = 100;
	public double fallSpeed = 1.2;
	/** Ticks between one obsidian tower starting to collapse and the next. */
	public int towerCollapseStaggerTicks = 14;

	// Phase 2: Grounded
	/** Walking speed (blocks per tick) and how close it walks to its target. */
	public double walkSpeed = 0.22;
	public double walkDistance = 5;
	/** The zombie wave starts at this share of health; the herald at this one. */
	public double waveThreshold = 0.85;
	public double heraldThreshold = 0.6;
	/** Zombie wave: this many solo, × party.json's wave curve (29 for 8 players), capped. Spawned this far out. */
	public int waveSolo = 7;
	public int waveMax = 40;
	public double waveRadius = 24;
	/** Wave number the mobs are rolled for (which mob kinds are eligible). */
	public int waveMobTier = 15;
	/** If the wave isn't cleared by then, the dragon comes down anyway. */
	public int waveTimeoutTicks = 20 * 90;
	/** Herald pool (boss ids). One is picked, preferring bosses not killed this run; unregistered ones are skipped. */
	public List<String> heraldPool = List.of("blocksouls_bosses:pale_rider", "blocksouls_bosses:ashwyrm", "blocksouls_bosses:stormplate_knight",
			"blocksouls_bosses:furnace_knight");
	/** The herald's health × this (it's an add, not a full boss fight). */
	public double heraldHealthMult = 0.6;

	// Stun (after the herald, and every cleared Crystal Order in phase 3): the big damage window
	public int stunTicks = 200;
	public double stunDamageMult = 2.0;
	/** Hits on the head while it's on the ground (walking or stunned) × this. */
	public double headDamageMult = 2.0;

	// Hovering (during the wave, the herald and phase 3)
	public double hoverHeight = 20;
	public double hoverRadius = 14;
	public double flySpeed = 0.9;

	// Phase 3: Final Stand
	/** Final Stand starts below this share of health. */
	public double finalStandThreshold = 0.35;
	/** v2 archetypes. Void beam (grounded): a beam from the jaws sweeps around the dragon. */
	public ArchetypeParams.Beam beam = new ArchetypeParams.Beam(
			new AttackStats(28, 60, 26, 260, 0, 20, 1.5, 6)).beam(18, 3.5);
	/** Void spiral (final stand): bolts stream along rotating arms across the island. */
	public ArchetypeParams.Spiral spiral = new ArchetypeParams.Spiral(
			new AttackStats(26, 100, 20, 260, 0, 80, 2, 6)).arms(4, 2.5);
	/** Hunting wisps (final stand): void orbs that hang, then home. */
	public ArchetypeParams.Orbs wisps = new ArchetypeParams.Orbs(
			new AttackStats(22, 100, 16, 240, 0, 40, 1.5, 7)).orbs(4, 0.42);
	public double finalStandDamage = 1.25;
	public double finalStandCooldowns = 0.75;
	public double finalStandRecovery = 0.8;
	/** Hovering in phase 3, it barely takes damage: the stuns are the damage check. */
	public double finalStandAirborneDamageMult = 0.15;
	/** Sovereign's End: cast this long (cycle 1), then it wipes everyone in the arena. */
	public int castTicks = 20 * 60;
	/** Solo gets this much longer (×1.25). */
	public double castSoloBonus = 0.25;
	/** Each cycle the cast is this much shorter... */
	public double castCycleFactor = 0.85;
	/** ...but never shorter than this. */
	public int castMinTicks = 20 * 30;
	/** A wrong crystal also pushes the cast forward by this share of its length. */
	public double wrongCrystalCastPenalty = 0.1;
	/** Crystals in each Crystal Order: this many solo, × party.json's raid-crystal curve, at most {@link #crystalsMax}. */
	public int crystalsSolo = 3;
	public int crystalsMax = 8;
	/** A wrong crystal heals the dragon by this share of its max health. */
	public double wrongCrystalHeal = 0.05;
	/** A wrong crystal re-forms after this long. */
	public int crystalReformTicks = 100;
	/** The obelisk shows the order one crystal at a time, this long each, then pauses. */
	public int showStepTicks = 26;
	public int showPauseTicks = 70;
	/** Plate radius (a 3×3 of glowing obsidian at 1) and distance from the island center. */
	public int plateRadius = 1;
	public double plateDistance = 20;
	/** Soft enrage after this long in the fight (12 minutes): all damage × {@link #softEnrageDamage}. 0 = off. */
	public int softEnrageTicks = 20 * 60 * 12;
	public double softEnrageDamage = 1.5;

	// Attacks. AttackStats(windup, active, recovery, cooldown, minRange, maxRange, weight, damage)
	/** Bite (grounded): a lunge with the jaws, short cone in front. */
	public AttackStats bite = new AttackStats(16, 4, 14, 50, 0, 7, 3, 9);
	public double biteRange = 6.5;
	public double biteAngle = 70;
	/** Tail sweep (grounded): a low sweep around everything but the head. Jump it, or stand at the head. */
	public AttackStats tailSweep = new AttackStats(20, 4, 16, 90, 0, 12, 3, 10);
	public double tailSweepRadius = 11;
	public double tailSweepAngle = 280;
	public double tailSweepHeight = 1.1;
	/** Wing gust (grounded): a cone of wind toward the void edge. */
	public AttackStats wingGust = new AttackStats(30, 6, 20, 150, 0, 16, 2, 4);
	public double wingGustRadius = 16;
	public double wingGustAngle = 140;
	public double wingGustPush = 2.2;
	/** Ground breath (grounded): a cone of void fire that slowly follows its target. Damage per hit, every 10 ticks. */
	public AttackStats groundBreath = new AttackStats(26, 50, 24, 160, 0, 11, 2, 4);
	public double groundBreathLength = 11;
	public double groundBreathAngle = 50;
	public double groundBreathTurnRate = 2.0;
	/** Charge (grounded): lowers its head and runs a straight line. */
	public AttackStats charge = new AttackStats(24, 14, 28, 160, 8, 30, 2, 12);
	public double chargeDistance = 18;
	public double chargeKnockback = 1.5;
	/** Stomp (grounded): rears up and slams; a shockwave ring expands across the ground. Jump the ring. */
	public AttackStats stomp = new AttackStats(24, 14, 20, 140, 0, 14, 2, 8);
	public double stompRadius = 13;
	public double stompHeight = 0.8;
	/** Void orbs (any time): slow homing orbs that pop on contact. */
	public AttackStats voidOrbs = new AttackStats(30, 6, 16, 240, 0, 80, 2, 8);
	public int voidOrbsBase = 2;
	public int voidOrbLifetimeTicks = 220;
	public double voidOrbSpeed = 0.17;
	public double voidOrbRadius = 1.1;
	/** Breath line (airborne): a telegraphed lane, then it flies along it breathing void fire. */
	public AttackStats breathLine = new AttackStats(40, 40, 20, 160, 0, 80, 3, 4);
	public double breathLineLength = 26;
	public double breathLineHalfWidth = 2.5;
	public int breathHitIntervalTicks = 10;
	/** Dive (airborne): 1.2 s screech + a shadow on the ground, then a straight dive through the shadow. */
	public AttackStats dive = new AttackStats(24, 16, 24, 220, 0, 80, 2, 12);
	public double diveRadius = 3;
	public double diveLength = 14;
	public double diveKnockback = 1.4;
	/** Meteor shower (phase 3): circles under every player and around them, then meteors land. */
	public AttackStats meteorShower = new AttackStats(30, 20, 20, 220, 0, 90, 3, 10);
	public int meteorsPerPlayer = 2;
	public int meteorsExtra = 3;
	public double meteorRadius = 2.5;
	public double meteorScatter = 9;
	/** Breath sweep (phase 3): a breath line across the whole island. */
	public AttackStats breathSweep = new AttackStats(36, 50, 20, 260, 0, 90, 2, 5);
	public double breathSweepLength = 60;
	public double breathSweepHalfWidth = 3;

	/** Experience on death (the vanilla dragon's first kill: phase 1 gives none, its death becomes the fall). */
	public int experience = 12000;
}
