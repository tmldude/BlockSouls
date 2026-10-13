package io.github.tmldude.blocksouls.bosses.riftwarden;

import io.github.tmldude.blocksouls.bosses.api.AttackStats;
import io.github.tmldude.blocksouls.bosses.archetype.ArchetypeParams;

/**
 * {@code config/blocksouls/boss-riftwarden.json}: floor 6's final boss. Ticks are 1/20 s; damage is at normal
 * difficulty, phase 1. Party size scales its health (PartyScaling bossHealth, like every boss), its phase-2 adds
 * ({@code arenaAdds}), the arena hazards ({@code arenaHazards}) and the damage rush's window ({@code rushWindow}).
 */
public class RiftwardenConfig {
	public double health = 900;
	public double armor = 14;
	public double speed = 0.26;
	/** Fog wall radius; matches the final arena's ring (world's {@code final_arena} slot, radius 18). */
	public int arenaRadius = 19;
	/** Max poise for one player (posture); 0 or less = the framework default. */
	public double poise = 0;

	/** Trigger plate: a non-spectator player within this many blocks (horizontally) of the arena centre starts the fight. */
	public double plateRadius = 1.6;

	// ---------------------------------------------------------------- phase 1: the Warden

	/** Rift ring: a ground wave out from the boss; jump it. */
	public ArchetypeParams.Ring ring = new ArchetypeParams.Ring(new AttackStats(20, 50, 20, 120, 0, 10, 3, 9)).reach(16, 0.5);
	/** Fault lines: rift lines in a cross under the target. */
	public ArchetypeParams.Cross cross = new ArchetypeParams.Cross(new AttackStats(22, 4, 18, 120, 0, 20, 2, 10)).arms(4, 10);
	/** Rift leap: jumps to a player, shockwave on landing. */
	public ArchetypeParams.Leap leap = new ArchetypeParams.Leap(new AttackStats(26, 3, 26, 160, 5, 16, 2, 13)).leap(16, 3.5);
	/** Shoulder charge across the ring. */
	public ArchetypeParams.Charge charge = new ArchetypeParams.Charge(new AttackStats(22, 14, 20, 150, 6, 18, 1.5, 12)).distance(16);

	// ---------------------------------------------------------------- phase 2: transformed

	/** Phase 2 starts below this share of health: the Warden tears open and transforms. */
	public double phase2Threshold = 0.6;
	public double phase2Damage = 1.2;
	public double phase2Recovery = 0.75;
	public double phase2Cooldowns = 0.75;
	public double phase2Speed = 1.15;
	/** Rift beam: a sweeping beam around the boss. */
	public ArchetypeParams.Beam beam = new ArchetypeParams.Beam(new AttackStats(26, 50, 26, 220, 0, 12, 1.5, 5)).beam(12, 4);
	/** Seeking motes. */
	public ArchetypeParams.Orbs orbs = new ArchetypeParams.Orbs(new AttackStats(20, 80, 16, 160, 0, 20, 2, 6)).orbs(4, 0.4);
	/** Pull from the rift: adds from floor 5's roster (count × PartyScaling arenaAdds); a shield holds until they die. */
	public ArchetypeParams.Summon summon = new ArchetypeParams.Summon(new AttackStats(30, 1, 20, 20 * 45, 0, 30, 1.4, 0)).adds(3, 5);
	/** Feint: starts a ring, then leaps instead. */
	public ArchetypeParams.Feint feint = new ArchetypeParams.Feint(new AttackStats(9, 1, 0, 240, 5, 10, 1.2, 0));

	// ---------------------------------------------------------------- phase 3: the damage rush

	/** Phase 3 (the damage rush) starts below this share of health. */
	public double phase3Threshold = 0.3;
	public double phase3Damage = 1.0;
	public double phase3Recovery = 1.4;
	public double phase3Cooldowns = 1.6;
	public double phase3Speed = 0.8;
}
