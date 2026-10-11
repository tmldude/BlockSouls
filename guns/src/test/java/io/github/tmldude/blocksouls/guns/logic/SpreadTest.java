package io.github.tmldude.blocksouls.guns.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class SpreadTest {
	@Test
	void staysInsideTheCone() {
		Random random = new Random(1);
		double[][] dirs = {{0, 0, 1}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0.3, -0.8, 0.5}};
		for (double[] dir : dirs) {
			for (int i = 0; i < 500; i++) {
				double[] out = Spread.deflect(dir[0], dir[1], dir[2], 6, random.nextDouble(), random.nextDouble());
				assertEquals(1.0, Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]), 1e-9, "unit length");
				assertTrue(Spread.angleBetween(dir, out) <= 6 + 1e-6, "inside the cone");
			}
		}
	}

	@Test
	void edgeOfTheConeIsTheMaxAngle() {
		double[] out = Spread.deflect(0, 0, 1, 4.5, 0.999999, 0.3);
		assertEquals(4.5, Spread.angleBetween(new double[] {0, 0, 1}, out), 1e-3);
	}

	@Test
	void zeroSpreadIsExact() {
		assertArrayEquals(new double[] {0, 0, 1}, Spread.deflect(0, 0, 2, 0, 0.5, 0.5), 1e-12);
		double[] center = Spread.deflect(1, 0, 0, 10, 0, 0.7);
		assertEquals(0, Spread.angleBetween(new double[] {1, 0, 0}, center), 1e-6);
	}

	@Test
	void tighterSpreadWhenAiming() {
		Random random = new Random(7);
		double hip = 0;
		double ads = 0;
		for (int i = 0; i < 200; i++) {
			double r1 = random.nextDouble();
			double r2 = random.nextDouble();
			hip += Spread.angleBetween(new double[] {0, 0, 1}, Spread.deflect(0, 0, 1, 2.5, r1, r2));
			ads += Spread.angleBetween(new double[] {0, 0, 1}, Spread.deflect(0, 0, 1, 0.8, r1, r2));
		}
		assertTrue(ads < hip / 2);
	}
}
