package io.github.tmldude.blocksouls.mobs.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DreadMeterTest {
	private static final DreadMeter.Settings S = new DreadMeter.Settings(1, 2, 120, 240, 120, 1, 9);

	/** Ticks one second at a time; returns the pack sizes sent, in order. */
	private static List<Integer> run(DreadMeter meter, boolean inNether, int seconds) {
		List<Integer> packs = new ArrayList<>();
		for (int i = 0; i < seconds; i++) {
			int size = meter.tick(inNether, 1, S, 3, 1, 0).packSize();
			if (size > 0) {
				packs.add(size);
			}
		}
		return packs;
	}

	@Test
	void risesOnePerSecondInTheNether() {
		DreadMeter meter = new DreadMeter(0);
		run(meter, true, 30);
		assertEquals(30, meter.dread(), 1e-9);
	}

	@Test
	void decaysTwoPerSecondOutsideAndNeverGoesNegative() {
		DreadMeter meter = new DreadMeter(50);
		run(meter, false, 10);
		assertEquals(30, meter.dread(), 1e-9);
		run(meter, false, 100);
		assertEquals(0, meter.dread(), 1e-9);
	}

	@Test
	void noPackBeforeTheThreshold() {
		DreadMeter meter = new DreadMeter(0);
		assertTrue(run(meter, true, 239).isEmpty());
	}

	@Test
	void firstPackAtThresholdThenEveryIntervalGrowing() {
		DreadMeter meter = new DreadMeter(0);
		assertEquals(List.of(3), run(meter, true, 240));
		assertEquals(List.of(4), run(meter, true, 120));
		assertEquals(List.of(5), run(meter, true, 120));
		assertEquals(3, meter.packsThisVisit());
	}

	@Test
	void packsCapOut() {
		DreadMeter meter = new DreadMeter(0);
		List<Integer> packs = run(meter, true, 240 + 120 * 10);
		assertEquals(9, packs.getLast());
	}

	@Test
	void leavingResetsThePackTimerNotTheMeter() {
		DreadMeter meter = new DreadMeter(0);
		run(meter, true, 300); // first pack at 240, timer at 60
		run(meter, false, 5); // dread 300 -> 290
		assertEquals(290, meter.dread(), 1e-9);
		assertEquals(0, meter.packsThisVisit());

		// Back in: already past the threshold, so the next pack waits a full interval, at base size.
		assertTrue(run(meter, true, 119).isEmpty());
		assertEquals(List.of(3), run(meter, true, 1));
	}

	@Test
	void howlsOnlyPastHowlThreshold() {
		DreadMeter meter = new DreadMeter(100);
		assertFalse(meter.tick(true, 1, S, 3, 0, 1).howl());
		DreadMeter loud = new DreadMeter(130);
		assertTrue(loud.tick(true, 1, S, 3, 0, 1).howl());
		assertFalse(loud.tick(true, 1, S, 3, 0.5, 0.1).howl());
	}

	@Test
	void intensityGrowsWithDread() {
		assertEquals(0, DreadMeter.intensity(10, S), 1e-9);
		assertEquals(0.5, DreadMeter.intensity(120, S), 1e-9);
		assertEquals(1, DreadMeter.intensity(500, S), 1e-9);
	}
}
