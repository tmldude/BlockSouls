package io.github.tmldude.blocksouls.vehicles.kart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.vehicles.VehiclesConfig;

class KartPhysicsTest {
	private final VehiclesConfig.Kart cfg = new VehiclesConfig.Kart();

	@Test
	void acceleratesToTopSpeedAndStaysThere() {
		assertEquals(0.4, KartPhysics.speedAfter(0, 10, cfg), 1e-9);
		// 0.75 / 0.04 = 18.75 ticks to top speed.
		assertEquals(cfg.maxSpeed, KartPhysics.speedAfter(0, 19, cfg), 1e-9);
		assertEquals(cfg.maxSpeed, KartPhysics.speedAfter(0, 100, cfg), 1e-9);
	}

	@Test
	void brakingIsStrongerThanAccelerating() {
		double braked = KartPhysics.forwardSpeed(0.5, -1, cfg.maxSpeed, cfg);
		double accelerated = KartPhysics.forwardSpeed(0.5, 1, cfg.maxSpeed, cfg);
		assertTrue(0.5 - braked > accelerated - 0.5);
		assertEquals(0, KartPhysics.forwardSpeed(0.05, -1, cfg.maxSpeed, cfg), 1e-9, "braking stops at 0, doesn't flip into reverse");
	}

	@Test
	void reverseIsSlowerThanForward() {
		double speed = 0;
		for (int i = 0; i < 100; i++) {
			speed = KartPhysics.forwardSpeed(speed, -1, cfg.maxSpeed, cfg);
		}
		assertEquals(-cfg.maxReverseSpeed, speed, 1e-9);
		assertTrue(cfg.maxReverseSpeed < cfg.maxSpeed);
	}

	@Test
	void coastingSlowsToAStop() {
		double speed = cfg.maxSpeed;
		for (int i = 0; i < 300; i++) {
			speed = KartPhysics.forwardSpeed(speed, 0, cfg.maxSpeed, cfg);
		}
		assertEquals(0, speed);
	}

	@Test
	void waterCapsSpeed() {
		assertEquals(cfg.waterMaxSpeed, KartPhysics.forwardSpeed(cfg.waterMaxSpeed, 1, cfg.waterMaxSpeed, cfg), 1e-9);
		assertTrue(KartPhysics.forwardSpeed(cfg.maxSpeed, 1, cfg.waterMaxSpeed, cfg) < cfg.maxSpeed, "a fast kart stalls in water");
	}

	@Test
	void boostDecaysBackToTopSpeed() {
		double speed = cfg.maxSpeed + KartPhysics.driftBoost(60, cfg);
		double next = KartPhysics.forwardSpeed(speed, 1, cfg.maxSpeed, cfg);
		assertTrue(next < speed && next > cfg.maxSpeed);
		for (int i = 0; i < 200; i++) {
			speed = KartPhysics.forwardSpeed(speed, 1, cfg.maxSpeed, cfg);
		}
		assertEquals(cfg.maxSpeed, speed, 1e-9);
	}

	@Test
	void steeringFadesAtLowSpeedAndFlipsInReverse() {
		assertEquals(0, KartPhysics.yawRate(0, 1, false, true, cfg));
		assertEquals(cfg.turnRate, KartPhysics.yawRate(cfg.maxSpeed, 1, false, true, cfg), 1e-9);
		assertEquals(cfg.turnRate / 2, KartPhysics.yawRate(cfg.fullTurnSpeed / 2, 1, false, true, cfg), 1e-9);
		assertTrue(KartPhysics.yawRate(-0.2, 1, false, true, cfg) < 0, "reversing turns the other way");
		assertTrue(KartPhysics.yawRate(0.5, -1, true, true, cfg) < -cfg.turnRate, "drifting turns tighter");
		assertTrue(Math.abs(KartPhysics.yawRate(0.5, 1, false, false, cfg)) < cfg.turnRate, "less control in the air");
	}

	@Test
	void driftNeedsSprintSpeedAndSteeringToStart() {
		assertTrue(KartPhysics.drifting(false, true, 1, 0.5, cfg));
		assertFalse(KartPhysics.drifting(false, true, 0, 0.5, cfg), "no steering, no drift");
		assertFalse(KartPhysics.drifting(false, false, 1, 0.5, cfg), "no sprint, no drift");
		assertFalse(KartPhysics.drifting(false, true, 1, 0.1, cfg), "too slow");
		assertTrue(KartPhysics.drifting(true, true, 0, 0.5, cfg), "a drift continues while sprint is held");
		assertTrue(KartPhysics.drifting(true, true, 1, cfg.minDriftSpeed * 0.8, cfg), "a drift tolerates some slowdown");
	}

	@Test
	void driftSlidesMoreThanGripping() {
		assertTrue(KartPhysics.sidewaysKept(true, false, true, cfg) > KartPhysics.sidewaysKept(false, false, true, cfg));
		assertTrue(KartPhysics.sidewaysKept(false, true, true, cfg) > KartPhysics.sidewaysKept(true, false, true, cfg), "ice slides most");
		assertEquals(1, KartPhysics.sidewaysKept(false, false, false, cfg), "no grip in the air");
	}

	@Test
	void driftBoostNeedsAtLeastOneSecond() {
		assertEquals(0, KartPhysics.driftBoost(19, cfg));
		assertEquals(cfg.driftBoosts[0], KartPhysics.driftBoost(20, cfg));
		assertEquals(cfg.driftBoosts[1], KartPhysics.driftBoost(200, cfg));
		assertEquals(0, KartPhysics.driftTier(5, cfg));
		assertEquals(2, KartPhysics.driftTier(50, cfg));
	}

	@Test
	void landingBouncesOnlyFromHighEnough() {
		assertEquals(0, KartPhysics.landingBounce(-0.1, cfg));
		assertEquals(0.5 * cfg.landingBounce, KartPhysics.landingBounce(-0.5, cfg), 1e-9);
	}

	@Test
	void ramDamageIsSpeedTimesTwenty() {
		assertEquals(0, KartPhysics.ramDamage(0.3, cfg));
		assertEquals(15, KartPhysics.ramDamage(0.75, cfg), 1e-5);
		assertEquals(8, KartPhysics.ramDamage(0.4, cfg), 1e-5);
	}

	@Test
	void headingMatchesMinecraftYaw() {
		double[] south = KartPhysics.heading(0);
		assertEquals(0, south[0], 1e-9);
		assertEquals(1, south[1], 1e-9);
		double[] west = KartPhysics.heading(90);
		assertEquals(-1, west[0], 1e-9);
		assertEquals(0, west[1], 1e-9);
	}
}
