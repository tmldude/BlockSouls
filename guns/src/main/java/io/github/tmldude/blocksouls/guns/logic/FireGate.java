package io.github.tmldude.blocksouls.guns.logic;

/**
 * Fire-rate limiter in (possibly fractional) ticks. A gun at 140 RPM fires every 8.57 ticks; firing at the first
 * allowed tick keeps the fractional remainder so the average rate stays exact. A small tolerance lets a shot that
 * arrives slightly early (packets bunched into one tick) through without raising the average rate.
 */
public final class FireGate {
	private double nextShot = Double.NEGATIVE_INFINITY;

	public boolean canFire(long now, double tolerance) {
		return now + tolerance >= nextShot;
	}

	/** Fires if allowed and schedules the next shot {@code interval} ticks later. */
	public boolean tryFire(long now, double interval, double tolerance) {
		if (!canFire(now, tolerance)) {
			return false;
		}

		// On time (or early within tolerance): keep the schedule. Late by more than a tick: restart from now.
		double base = now - nextShot < 1.0 ? nextShot : now;
		nextShot = base + interval;
		return true;
	}

	public double nextShot() {
		return nextShot;
	}

	public void reset() {
		nextShot = Double.NEGATIVE_INFINITY;
	}
}
