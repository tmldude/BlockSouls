package io.github.tmldude.blocksouls.bosses.palerider;

/**
 * Pure geometry and timing for the Pale Rider's moves (no Minecraft classes, unit-tested). Directions are horizontal
 * unit vectors given as {x, z}.
 */
public final class PaleRiderMath {
	private PaleRiderMath() {
	}

	/** Where the lance charge is during its active stage. */
	public enum LanceStep {
		FIRST_PASS, TURN, SECOND_PASS, DONE
	}

	/** Which part of the two-pass lance charge {@code tick} (0-based, into the active stage) falls in. */
	public static LanceStep lanceStep(int tick, int passTicks, int turnTicks) {
		if (tick < passTicks) {
			return LanceStep.FIRST_PASS;
		}
		if (tick < passTicks + turnTicks) {
			return LanceStep.TURN;
		}
		if (tick < 2 * passTicks + turnTicks) {
			return LanceStep.SECOND_PASS;
		}
		return LanceStep.DONE;
	}

	/** Active ticks the lance charge needs: two passes and the turn between them. */
	public static int lanceActiveTicks(int passTicks, int turnTicks) {
		return 2 * passTicks + turnTicks;
	}

	/** {@code count} directions fanned around {@code dir}, {@code spreadDegrees} apart, centered on it. */
	public static double[][] fan(double dirX, double dirZ, int count, double spreadDegrees) {
		double[][] out = new double[Math.max(0, count)][];
		double base = Math.atan2(dirZ, dirX);
		for (int i = 0; i < out.length; i++) {
			double offset = Math.toRadians(spreadDegrees * (i - (count - 1) / 2.0));
			out[i] = new double[] {Math.cos(base + offset), Math.sin(base + offset)};
		}
		return out;
	}

	/**
	 * The spot {@code distance} behind a target at ({@code tx}, {@code tz}), seen from the attacker at ({@code fromX},
	 * {@code fromZ}): on the far side of the target, along the attacker → target line. Returns {x, z}.
	 */
	public static double[] behind(double fromX, double fromZ, double tx, double tz, double distance) {
		double dx = tx - fromX;
		double dz = tz - fromZ;
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1e-4) {
			dx = 0;
			dz = 1;
			length = 1;
		}
		return new double[] {tx + dx / length * distance, tz + dz / length * distance};
	}

	/** Whether the periodic phantom charge starts this tick ({@code ticksInPhase} counts from the phase start). */
	public static boolean phantomChargeDue(int ticksInPhase, int intervalTicks) {
		return intervalTicks > 0 && ticksInPhase > 0 && ticksInPhase % intervalTicks == 0;
	}
}
