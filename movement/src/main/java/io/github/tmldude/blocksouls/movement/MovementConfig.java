package io.github.tmldude.blocksouls.movement;

/** {@code config/blocksouls/movement.json}. Velocities are blocks/tick, times are ticks. */
public class MovementConfig {
	public Jump jump = new Jump();
	public Roll roll = new Roll();
	public Climb climb = new Climb();
	public Stamina stamina = new Stamina();

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

	public static class Roll {
		public boolean enabled = true;
		public int durationTicks = 12;
		/** Damage is ignored on ticks [iframeStart, iframeEnd] of the roll (tick 0 = the press). */
		public int iframeStart = 2;
		public int iframeEnd = 8;
		/** Horizontal speed held for the first {@link #fullSpeedTicks}, then eased to 0 by the end. */
		public double speed = 0.6;
		public int fullSpeedTicks = 7;
		public double staminaCost = 25;
	}

	public static class Climb {
		public boolean enabled = true;
		/** Upward speed while climbing; never slows a faster rise (e.g. a jump). */
		public double climbSpeed = 0.25;
		public double staminaPerSecond = 5;
		/** How far ahead (blocks) to look for a wall in the input direction. */
		public double wallReach = 0.05;
		/** Pull up over the top once the wall's top is at most this far above the player's feet. */
		public double ledgeGrabHeight = 1.3;
		/** Upward and forward velocity of the pull-up. */
		public double ledgeBoostUp = 0.45;
		public double ledgeBoostForward = 0.2;
	}

	public static class Stamina {
		public double max = 100;
		public double regenPerSecond = 15;
		/** Ticks after spending stamina before it starts regenerating. */
		public int regenDelayTicks = 20;
	}
}
