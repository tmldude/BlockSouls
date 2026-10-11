package io.github.tmldude.blocksouls.bosses.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.bosses.logic.PhaseMath;

/**
 * Everything static about a boss kind: id (from {@code CommonIds}), base health, arena radius, music and phases.
 * Built once at init from the boss's config; shared by every instance and by the client (which needs the attack
 * names and phase thresholds).
 */
public final class BossDefinition {
	private final Identifier id;
	private final double baseHealth;
	private final int arenaRadius;
	private final @Nullable SoundEvent music;
	private final List<BossPhase> phases;
	private final List<Attack> attacks;
	private final double[] thresholds;
	private final double chaseSpeed;

	private BossDefinition(Builder builder) {
		this.id = builder.id;
		this.baseHealth = builder.baseHealth;
		this.arenaRadius = builder.arenaRadius;
		this.music = builder.music;
		this.phases = List.copyOf(builder.phases);
		this.chaseSpeed = builder.chaseSpeed;
		Map<String, Attack> byName = new LinkedHashMap<>();
		for (BossPhase phase : phases) {
			for (Attack attack : phase.attacks()) {
				Attack previous = byName.putIfAbsent(attack.name(), attack);
				if (previous != null && previous != attack) {
					throw new IllegalArgumentException(id + ": two different attacks named " + attack.name());
				}
			}
		}
		this.attacks = List.copyOf(byName.values());
		this.thresholds = phases.stream().mapToDouble(BossPhase::threshold).toArray();
		if (phases.isEmpty() || !PhaseMath.valid(thresholds)) {
			throw new IllegalArgumentException(id + ": phases need decreasing thresholds starting at 1.0");
		}
		if (attacks.isEmpty()) {
			throw new IllegalArgumentException(id + ": a boss needs at least one attack");
		}
	}

	public static Builder builder(Identifier id) {
		return new Builder(id);
	}

	public Identifier id() {
		return id;
	}

	public double baseHealth() {
		return baseHealth;
	}

	public int arenaRadius() {
		return arenaRadius;
	}

	public @Nullable SoundEvent music() {
		return music;
	}

	public List<BossPhase> phases() {
		return phases;
	}

	public BossPhase phase(int index) {
		return phases.get(Math.clamp(index, 0, phases.size() - 1));
	}

	/** Every attack in any phase, in order of first appearance. Indices are what the client receives. */
	public List<Attack> attacks() {
		return attacks;
	}

	public int indexOf(Attack attack) {
		return attacks.indexOf(attack);
	}

	public @Nullable Attack attack(int index) {
		return index >= 0 && index < attacks.size() ? attacks.get(index) : null;
	}

	/** Phase start thresholds (fractions of max health); index 0 is 1.0. */
	public double[] thresholds() {
		return thresholds.clone();
	}

	/** Navigation speed multiplier while chasing. */
	public double chaseSpeed() {
		return chaseSpeed;
	}

	public static final class Builder {
		private final Identifier id;
		private double baseHealth = 200;
		private int arenaRadius = 16;
		private @Nullable SoundEvent music;
		private final List<BossPhase> phases = new ArrayList<>();
		private double chaseSpeed = 1.0;

		private Builder(Identifier id) {
			this.id = id;
		}

		/** Max health before scaling. Also register it as the entity type's {@code MAX_HEALTH} attribute. */
		public Builder health(double health) {
			this.baseHealth = health;
			return this;
		}

		/** Fog wall radius around the boss's home; walking inside it engages. */
		public Builder arenaRadius(int radius) {
			this.arenaRadius = radius;
			return this;
		}

		public Builder music(SoundEvent music) {
			this.music = music;
			return this;
		}

		public Builder chaseSpeed(double speed) {
			this.chaseSpeed = speed;
			return this;
		}

		public Builder phase(BossPhase phase) {
			this.phases.add(phase);
			return this;
		}

		public Builder phase(BossPhase.Builder phase) {
			return phase(phase.build());
		}

		public BossDefinition build() {
			return new BossDefinition(this);
		}
	}
}
