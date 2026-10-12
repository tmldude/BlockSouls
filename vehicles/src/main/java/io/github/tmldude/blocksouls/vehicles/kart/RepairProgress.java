package io.github.tmldude.blocksouls.vehicles.kart;

import io.github.tmldude.blocksouls.vehicles.VehiclesConfig;

/** Parts fitted to a wreck so far. Immutable; pure logic so it can be unit-tested. */
public record RepairProgress(int wheels, int engines, int scrap) {
	public static final RepairProgress EMPTY = new RepairProgress(0, 0, 0);

	public enum Part {
		WHEEL, ENGINE, SCRAP
	}

	/** Whether one more {@code part} is still needed. */
	public boolean needs(Part part, VehiclesConfig.Wreck cfg) {
		return switch (part) {
			case WHEEL -> wheels < cfg.wheels;
			case ENGINE -> engines < cfg.engines;
			case SCRAP -> scrap < cfg.scrap;
		};
	}

	/** This progress with one more {@code part}, or {@code this} if that part is already complete. */
	public RepairProgress add(Part part, VehiclesConfig.Wreck cfg) {
		if (!needs(part, cfg)) {
			return this;
		}
		return switch (part) {
			case WHEEL -> new RepairProgress(wheels + 1, engines, scrap);
			case ENGINE -> new RepairProgress(wheels, engines + 1, scrap);
			case SCRAP -> new RepairProgress(wheels, engines, scrap + 1);
		};
	}

	public boolean complete(VehiclesConfig.Wreck cfg) {
		return wheels >= cfg.wheels && engines >= cfg.engines && scrap >= cfg.scrap;
	}

	/** What's still missing: per part, how many more it needs (never negative). */
	public RepairProgress remaining(VehiclesConfig.Wreck cfg) {
		return new RepairProgress(Math.max(0, cfg.wheels - wheels), Math.max(0, cfg.engines - engines), Math.max(0, cfg.scrap - scrap));
	}

	/** Share of all parts fitted, 0..1. */
	public double fraction(VehiclesConfig.Wreck cfg) {
		int total = cfg.wheels + cfg.engines + cfg.scrap;
		if (total <= 0) {
			return 1;
		}
		return (double) (Math.min(wheels, cfg.wheels) + Math.min(engines, cfg.engines) + Math.min(scrap, cfg.scrap)) / total;
	}

	/** What's left of a kart that broke down: its kept wheels and engine, no scrap. */
	public static RepairProgress brokenKart(VehiclesConfig.Kart kart, VehiclesConfig.Wreck cfg) {
		return new RepairProgress(Math.min(kart.wheelsKeptOnBreak, cfg.wheels), kart.engineKeptOnBreak ? cfg.engines : 0, 0);
	}
}
