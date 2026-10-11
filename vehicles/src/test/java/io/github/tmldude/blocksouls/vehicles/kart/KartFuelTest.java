package io.github.tmldude.blocksouls.vehicles.kart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.vehicles.VehiclesConfig;

class KartFuelTest {
	private final VehiclesConfig.Fuel cfg = new VehiclesConfig.Fuel();

	@Test
	void throttleBurnsAndIdleDoesnt() {
		assertEquals(99, KartFuel.burn(100, 1, cfg));
		assertEquals(99, KartFuel.burn(100, -1, cfg));
		assertEquals(100, KartFuel.burn(100, 0, cfg));
		assertEquals(0, KartFuel.burn(0, 1, cfg));
	}

	@Test
	void fullTankLastsCapacityTicksOfThrottle() {
		int fuel = cfg.fuelCapacity;
		int ticks = 0;
		while (fuel > 0) {
			fuel = KartFuel.burn(fuel, 1, cfg);
			ticks++;
		}
		assertEquals(6000, ticks);
	}

	@Test
	void emptyTankCoastsButStillBrakes() {
		assertEquals(1, KartFuel.effectiveThrottle(1, 0.5, 10));
		assertEquals(0, KartFuel.effectiveThrottle(1, 0.5, 0));
		assertEquals(-1, KartFuel.effectiveThrottle(-1, 0.5, 0), "braking while rolling forward needs no fuel");
		assertEquals(0, KartFuel.effectiveThrottle(-1, 0, 0), "but reversing does");
		assertEquals(0, KartFuel.effectiveThrottle(-1, -0.1, 0));
	}

	@Test
	void refuelAddsAndCapsAtAFullTank() {
		assertEquals(700, KartFuel.refuel(100, 600, cfg));
		assertEquals(cfg.fuelCapacity, KartFuel.refuel(5000, 5400, cfg));
		assertTrue(KartFuel.canRefuel(5999, 600, cfg));
		assertFalse(KartFuel.canRefuel(cfg.fuelCapacity, 600, cfg), "a full tank takes nothing");
		assertFalse(KartFuel.canRefuel(0, 0, cfg), "not a fuel");
	}

	@Test
	void defaultItemValues() {
		assertEquals(600, cfg.items.get("minecraft:coal"));
		assertEquals(600, cfg.items.get("minecraft:charcoal"));
		assertEquals(5400, cfg.items.get("minecraft:coal_block"));
		assertEquals(1200, cfg.items.get("minecraft:blaze_rod"));
		assertEquals(3000, cfg.items.get("blocksouls_vehicles:fuel_can"));
	}

	@Test
	void repairKeepsTheBrokenKartsFuelOrStartsAQuarterFull() {
		assertEquals(1500, KartFuel.afterRepair(KartFuel.UNKNOWN, cfg));
		assertEquals(1234, KartFuel.afterRepair(1234, cfg));
		assertEquals(0, KartFuel.afterRepair(0, cfg), "an empty kart stays empty");
	}

	@Test
	void lowBelowFifteenPercent() {
		assertTrue(KartFuel.low(899, cfg));
		assertFalse(KartFuel.low(900, cfg));
		assertEquals(0.5, KartFuel.share(3000, cfg), 1e-9);
	}
}
