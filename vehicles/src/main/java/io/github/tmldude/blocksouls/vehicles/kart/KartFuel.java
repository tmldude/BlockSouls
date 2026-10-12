package io.github.tmldude.blocksouls.vehicles.kart;

import io.github.tmldude.blocksouls.vehicles.VehiclesConfig;

/** Fuel math, in ticks of throttle. Pure logic so it can be unit-tested. */
public final class KartFuel {
	/** A wreck that never was a kart (placed by world gen, spawned by command) has no tank to remember. */
	public static final int UNKNOWN = -1;

	private KartFuel() {
	}

	/** Fuel left after one tick with this throttle (+1 = W, -1 = S, 0 = idle). Idling burns nothing. */
	public static int burn(int fuel, int throttle, VehiclesConfig.Fuel cfg) {
		if (throttle == 0) {
			return fuel;
		}
		return Math.max(0, fuel - cfg.burnPerTick);
	}

	/**
	 * The throttle the engine can actually deliver: everything with fuel, nothing on an empty tank except braking
	 * (S while still rolling forward), which needs no engine.
	 */
	public static int effectiveThrottle(int throttle, double forwardSpeed, int fuel) {
		if (fuel > 0 || (throttle < 0 && forwardSpeed > 0)) {
			return throttle;
		}
		return 0;
	}

	/** Fuel after adding {@code amount}, capped at a full tank. */
	public static int refuel(int fuel, int amount, VehiclesConfig.Fuel cfg) {
		return (int) Math.min((long) Math.max(0, fuel) + Math.max(0, amount), cfg.fuelCapacity);
	}

	/** Whether one item worth {@code amount} can go in (there's room and it's a fuel at all). */
	public static boolean canRefuel(int fuel, int amount, VehiclesConfig.Fuel cfg) {
		return amount > 0 && fuel < cfg.fuelCapacity;
	}

	/** Fuel a kart repaired from a wreck starts with: what the broken kart had, or {@link #repairedStart} if unknown. */
	public static int afterRepair(int storedFuel, VehiclesConfig.Fuel cfg) {
		return storedFuel == UNKNOWN ? repairedStart(cfg) : clamp(storedFuel, cfg);
	}

	public static int repairedStart(VehiclesConfig.Fuel cfg) {
		return clamp((int) Math.round(cfg.fuelCapacity * cfg.repairedShare), cfg);
	}

	public static int clamp(int fuel, VehiclesConfig.Fuel cfg) {
		return Math.clamp(fuel, 0, Math.max(0, cfg.fuelCapacity));
	}

	/** Share of a full tank, 0..1. */
	public static double share(int fuel, VehiclesConfig.Fuel cfg) {
		return cfg.fuelCapacity <= 0 ? 0 : Math.clamp((double) fuel / cfg.fuelCapacity, 0, 1);
	}

	/** Low enough for the gauge to flash and the engine to sputter. */
	public static boolean low(int fuel, VehiclesConfig.Fuel cfg) {
		return share(fuel, cfg) < cfg.lowShare;
	}
}
