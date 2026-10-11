package io.github.tmldude.blocksouls.vehicles.kart;

import io.github.tmldude.blocksouls.vehicles.VehiclesConfig;

/**
 * The kart's driving math as pure functions (no Minecraft classes), so it can be unit-tested. Speeds are
 * blocks/tick along the kart's heading (negative = reversing), angles are degrees.
 */
public final class KartPhysics {
	private static final double STOP_SPEED = 0.003;

	private KartPhysics() {
	}

	/**
	 * Forward speed after one tick on the ground.
	 *
	 * @param throttle +1 = W, -1 = S, 0 = neither
	 * @param maxSpeed top speed for this surface (lower in water)
	 */
	public static double forwardSpeed(double speed, int throttle, double maxSpeed, VehiclesConfig.Kart cfg) {
		if (throttle > 0) {
			if (speed < 0) {
				return Math.min(0, speed + cfg.braking);
			}
			if (speed > maxSpeed) {
				return Math.max(maxSpeed, speed * cfg.overspeedDecay);
			}
			return Math.min(maxSpeed, speed + cfg.acceleration);
		}
		if (throttle < 0) {
			if (speed > 0) {
				return Math.max(0, speed - cfg.braking);
			}
			return Math.max(-Math.min(cfg.maxReverseSpeed, maxSpeed), speed - cfg.reverseAcceleration);
		}
		double coasted = speed * cfg.coastFriction;
		if (Math.abs(speed) > maxSpeed) {
			coasted = Math.copySign(Math.max(maxSpeed, Math.abs(speed) * cfg.overspeedDecay * cfg.coastFriction), speed);
		}
		return Math.abs(coasted) < STOP_SPEED ? 0 : coasted;
	}

	/** Speed after holding W from {@code speed} for {@code ticks} ticks. */
	public static double speedAfter(double speed, int ticks, VehiclesConfig.Kart cfg) {
		for (int i = 0; i < ticks; i++) {
			speed = forwardSpeed(speed, 1, cfg.maxSpeed, cfg);
		}
		return speed;
	}

	/**
	 * Yaw change this tick. Steering flips while reversing (like a car) and fades out at low speed, so a
	 * parked kart can't spin on the spot.
	 *
	 * @param steer +1 = D (yaw increases, turning right), -1 = A, 0 = none
	 */
	public static double yawRate(double speed, int steer, boolean drifting, boolean onGround, VehiclesConfig.Kart cfg) {
		if (steer == 0 || speed == 0) {
			return 0;
		}
		double strength = Math.min(1, Math.abs(speed) / cfg.fullTurnSpeed);
		double rate = steer * cfg.turnRate * strength * Math.signum(speed);
		if (drifting) {
			rate *= cfg.driftTurnMultiplier;
		}
		if (!onGround) {
			rate *= cfg.airControl;
		}
		return rate;
	}

	/** Share of sideways velocity kept after one tick (1 = slides forever, 0 = on rails). */
	public static double sidewaysKept(boolean drifting, boolean onIce, boolean onGround, VehiclesConfig.Kart cfg) {
		if (!onGround) {
			return 1;
		}
		double grip = onIce ? cfg.iceGrip : drifting ? cfg.driftGrip : cfg.grip;
		return 1 - grip;
	}

	/** Whether the driver is drifting this tick: sprint held, fast enough, and (to start one) steering. */
	public static boolean drifting(boolean wasDrifting, boolean sprint, int steer, double speed, VehiclesConfig.Kart cfg) {
		if (!sprint || speed < cfg.minDriftSpeed * (wasDrifting ? 0.7 : 1)) {
			return false;
		}
		return wasDrifting || steer != 0;
	}

	/** Extra speed for releasing a drift that lasted {@code driftTicks} ticks (0 if too short). */
	public static double driftBoost(int driftTicks, VehiclesConfig.Kart cfg) {
		double boost = 0;
		for (int i = 0; i < Math.min(cfg.driftBoostTicks.length, cfg.driftBoosts.length); i++) {
			if (driftTicks >= cfg.driftBoostTicks[i]) {
				boost = Math.max(boost, cfg.driftBoosts[i]);
			}
		}
		return boost;
	}

	/** Boost tier reached after {@code driftTicks} (0 = none yet), for sparks. */
	public static int driftTier(int driftTicks, VehiclesConfig.Kart cfg) {
		int tier = 0;
		for (int i = 0; i < cfg.driftBoostTicks.length; i++) {
			if (driftTicks >= cfg.driftBoostTicks[i]) {
				tier = i + 1;
			}
		}
		return tier;
	}

	/** Upward speed after landing with vertical speed {@code fallSpeed} (negative = falling); 0 = no bounce. */
	public static double landingBounce(double fallSpeed, VehiclesConfig.Kart cfg) {
		return -fallSpeed > cfg.minBounceSpeed ? -fallSpeed * cfg.landingBounce : 0;
	}

	/** Damage a ram at {@code speed} deals (0 below the ram threshold). */
	public static float ramDamage(double speed, VehiclesConfig.Kart cfg) {
		return speed > cfg.ramMinSpeed ? (float) (speed * cfg.ramDamagePerSpeed) : 0;
	}

	public static double ramKnockback(double speed, VehiclesConfig.Kart cfg) {
		return speed * cfg.ramKnockbackPerSpeed;
	}

	/** Heading unit vector {x, z} for a yaw in degrees (Minecraft convention: yaw 0 faces +z). */
	public static double[] heading(double yawDegrees) {
		double rad = Math.toRadians(yawDegrees);
		return new double[] {-Math.sin(rad), Math.cos(rad)};
	}
}
