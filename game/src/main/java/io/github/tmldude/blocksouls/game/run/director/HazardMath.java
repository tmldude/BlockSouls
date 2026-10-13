package io.github.tmldude.blocksouls.game.run.director;

import java.util.List;
import java.util.function.DoubleSupplier;

import org.jspecify.annotations.Nullable;

import io.github.tmldude.blocksouls.common.party.PartyScaling;

/** The pure rules behind floor hazards (no Minecraft classes, so the unit tests run offline). */
final class HazardMath {
	private HazardMath() {
	}

	/** Storm radius for the party: the solo radius grown with the island ({@code floorSize}). */
	static double stormRadius(double soloRadius, int players) {
		return soloRadius * PartyScaling.floorSize(players);
	}

	/** Surge pack size for the party (at least one). */
	static int surgePack(int soloPack, int players) {
		return (int) Math.max(1, Math.round(soloPack * PartyScaling.directorBurst(players)));
	}

	/** Weighted pick (weights below 1 never come up); null for an empty or weightless list. */
	static FloorHazardsConfig.@Nullable Problem pick(List<FloorHazardsConfig.Problem> problems, DoubleSupplier random) {
		int total = 0;
		for (FloorHazardsConfig.Problem problem : problems) {
			total += Math.max(0, problem.weight);
		}
		if (total == 0) {
			return null;
		}
		double roll = random.getAsDouble() * total;
		for (FloorHazardsConfig.Problem problem : problems) {
			roll -= Math.max(0, problem.weight);
			if (roll < 0 && problem.weight > 0) {
				return problem;
			}
		}
		return problems.getLast();
	}

	/**
	 * A storm circle drifting across the island. It walks toward a target point (a spot near the party, picked by the
	 * caller) and never leaves the island ({@code islandRadius} around the camp at 0,0).
	 */
	static final class Storm {
		double x;
		double z;
		final double radius;
		final double speed;
		final double islandRadius;
		double targetX;
		double targetZ;

		Storm(double x, double z, double radius, double speed, double islandRadius) {
			this.radius = radius;
			this.speed = speed;
			this.islandRadius = islandRadius;
			double[] clamped = clamp(x, z);
			this.x = clamped[0];
			this.z = clamped[1];
			this.targetX = this.x;
			this.targetZ = this.z;
		}

		void aim(double x, double z) {
			double[] clamped = clamp(x, z);
			targetX = clamped[0];
			targetZ = clamped[1];
		}

		/** Moves {@code seconds} worth toward the target; returns whether it arrived. */
		boolean step(double seconds) {
			double dx = targetX - x;
			double dz = targetZ - z;
			double distance = Math.sqrt(dx * dx + dz * dz);
			double move = speed * seconds;
			if (distance <= move) {
				x = targetX;
				z = targetZ;
				return true;
			}
			x += dx / distance * move;
			z += dz / distance * move;
			return false;
		}

		boolean inside(double px, double pz) {
			double dx = px - x;
			double dz = pz - z;
			return dx * dx + dz * dz <= radius * radius;
		}

		/** The point clamped so the whole circle's centre stays over the island. */
		private double[] clamp(double px, double pz) {
			double limit = Math.max(0, islandRadius - radius * 0.5);
			double d = Math.sqrt(px * px + pz * pz);
			if (d <= limit || d == 0) {
				return new double[] {px, pz};
			}
			return new double[] {px / d * limit, pz / d * limit};
		}
	}
}
