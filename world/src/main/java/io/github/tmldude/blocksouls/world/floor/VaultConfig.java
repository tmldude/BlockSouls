package io.github.tmldude.blocksouls.world.floor;

/**
 * {@code config/blocksouls/vault.json}: floor 4's (The Vault) numbers. Pure data so the tests read the defaults.
 */
public class VaultConfig {
	public static final String FILE = "vault";

	/** Hits a vault wall takes before it bursts (the brief: 2–3). */
	public int wallHits = 3;
	/** Mob closet swarm for a solo party, scaled by {@code PartyScaling.directorBurst}. */
	public int closetSwarm = 4;
	public String closetSwarmMob = "minecraft:silverfish";
	/** Heavier mobs that step out of a closet with the swarm (not scaled). */
	public int closetBrutes = 1;
	public String closetBruteMob = "minecraft:zombie";
	/** Players within this many blocks of a broken closet count toward its swarm. */
	public int closetPartyReach = 48;
	/** Most wall blocks one burst plug (a shortcut, closet or hollow wall) collapses at once. */
	public int plugCollapseLimit = 64;

	/** The swarm a closet releases for a party (at least one). */
	public int swarm(double burstFactor) {
		return Math.max(1, (int) Math.round(closetSwarm * burstFactor));
	}
}
