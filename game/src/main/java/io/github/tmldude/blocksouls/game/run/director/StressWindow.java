package io.github.tmldude.blocksouls.game.run.director;

/**
 * The stress model's rolling window: per-second buckets of damage taken, downs and damage dealt over the last
 * {@code seconds}. Events land in the current bucket; {@link #roll()} once a second starts a fresh one and
 * forgets the oldest. Pure, no Minecraft types.
 */
public final class StressWindow {
	private double[] damage;
	private double[] dealt;
	private int[] downs;
	private int head;

	public StressWindow(int seconds) {
		allocate(seconds);
	}

	private void allocate(int seconds) {
		int size = Math.max(2, seconds);
		damage = new double[size];
		dealt = new double[size];
		downs = new int[size];
		head = 0;
	}

	public int seconds() {
		return damage.length;
	}

	/** Changes the window length (clears it). */
	public void resize(int seconds) {
		if (Math.max(2, seconds) != damage.length) {
			allocate(seconds);
		}
	}

	public void clear() {
		allocate(damage.length);
	}

	public void addDamage(double amount) {
		if (amount > 0) {
			damage[head] += amount;
		}
	}

	public void addDealt(double amount) {
		if (amount > 0) {
			dealt[head] += amount;
		}
	}

	public void addDown() {
		downs[head]++;
	}

	/** A second passed: the oldest bucket is dropped and becomes the new current one. */
	public void roll() {
		head = (head + 1) % damage.length;
		damage[head] = 0;
		dealt[head] = 0;
		downs[head] = 0;
	}

	public double damage() {
		double sum = 0;
		for (double value : damage) {
			sum += value;
		}
		return sum;
	}

	public int downs() {
		int sum = 0;
		for (int value : downs) {
			sum += value;
		}
		return sum;
	}

	/** Damage dealt per second over the newer half of the window. */
	public double dpsRecent() {
		return halfAverage(true);
	}

	/** Damage dealt per second over the older half of the window. */
	public double dpsEarlier() {
		return halfAverage(false);
	}

	private double halfAverage(boolean recent) {
		int size = dealt.length;
		int half = size / 2;
		double sum = 0;
		for (int age = recent ? 0 : half; age < (recent ? half : size); age++) {
			sum += dealt[Math.floorMod(head - age, size)];
		}
		int count = recent ? half : size - half;
		return sum / Math.max(1, count);
	}
}
