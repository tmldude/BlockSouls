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

	// ---- PR 3: mini-bosses, the secret, the trapped hall
	/** The Listener (a blind hunter, warden-bodied) for a solo party; health is scaled by {@code PartyScaling.bossHealth}. */
	public double listenerHealth = 160;
	public double listenerDamage = 7;
	/** The Colossus (an iron-golem-bodied brute) for a solo party; health is scaled by {@code PartyScaling.bossHealth}. */
	public double colossusHealth = 200;
	public double colossusDamage = 8;
	/** Players within this many blocks of the secret mini-boss when it dies are each offered a trinket. */
	public int secretTrinketReach = 48;
	/** The trapped hall's flame traps: ticks of smoke (the tell), of flame, and of rest. */
	public int trapSmokeTicks = 20;
	public int trapFlameTicks = 30;
	public int trapRestTicks = 30;
	/** Damage a lit trap deals to whoever stands on it (once per half second), and seconds of burning. */
	public float trapDamage = 3;
	public int trapBurnSeconds = 3;

	/** The swarm a closet releases for a party (at least one). */
	public int swarm(double burstFactor) {
		return Math.max(1, (int) Math.round(closetSwarm * burstFactor));
	}
}
