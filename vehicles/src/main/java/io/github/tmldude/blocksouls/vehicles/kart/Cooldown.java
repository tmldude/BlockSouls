package io.github.tmldude.blocksouls.vehicles.kart;

/** A rate limit keyed on game time. Mutable but Minecraft-free so it can be unit-tested. */
public final class Cooldown {
	private long lastUsed = Long.MIN_VALUE;

	/** Whether it's ready at {@code now}, i.e. at least {@code ticks} have passed since the last use. */
	public boolean ready(long now, int ticks) {
		return lastUsed == Long.MIN_VALUE || now - lastUsed >= ticks;
	}

	/** Uses it if ready; returns whether it was. */
	public boolean tryUse(long now, int ticks) {
		if (!ready(now, ticks)) {
			return false;
		}
		lastUsed = now;
		return true;
	}

	public void reset() {
		lastUsed = Long.MIN_VALUE;
	}
}
