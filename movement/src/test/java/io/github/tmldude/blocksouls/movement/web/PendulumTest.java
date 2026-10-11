package io.github.tmldude.blocksouls.movement.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.web.Pendulum.Vec;

class PendulumTest {
	static final double GRAVITY = 0.08;
	static final Vec ANCHOR = new Vec(0, 20, 0);

	final MovementConfig.Web config = new MovementConfig.Web();

	/** Swings from {@code start} for {@code ticks}, moving by the returned velocity each tick like the player does. */
	class Swing {
		Vec pos;
		Vec vel = Vec.ZERO;
		double length;
		double maxStretch;

		Swing(Vec start) {
			pos = start;
			length = Pendulum.initialLength(start.subtract(ANCHOR).length(), config);
		}

		void run(int ticks, boolean reel) {
			for (int i = 0; i < ticks; i++) {
				Pendulum.Step step = Pendulum.step(pos, vel, ANCHOR, length, reel, GRAVITY, config);
				vel = step.velocity();
				length = step.length();
				pos = pos.add(vel);
				maxStretch = Math.max(maxStretch, pos.subtract(ANCHOR).length() - length);
			}
		}

		double energy() {
			return 0.5 * vel.dot(vel) + GRAVITY * pos.y();
		}
	}

	@Test
	void ropeKeepsItsLength() {
		Swing swing = new Swing(new Vec(10, 20, 0));
		swing.run(400, false);
		assertTrue(swing.maxStretch < 0.05, "rope stretched by " + swing.maxStretch);
		assertEquals(10, swing.length, 1e-9);
	}

	@Test
	void energyRoughlyConservedWithoutBoostOrDrag() {
		config.swingBoost = 0;
		config.drag = 1.0;
		Swing swing = new Swing(new Vec(10, 20, 0));
		double start = swing.energy();
		double minEnergy = start;
		double maxEnergy = start;
		// About one full period there and back.
		for (int i = 0; i < 80; i++) {
			swing.run(1, false);
			minEnergy = Math.min(minEnergy, swing.energy());
			maxEnergy = Math.max(maxEnergy, swing.energy());
		}
		// Energy relative to the bottom of the arc, so the tolerance means something.
		double swingEnergy = GRAVITY * 10;
		assertTrue(start - minEnergy < 0.1 * swingEnergy, "lost " + (start - minEnergy));
		assertTrue(maxEnergy - start < 0.1 * swingEnergy, "gained " + (maxEnergy - start));
	}

	@Test
	void swingsThroughTheBottomAndUpTheOtherSide() {
		config.swingBoost = 0;
		config.drag = 1.0;
		Swing swing = new Swing(new Vec(10, 20, 0));
		double minX = 10;
		for (int i = 0; i < 80; i++) {
			swing.run(1, false);
			minX = Math.min(minX, swing.pos.x());
		}
		assertTrue(minX < -8, "only reached x=" + minX);
	}

	@Test
	void boostAddsEnergyOnTheDownswing() {
		config.drag = 1.0;
		Swing boosted = new Swing(new Vec(10, 20, 0));
		double start = boosted.energy();
		boosted.run(20, false);
		assertTrue(boosted.energy() > start + 0.1, "boost should pump the swing");
	}

	@Test
	void defaultSwingKeepsGoingDespiteDrag() {
		Swing swing = new Swing(new Vec(10, 20, 0));
		double maxHeight = 0;
		for (int i = 0; i < 300; i++) {
			swing.run(1, false);
			if (i > 200) {
				maxHeight = Math.max(maxHeight, swing.pos.y());
			}
		}
		// Still swinging most of the way up after 10 s, not dangling at the bottom.
		assertTrue(maxHeight > 17, "peak height after 10 s " + maxHeight);
	}

	@Test
	void reelingShortensToTheMinimum() {
		Swing swing = new Swing(new Vec(0, 10, 0));
		swing.run(1, true);
		assertEquals(10 - config.reelSpeed, swing.length, 1e-9);
		swing.run(200, true);
		assertEquals(config.minLength, swing.length, 1e-9);
		assertTrue(swing.pos.subtract(ANCHOR).length() < config.minLength + 0.5, "pulled up the rope");
	}

	@Test
	void speedIsCapped() {
		Pendulum.Step step = Pendulum.step(new Vec(0, 10, 0), new Vec(20, -20, 0), ANCHOR, 10, false, GRAVITY, config);
		assertTrue(step.velocity().length() <= config.maxSpeed + 1e-9);
	}

	@Test
	void slackRopeIsFreeFall() {
		Vec vel = new Vec(0.3, 0.5, 0);
		Pendulum.Step step = Pendulum.step(new Vec(0, 15, 0), vel, ANCHOR, 10, false, GRAVITY, config);
		assertEquals(0.3 * config.drag, step.velocity().x(), 1e-9);
		assertEquals((0.5 - GRAVITY) * config.drag, step.velocity().y(), 1e-9);
	}

	@Test
	void shortShotGetsTheMinimumLength() {
		assertEquals(config.minLength, Pendulum.initialLength(1, config));
		assertEquals(12, Pendulum.initialLength(12, config));
	}

	@Test
	void releaseHops() {
		Vec released = Pendulum.release(new Vec(1, 0, 0), config);
		assertEquals(config.releaseBoost, released.y(), 1e-9);
		assertEquals(1, released.x(), 1e-9);
	}

	@Test
	void serverDropsDistantPlayers() {
		assertTrue(Pendulum.isTooFar(10 + config.serverSlack + 0.1, 10, config));
		assertTrue(!Pendulum.isTooFar(10 + config.serverSlack - 0.1, 10, config));
	}
}
