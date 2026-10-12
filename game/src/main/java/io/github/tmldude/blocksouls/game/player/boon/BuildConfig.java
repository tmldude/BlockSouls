package io.github.tmldude.blocksouls.game.player.boon;

/**
 * {@code config/blocksouls/boons.json}: the v2 build systems (levels, weighted offers, skip-for-souls, keystones,
 * curses, trinkets and every rule's numbers). The v1 relic settings (tier odds, wildcard, cards per relic) stay in
 * {@code game-players.json}. Times are ticks unless named otherwise.
 */
public class BuildConfig {
	/** Shipped-default version; bump with {@link #migrate} when a shipped default changes (DECISIONS.md rule). */
	public int defaultsVersion = 1;

	public Levels levels = new Levels();
	public Offers offers = new Offers();
	public Souls souls = new Souls();
	public Curses curses = new Curses();
	public Trinkets trinkets = new Trinkets();
	public Rules rules = new Rules();
	public Hud hud = new Hud();

	/** Applies newer shipped defaults to a file written by an older version. Hand-tuned values are kept. */
	public BuildConfig migrate() {
		if (defaultsVersion < 1) {
			defaultsVersion = 1;
		}
		return this;
	}

	public static class Levels {
		/** Levels (pips) an ordinary boon has unless its JSON says {@code "levels"}. A duplicate pick upgrades it. */
		public int defaultLevels = 3;
	}

	public static class Offers {
		/**
		 * Relic cards weight toward the build: a boon's weight is {@code 1 + tagWeightPerStack × (owned stacks of its
		 * tags)}, capped at {@code 1 + tagWeightMax}. 0 restores uniform draws. The Wildcard is unchanged.
		 */
		public double tagWeightPerStack = 0.5;
		public double tagWeightMax = 3.0;
		/** Keystones only join a relic's pool once the player owns this many stacks of one of their tags (0 = always). */
		public int keystoneMinTagStacks = 1;
		/** One keystone per trigger tag: owning one blocks offers of every other keystone sharing a trigger tag. */
		public boolean oneKeystonePerTrigger = true;
		/** Chance a relic replaces one card with "upgrade a random owned boon" (only when something can level up). */
		public double upgradeCardChance = 0.15;
		/** How many tags the HUD build line shows (top by stacks). */
		public int buildLineTags = 2;
	}

	public static class Souls {
		/** Souls paid for declining a relic's cards, by the relic's tier. */
		public int skipCommon = 1;
		public int skipRare = 2;
		public int skipLegendary = 4;
	}

	public static class Curses {
		/** Most curses a player can carry; applying more fails. */
		public int maxCurses = 5;
	}

	public static class Trinkets {
		/** Trinket slots. A pick with full slots replaces the oldest trinket. */
		public int slots = 3;
		/** Cards a Trinket Case offers. */
		public int offerCards = 3;
		/** Every participant of a field boss kill gets a Trinket Case (the final boss too). */
		public boolean caseOnBossKill = true;
	}

	/** Numbers for the rules boons/curses/trinkets reference by id; one block per rule. */
	public static class Rules {
		public PowderBurn powderBurn = new PowderBurn();
		public RedLedger redLedger = new RedLedger();
		public EmberStep emberStep = new EmberStep();
		public DeadeyeWind deadeyeWind = new DeadeyeWind();
		public StormChorus stormChorus = new StormChorus();
		public BlightTithe blightTithe = new BlightTithe();
		public Undertow undertow = new Undertow();
		public Hemorrhage hemorrhage = new Hemorrhage();
		public Loud loud = new Loud();
		public HeavyPockets heavyPockets = new HeavyPockets();
		public AllyReload allyReload = new AllyReload();
		public ReviveIframes reviveIframes = new ReviveIframes();
		public LastShell lastShell = new LastShell();
		public MagnetCore magnetCore = new MagnetCore();
		public SiegeLens siegeLens = new SiegeLens();
		public SoulJar soulJar = new SoulJar();
		public DawnClock dawnClock = new DawnClock();
	}

	public static class PowderBurn {
		/** Bullets remembered; a reload releases a nova dealing {@code damageShare} of their summed damage. */
		public int bullets = 6;
		public double damageShare = 0.5;
		public double radius = 3.5;
	}

	public static class RedLedger {
		/** Kills grant absorption (half-hearts) up to {@code overhealMax}; natural regen is off while owned. */
		public double absorptionPerKill = 2.0;
		public double overhealMax = 20.0;
	}

	public static class EmberStep {
		/** The roll leaves fire for {@code trailTicks}: mobs within {@code radius} of the roller burn and take damage. */
		public int trailTicks = 10;
		public double radius = 1.5;
		public int fireTicks = 60;
		public double damage = 2.0;
		/** Also place short-lived fire blocks along the roll. */
		public boolean placeFire = true;
	}

	public static class DeadeyeWind {
		/** Stamina refunded per precision hit (movement installs the stamina hook). */
		public double staminaRefund = 25;
	}

	public static class StormChorus {
		/** Every n-th kill strikes the nearest hostile within {@code range} with lightning. */
		public int killsPerStrike = 4;
		public double range = 12;
	}

	public static class BlightTithe {
		/** Precision hits wither the target; a kill on a withered target heals the owner. */
		public int witherTicks = 60;
		public int witherAmplifier = 1;
		public double healOnKill = 4.0;
	}

	public static class Undertow {
		/** A finished reload pulls hostiles within {@code radius} toward the owner. */
		public double radius = 8.0;
		public double pullStrength = 0.9;
	}

	public static class Hemorrhage {
		/** Healing received is multiplied by this per curse level. */
		public double healMultiplier = 0.75;
	}

	public static class Loud {
		/** Every {@code intervalTicks}, idle hostiles within {@code radius} target the owner. */
		public double radius = 32;
		public int intervalTicks = 20;
	}

	public static class HeavyPockets {
		/** Souls lost from the inventory on going down. */
		public int soulsLost = 3;
	}

	public static class AllyReload {
		/** A precision kill reloads the nearest standing ally within this range (guns installs the hook). */
		public double range = 24;
	}

	public static class ReviveIframes {
		/** Reviver and revived both get full damage immunity (Resistance V) for this long. */
		public int ticks = 200;
	}

	public static class LastShell {
		/** Once per siege (reset at dusk), a lethal hit leaves the owner at {@code health} instead of downing them. */
		public float health = 2.0F;
		public int immunityTicks = 40;
	}

	public static class MagnetCore {
		/** Dropped items within {@code radius} fly to the owner at {@code speed} blocks per tick. */
		public double radius = 6.0;
		public double speed = 0.4;
	}

	public static class SiegeLens {
		/** At dawn the owner is told about the next {@code nightsAhead} nights (Blood Moons are fixed by the cadence). */
		public int nightsAhead = 2;
	}

	public static class SoulJar {
		/** Extra souls per relic skipped. */
		public int bonusSouls = 1;
	}

	public static class DawnClock {
		/** Prep-phase length multiplier the run's clock may read via {@code BoonRules.prepTimeMultiplier}. */
		public double prepTimeMultiplier = 1.2;
	}

	/** Client-side toggles; each client reads its own file. */
	public static class Hud {
		public boolean showBuildLine = true;
		public boolean showTrinkets = true;
	}
}
