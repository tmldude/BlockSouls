package io.github.tmldude.blocksouls.movement;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/**
 * {@code config/blocksouls/movement.json}. Velocities are blocks/tick, times are ticks.
 *
 * <p>One instance lives for the whole game ({@link BlockSoulsMovement#CONFIG}); features keep references to its
 * sections, so new values (server sync, reload) are copied into it with {@link #copyFrom} rather than replacing it.
 */
public class MovementConfig {
	private static final Gson GSON = new Gson();

	public Jump jump = new Jump();
	public Roll roll = new Roll();
	public Climb climb = new Climb();
	public Stamina stamina = new Stamina();
	public Web web = new Web();
	public Traits traits = new Traits();

	public String toJson() {
		return GSON.toJson(this);
	}

	/** Parses a config sent by a server; missing keys keep their defaults. Null if it doesn't parse. */
	public static MovementConfig fromJson(String json) {
		try {
			MovementConfig config = GSON.fromJson(json, MovementConfig.class);
			return config != null ? config : new MovementConfig();
		} catch (JsonParseException e) {
			return null;
		}
	}

	/** Copies every value from {@code other} into this instance and its existing sections. */
	public void copyFrom(MovementConfig other) {
		copyFields(other, this);
	}

	private static void copyFields(Object from, Object to) {
		for (Field field : to.getClass().getFields()) {
			if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
				continue;
			}
			try {
				Object value = field.get(from);
				Object current = field.get(to);
				boolean section = field.getType().getDeclaringClass() == MovementConfig.class;
				if (!section) {
					field.set(to, value);
				} else if (value != null && current != null) {
					copyFields(value, current);
				} else if (value != null) {
					field.set(to, value);
				}
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Can't copy config field " + field, e);
			}
		}
	}

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
		/** Client-only look: how far the camera rolls toward a sideways dodge, and whether the view drops to crouch height. */
		public double cameraTiltDegrees = 10;
		public boolean lowerEyeHeight = true;
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

	public static class Web {
		public boolean enabled = true;
		/** Anchor raycast reach in blocks (before {@code WEB_REACH}). */
		public double range = 32;
		public double staminaCost = 10;
		/** Shortest rope, in blocks: attach length and the reel-in limit. */
		public double minLength = 3;
		/** Rope length removed per tick while holding sneak. */
		public double reelSpeed = 0.3;
		/** Speed added along the swing direction each tick while moving downward on the arc. */
		public double swingBoost = 0.04;
		/** Velocity multiplier per tick while swinging (replaces vanilla's air drag). */
		public double drag = 0.99;
		/** Gravity multiplier while swinging (1.0 = the player's normal gravity): lower floats, higher snaps. */
		public double gravityMult = 1.0;
		/** The rope counts as taut (pulls and pumps) within this many blocks of its full length. */
		public double ropeSlack = 0.05;
		/** Upward velocity added on release. */
		public double releaseBoost = 0.15;
		/** Swing speed cap in blocks/tick (vanilla only kicks above ~10). */
		public double maxSpeed = 2.5;
		/** No fall damage for this many ticks after letting go. */
		public int noFallTicksAfterRelease = 10;
		/** Server drops the swing when the player is further than rope length + this from the anchor. */
		public double serverSlack = 4;
		/** Server accepts anchors up to range × this from its own view of the player. */
		public double serverRangeTolerance = 1.1;
		/** Extra rays tried above the look direction when it misses, in degrees each. */
		public double fallbackRayStepDegrees = 6;
		public int fallbackRays = 3;
	}

	/** How {@code PlayerTraits} levels scale movement. Every level stacks. */
	public static class Traits {
		/** Extra mid-air jumps per {@code DOUBLE_JUMP} level. */
		public int airJumpsPerLevel = 1;
		/** Ticks added to the end of the roll's i-frame window per {@code ROLL_IFRAMES} level. */
		public int rollIframeTicksPerLevel = 2;
		/** Rolls are free for this long after a mob kill ({@code FREE_ROLL_ON_KILL}). Not scaled by level. */
		public int freeRollTicks = 60;
		public double webReachPerLevel = 0.25;
		public double climbSpeedPerLevel = 0.25;
		public double staminaRegenPerLevel = 0.20;
		/** {@code FLIGHT}: flying speed as a fraction of creative's (0.05). */
		public double flightSpeedFraction = 0.6;
		public double flightStaminaPerSecond = 12;
		/** Ticks between the two jump presses that start flight. */
		public int flightDoubleTapTicks = 7;
	}
}
