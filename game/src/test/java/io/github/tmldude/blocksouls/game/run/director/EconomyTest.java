package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EconomyTest {
	@Test
	void pylonCircleShrinksLinearlyAndClampsAtTheEnds() {
		DirectorConfig.Pylon pylon = new DirectorConfig.Pylon();
		pylon.seconds = 60;
		pylon.startRadius = 12;
		pylon.endRadius = 3;
		assertEquals(12, DirectorRules.pylonRadius(-5, pylon), 1e-9);
		assertEquals(12, DirectorRules.pylonRadius(0, pylon), 1e-9);
		assertEquals(7.5, DirectorRules.pylonRadius(30, pylon), 1e-9);
		assertEquals(3, DirectorRules.pylonRadius(60, pylon), 1e-9);
		assertEquals(3, DirectorRules.pylonRadius(600, pylon), 1e-9);
	}
}
