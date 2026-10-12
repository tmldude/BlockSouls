package io.github.tmldude.blocksouls.vehicles.kart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.vehicles.VehiclesConfig;

class RepairProgressTest {
	private final VehiclesConfig.Wreck cfg = new VehiclesConfig.Wreck();

	private RepairProgress add(RepairProgress progress, RepairProgress.Part part, int times) {
		for (int i = 0; i < times; i++) {
			progress = progress.add(part, cfg);
		}
		return progress;
	}

	@Test
	void allPartsCompleteTheRepair() {
		RepairProgress progress = add(RepairProgress.EMPTY, RepairProgress.Part.WHEEL, 4);
		progress = add(progress, RepairProgress.Part.ENGINE, 1);
		assertFalse(progress.complete(cfg));
		progress = add(progress, RepairProgress.Part.SCRAP, 8);
		assertTrue(progress.complete(cfg));
		assertEquals(1, progress.fraction(cfg), 1e-9);
	}

	@Test
	void partialPartsDont() {
		RepairProgress progress = add(RepairProgress.EMPTY, RepairProgress.Part.WHEEL, 3);
		progress = add(progress, RepairProgress.Part.ENGINE, 1);
		progress = add(progress, RepairProgress.Part.SCRAP, 8);
		assertFalse(progress.complete(cfg));
		assertTrue(progress.needs(RepairProgress.Part.WHEEL, cfg));
	}

	@Test
	void extraPartsAreRefused() {
		RepairProgress full = add(RepairProgress.EMPTY, RepairProgress.Part.ENGINE, 1);
		assertFalse(full.needs(RepairProgress.Part.ENGINE, cfg));
		assertSame(full, full.add(RepairProgress.Part.ENGINE, cfg));
	}

	@Test
	void brokenKartKeepsSomeParts() {
		VehiclesConfig.Kart kart = new VehiclesConfig.Kart();
		RepairProgress broken = RepairProgress.brokenKart(kart, cfg);
		assertEquals(new RepairProgress(2, 1, 0), broken);
		assertFalse(broken.complete(cfg));
	}

	@Test
	void remainingCountsWhatsMissing() {
		RepairProgress progress = add(RepairProgress.EMPTY, RepairProgress.Part.WHEEL, 2);
		progress = add(progress, RepairProgress.Part.SCRAP, 3);
		assertEquals(new RepairProgress(2, 1, 5), progress.remaining(cfg));
		assertEquals(new RepairProgress(0, 0, 0), new RepairProgress(9, 9, 9).remaining(cfg));
	}
}
