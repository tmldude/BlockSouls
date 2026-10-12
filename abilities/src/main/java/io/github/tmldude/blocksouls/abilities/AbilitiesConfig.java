package io.github.tmldude.blocksouls.abilities;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code config/blocksouls/abilities.json}. Times are ticks (20/s); damage is in half-hearts; chances/shares 0..1. */
public class AbilitiesConfig {
	/** Bumped when shipped defaults change; {@link #migrate} brings older files up without touching hand-tuned values. */
	public int defaultsVersion = 1;

	public Kit kit = new Kit();
	public Charge charge = new Charge();
	public Synergy synergy = new Synergy();
	public Tactical tactical = new Tactical();
	public Signature signature = new Signature();
	public Ultimate ultimate = new Ultimate();
	public Hud hud = new Hud();

	/** What a player gets when a slot is empty, and which signature each origin grants. */
	public static class Kit {
		/** Ability ids without the {@code blocksouls_abilities:} namespace. */
		public String defaultTactical = "frag";
		/** Used while the player has no origin (classic runs, or before v2 origins ship). */
		public String defaultSignature = "fan_the_hammer";
		public String defaultUltimate = "storm_barrage";
		/** Origin id → signature ability id. Origins come from {@code Origins.of(player)}. */
		public Map<String, String> signatureByOrigin = defaultSignatures();
		/** Re-check the origin's signature every time the player joins (so an origin pick mid-run lands). */
		public boolean refreshSignatureOnJoin = true;
	}

	/** Ultimate charge, 0..1 of the bar. Multiplied by the party curve {@code party.json: ultimateCharge}. */
	public static class Charge {
		public double perKill = 0.045;
		public double perPrecisionHit = 0.02;
		public double eliteKillMult = 2.0;
		public double bossKillMult = 6.0;
		/** Each {@code on_kill} tag stack in the player's build adds this much more charge per kill (cap below). */
		public double onKillStackBonus = 0.05;
		/** Each {@code on_precision} stack adds this much more charge per precision hit. */
		public double onPrecisionStackBonus = 0.05;
		public double stackBonusCap = 0.5;
	}

	/** How an ability scales with boon stacks that share its tags ({@code SynergyTags.of(player)}). */
	public static class Synergy {
		/** Damage / healing multiplier bonus per shared tag stack. */
		public double perStack = 0.08;
		public double cap = 0.6;
	}

	public static class Tactical {
		public int cooldownTicks = 500;
		public Frag frag = new Frag();
		public WebBomb webBomb = new WebBomb();
		public FireWall fireWall = new FireWall();
		public HealingDart healingDart = new HealingDart();

		public static class Frag {
			public double speed = 1.1;
			public double gravity = 0.05;
			public int fuseTicks = 30;
			public double radius = 3.5;
			public double damage = 12;
			/** Damage share kept at the edge of the radius. */
			public double edgeShare = 0.4;
			public double knockback = 0.8;
		}

		public static class WebBomb {
			public double speed = 1.0;
			public double gravity = 0.05;
			public int fuseTicks = 40;
			public double radius = 4.0;
			public int durationTicks = 100;
			public int pulseTicks = 5;
			public int slownessAmplifier = 3;
			/** Blocks per tick mobs are dragged toward the centre. */
			public double pullStrength = 0.1;
		}

		public static class FireWall {
			public double length = 7;
			public double distanceAhead = 2;
			public int durationTicks = 120;
			public int pulseTicks = 10;
			public double damagePerPulse = 2;
			public int fireSeconds = 4;
			public double thickness = 1.0;
		}

		public static class HealingDart {
			public double range = 24;
			/** Half-hearts on the ally hit (× the party curve {@code abilityHeal}). */
			public double heal = 8;
			/** Share healed on yourself when the dart finds no ally. */
			public double selfShare = 0.5;
		}
	}

	public static class Signature {
		public int cooldownTicks = 900;
		public GroundPound groundPound = new GroundPound();
		public WebPull webPull = new WebPull();
		public EmberWave emberWave = new EmberWave();
		public Geyser geyser = new Geyser();
		public TauntSlam tauntSlam = new TauntSlam();
		public FanTheHammer fanTheHammer = new FanTheHammer();
		public Overclock overclock = new Overclock();
		public Transfusion transfusion = new Transfusion();

		public static class GroundPound {
			public double slamSpeed = 1.6;
			public int maxFallTicks = 40;
			public double radius = 4;
			public double damage = 9;
			public double knockup = 0.7;
		}

		public static class WebPull {
			public double range = 20;
			/** Velocity given to a pulled mob, toward you. */
			public double mobPull = 1.3;
			/** Velocity given to you toward a hit block. */
			public double selfPull = 1.4;
		}

		public static class EmberWave {
			public double range = 7;
			public double coneDegrees = 70;
			public double damage = 7;
			public int fireSeconds = 5;
		}

		public static class Geyser {
			public double radius = 3.5;
			public double launch = 1.3;
			public int slowFallTicks = 80;
			public double mobDamage = 4;
		}

		public static class TauntSlam {
			public double radius = 8;
			public double damage = 4;
			public double knockback = 1.2;
			public int resistanceTicks = 120;
			public int resistanceAmplifier = 1;
		}

		public static class FanTheHammer {
			public int shots = 6;
			public double damage = 7;
			public double range = 40;
			public double spreadDegrees = 3;
		}

		public static class Overclock {
			public int durationTicks = 160;
			public int zapEveryTicks = 8;
			public double zapDamage = 4;
			public double zapRange = 10;
		}

		public static class Transfusion {
			public double radius = 5;
			public double drainDamage = 5;
			/** Share of the damage dealt that comes back as healing, split over you and allies in the radius. */
			public double healShare = 0.6;
		}
	}

	public static class Ultimate {
		public StormBarrage stormBarrage = new StormBarrage();
		public MeleeFrenzy meleeFrenzy = new MeleeFrenzy();
		public PartyOvershield partyOvershield = new PartyOvershield();

		public static class StormBarrage {
			public int durationTicks = 160;
			public int strikeEveryTicks = 6;
			public double radius = 12;
			public double damage = 9;
		}

		public static class MeleeFrenzy {
			public int durationTicks = 200;
			public int strikeEveryTicks = 5;
			public double reach = 3.5;
			public double damage = 11;
			public double knockback = 1.0;
			public int speedAmplifier = 1;
		}

		public static class PartyOvershield {
			/** Absorption half-hearts per player (× the party curve {@code abilityHeal}). */
			public double absorption = 8;
			public int durationTicks = 400;
			/** Allies within this many blocks (same dimension) get it; 0 = everyone in the dimension. */
			public double radius = 32;
		}
	}

	public static class Hud {
		public boolean enabled = true;
		/** Pixels from the bottom-left corner. */
		public int marginX = 8;
		public int marginY = 8;
		public boolean readyChime = true;
	}

	private static Map<String, String> defaultSignatures() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("blocksouls_game:drifter", "ground_pound");
		map.put("blocksouls_game:webslinger", "web_pull");
		map.put("blocksouls_game:ashborn", "ember_wave");
		map.put("blocksouls_game:tidecaller", "geyser");
		map.put("blocksouls_game:juggernaut", "taunt_slam");
		map.put("blocksouls_game:gunslinger", "fan_the_hammer");
		map.put("blocksouls_game:tinkerer", "overclock");
		map.put("blocksouls_game:bloodbound", "transfusion");
		map.put("blocksouls_game:zephyr", "geyser");
		map.put("blocksouls_game:gambler", "fan_the_hammer");
		return map;
	}

	/**
	 * Brings a file written by an older version up to the current defaults. Returns whether anything changed. Hand-
	 * tuned values are kept: only fields whose shipped default moved between versions are rewritten.
	 */
	public boolean migrate() {
		boolean changed = false;
		if (defaultsVersion < 1) {
			defaultsVersion = 1;
			changed = true;
		}
		return changed;
	}
}
