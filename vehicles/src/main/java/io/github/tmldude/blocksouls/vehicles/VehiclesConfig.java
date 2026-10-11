package io.github.tmldude.blocksouls.vehicles;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code config/blocksouls/vehicles.json}. Speeds are blocks/tick, rates are per tick, times are ticks. */
public class VehiclesConfig {
	public Kart kart = new Kart();
	public Wreck wreck = new Wreck();
	public Fuel fuel = new Fuel();
	public Horn horn = new Horn();
	public Hud hud = new Hud();

	public static class Kart {
		public float maxHealth = 40;
		/** Health restored per scrap used on a damaged kart. */
		public float scrapRepairHealth = 10;
		/** Whether players (and their bullets) can damage karts. Creative players can always break one. */
		public boolean playersCanDamage = false;

		public double maxSpeed = 0.75;
		public double acceleration = 0.04;
		/** Deceleration while holding back and still rolling forward. */
		public double braking = 0.08;
		public double reverseAcceleration = 0.02;
		public double maxReverseSpeed = 0.25;
		/** Speed multiplier per tick with no throttle (on the ground). */
		public double coastFriction = 0.96;
		/** Above max speed (after a drift boost), speed decays by this multiplier per tick. */
		public double overspeedDecay = 0.97;

		/** Yaw change per tick at full steering, in degrees. */
		public double turnRate = 5.5;
		/** Steering reaches full strength at this speed; slower karts turn proportionally less. */
		public double fullTurnSpeed = 0.2;
		/** Share of sideways velocity removed per tick: high = grippy, low = slides. */
		public double grip = 0.6;
		public double iceGrip = 0.04;
		/** Block friction above which the ground counts as ice (vanilla: ice 0.98, normal 0.6). */
		public double iceFriction = 0.8;

		/** Hold sprint while steering above this speed to drift. */
		public double minDriftSpeed = 0.3;
		public double driftGrip = 0.12;
		public double driftTurnMultiplier = 1.4;
		/** A drift held at least {@code driftBoostTicks[i]} ticks gives {@code driftBoosts[i]} extra speed on release. */
		public int[] driftBoostTicks = {20, 50};
		public double[] driftBoosts = {0.2, 0.35};

		public double gravity = 0.08;
		public double airDrag = 0.98;
		/** Air steering as a share of ground steering. */
		public double airControl = 0.5;
		public double hopVelocity = 0.32;
		public int hopCooldownTicks = 12;
		/** Landing faster than this bounces back up at {@link #landingBounce} times the fall speed. */
		public double minBounceSpeed = 0.3;
		public double landingBounce = 0.4;
		public float stepHeight = 1.0f;

		public double waterMaxSpeed = 0.08;
		/** Upward speed while sunk deeper than {@link #floatDepth} in water. */
		public double waterBuoyancy = 0.05;
		public double floatDepth = 0.3;

		/** Rams hit mobs only above this speed. */
		public double ramMinSpeed = 0.35;
		public double ramDamagePerSpeed = 20;
		/** Knockback strength per unit of speed. */
		public double ramKnockbackPerSpeed = 2.0;
		public double ramLift = 0.3;
		/** A mob can be rammed by the same kart at most once per this many ticks. */
		public int ramCooldownTicks = 10;

		/** When a kart breaks down into a wreck, the wreck keeps this many wheels and (if true) its engine. */
		public int wheelsKeptOnBreak = 2;
		public boolean engineKeptOnBreak = true;
	}

	/** Fuel is measured in ticks of throttle. */
	public static class Fuel {
		/** A full tank: this many ticks of throttle (6000 = 5 minutes). */
		public int fuelCapacity = 6000;
		/** Fuel burned per tick while holding forward or reverse (idling burns nothing). */
		public int burnPerTick = 1;
		/** A kart repaired from a wreck that never had a tank (world wrecks) starts with this share of a full tank. */
		public double repairedShare = 0.25;
		/** Below this share the gauge flashes and the engine sputters. */
		public double lowShare = 0.15;
		/** Ticks between sputters while low on fuel and on the throttle. */
		public int sputterIntervalTicks = 30;
		/** Fuel added per item when refuelling, by item id. */
		public Map<String, Integer> items = new LinkedHashMap<>();

		{
			items.put("minecraft:coal", 600);
			items.put("minecraft:charcoal", 600);
			items.put("minecraft:coal_block", 5400);
			items.put("minecraft:blaze_rod", 1200);
			items.put("blocksouls_vehicles:fuel_can", 3000);
		}
	}

	public static class Horn {
		/** Ticks between honks (server-checked). */
		public int cooldownTicks = 20;
		/** Blocks away the horn can be heard. */
		public float hearingRange = 48;
		/** Wave mobs this close to the kart flinch away from it. */
		public double flinchRadius = 6;
		/** Knockback strength of the flinch (a zombie hit by a bare hand gets 0.4). */
		public double flinchStrength = 0.8;
		/** Ticks between flinches; honks in between only make noise. */
		public int flinchCooldownTicks = 200;
	}

	/** Client-side display options. */
	public static class Hud {
		/** Fuel gauge next to the hotbar while driving. */
		public boolean showFuelGauge = true;
	}

	public static class Wreck {
		public int wheels = 4;
		public int engines = 1;
		public int scrap = 8;
	}
}
