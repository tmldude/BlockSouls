package io.github.tmldude.blocksouls.movement;

/** {@code config/blocksouls/movement.json}. Velocities are blocks/tick, times are ticks. */
public class MovementConfig {
	public Jump jump = new Jump();

	public static class Jump {
		public boolean enabled = true;
		/** Launch velocity multiplier for a tapped jump; holding jump adds {@link #holdBoost} on top. */
		public double tapJumpMult = 0.85;
		/** Upward velocity added each tick jump is held while rising, up to {@link #maxHoldTicks}. */
		public double holdBoost = 0.03;
		public int maxHoldTicks = 8;
		/** Gravity multiplier while falling (1.0 = vanilla). */
		public double fallGravityMult = 1.6;
		/** Extra fall gravity stops adding speed past this (vanilla terminal velocity is ~3.92). */
		public double maxFallSpeed = 3.9;
		/** Ticks after walking off a ledge during which a jump still works. */
		public int coyoteTicks = 3;
		/** Ticks before landing during which a jump press is remembered and fired on landing. */
		public int jumpBufferTicks = 5;
		public boolean tripleJump = true;
		/** Max ticks on the ground between jumps for the chain to continue. */
		public int tripleJumpWindowTicks = 10;
		/** Launch bonus for the 2nd and 3rd jump of a chain. */
		public double secondJumpBonus = 0.15;
		public double thirdJumpBonus = 0.30;
	}
}
