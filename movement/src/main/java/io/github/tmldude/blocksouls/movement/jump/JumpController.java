package io.github.tmldude.blocksouls.movement.jump;

import io.github.tmldude.blocksouls.movement.MovementConfig;

/**
 * Mario-style jump logic for one player, free of Minecraft types so it can be unit-tested.
 *
 * <p>Call {@link #tick} once per tick after the player has moved, and {@link #onJump} whenever a jump
 * launches (vanilla's or one requested by {@code tick}).
 */
public final class JumpController {
	private final MovementConfig.Jump config;

	private boolean wasOnGround;
	private boolean wasJumpDown;
	private int ticksInAir;
	private int ticksOnGround;
	private boolean jumpedSinceGrounded;
	private int bufferTicksLeft;
	private boolean holding;
	private int holdTicks;
	/** 0, 1 or 2: position in the triple-jump chain of the last jump. */
	private int chainStep;
	private boolean airborneFromJump;
	/** Whether the player landed from a jump and hasn't jumped since, which a chain jump requires. */
	private boolean landedFromJump;

	public JumpController(MovementConfig.Jump config) {
		this.config = config;
	}

	/**
	 * @param onGround  whether the player is standing on the ground
	 * @param jumpDown  whether the jump key is held
	 * @param velocityY current vertical velocity
	 * @param gravity   the player's gravity this tick (vanilla 0.08)
	 * @param eligible  false while flying, gliding, swimming, climbing, riding, etc. Skips all changes.
	 */
	public record Input(boolean onGround, boolean jumpDown, double velocityY, double gravity, boolean eligible) {
	}

	/** @param jump whether the caller should launch a jump now (coyote time or a buffered press) */
	public record Result(double velocityY, boolean jump) {
	}

	public Result tick(Input in) {
		boolean pressed = in.jumpDown() && !wasJumpDown;
		wasJumpDown = in.jumpDown();

		if (!in.eligible()) {
			holding = false;
			bufferTicksLeft = 0;
			wasOnGround = in.onGround();
			return new Result(in.velocityY(), false);
		}

		double vy = in.velocityY();
		boolean jump = false;

		if (in.onGround()) {
			ticksOnGround = wasOnGround ? ticksOnGround + 1 : 0;
			ticksInAir = 0;
			jumpedSinceGrounded = false;
			holding = false;
			if (airborneFromJump) {
				airborneFromJump = false;
				landedFromJump = true;
			}

			// Vanilla only jumps on landing if the key is held and its jump cooldown has expired, and if it
			// had jumped this tick we wouldn't be on the ground now. So a buffered press always fires here.
			if (bufferTicksLeft > 0) {
				jump = true;
			}
			bufferTicksLeft = 0;
		} else {
			ticksInAir++;

			if (pressed) {
				if (!jumpedSinceGrounded && ticksInAir <= config.coyoteTicks) {
					jump = true;
				} else {
					bufferTicksLeft = config.jumpBufferTicks + 1;
				}
			}

			if (holding && in.jumpDown() && vy > 0 && holdTicks < config.maxHoldTicks) {
				vy += config.holdBoost;
				holdTicks++;
			} else {
				holding = false;
			}

			if (vy < 0 && vy > -config.maxFallSpeed) {
				vy = Math.max(vy - in.gravity() * (config.fallGravityMult - 1.0), -config.maxFallSpeed);
			}
		}

		if (bufferTicksLeft > 0) {
			bufferTicksLeft--;
		}

		wasOnGround = in.onGround();
		return new Result(vy, jump);
	}

	/** Records a jump launch and returns the multiplier for its upward velocity. */
	public double onJump() {
		boolean chained = config.tripleJump && landedFromJump && chainStep < 2 && ticksOnGround <= config.tripleJumpWindowTicks;
		chainStep = chained ? chainStep + 1 : 0;

		jumpedSinceGrounded = true;
		airborneFromJump = true;
		landedFromJump = false;
		holding = true;
		holdTicks = 0;
		bufferTicksLeft = 0;

		double bonus = switch (chainStep) {
			case 1 -> config.secondJumpBonus;
			case 2 -> config.thirdJumpBonus;
			default -> 0.0;
		};
		return config.tapJumpMult * (1.0 + bonus);
	}

	/** Position in the triple-jump chain of the last jump: 0, 1 or 2. */
	public int chainStep() {
		return chainStep;
	}
}
