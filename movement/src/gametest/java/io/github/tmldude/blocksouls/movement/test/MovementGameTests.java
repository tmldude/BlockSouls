package io.github.tmldude.blocksouls.movement.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.player.Capabilities;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.climb.ClimbWorld;
import io.github.tmldude.blocksouls.movement.glide.GlideServer;
import io.github.tmldude.blocksouls.movement.roll.RollServer;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.stamina.StaminaState;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;
import io.github.tmldude.blocksouls.movement.web.WebServer;

public class MovementGameTests {
	@GameTest(maxTicks = 100)
	public void staminaRegeneratesOnTheServerTick(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		StaminaAttachment.set(player, new StaminaState(10, 0));

		helper.runAfterDelay(40, () -> {
			float stamina = StaminaAttachment.get(player).value();
			float expected = 10 + (float) (BlockSoulsMovement.CONFIG.stamina.regenPerSecond * 2);
			helper.assertTrue(Math.abs(stamina - expected) < 2, "expected ~" + expected + " stamina after 2s, got " + stamina);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void spendingStaminaDelaysRegen(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		StaminaAttachment.set(player, StaminaState.full(BlockSoulsMovement.CONFIG.stamina).spend(50, BlockSoulsMovement.CONFIG.stamina));

		helper.runAfterDelay(BlockSoulsMovement.CONFIG.stamina.regenDelayTicks - 2, () -> {
			helper.assertTrue(StaminaAttachment.get(player).value() == 50, "no regen during the delay");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void swingingCancelsFallDamageUntilShortlyAfterRelease(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		DamageSource fall = helper.getLevel().damageSources().fall();
		helper.assertTrue(WebServer.allowDamage(player, fall, 5), "fall damage applies normally");

		WebServer.attach(player, WebServer.ropePoint(player).add(0, 10, 0), 10);
		helper.assertTrue(!WebServer.allowDamage(player, fall, 5), "no fall damage while swinging");
		helper.assertTrue(WebServer.allowDamage(player, helper.getLevel().damageSources().generic(), 5), "other damage still applies");

		WebServer.detach(player, false);
		helper.assertTrue(!WebServer.allowDamage(player, fall, 5), "no fall damage right after release");
		helper.runAfterDelay(BlockSoulsMovement.CONFIG.web.noFallTicksAfterRelease + 2, () -> {
			helper.assertTrue(WebServer.allowDamage(player, fall, 5), "fall damage back after the grace window");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void swingIsDroppedWhenTooFarFromTheAnchor(GameTestHelper helper) {
		ServerPlayer near = helper.makeMockServerPlayerInLevel();
		ServerPlayer far = helper.makeMockServerPlayerInLevel();
		WebServer.attach(near, WebServer.ropePoint(near).add(0, 8, 0), 8);
		WebServer.attach(far, WebServer.ropePoint(far).add(0, 30, 0), 8);

		helper.runAfterDelay(5, () -> {
			helper.assertTrue(WebServer.isSwinging(near), "a player on their rope keeps swinging");
			helper.assertTrue(!WebServer.isSwinging(far), "a player far past rope length + slack is dropped");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void goingDownDropsTheSwing(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		WebServer.attach(player, WebServer.ropePoint(player).add(0, 8, 0), 8);
		PlayerStatus.setDowned(player, true);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!WebServer.isSwinging(player), "downed players can't swing");
			helper.succeed();
		});
	}

	@GameTest
	public void downedPlayersCantRoll(GameTestHelper helper) {
		ServerPlayer up = helper.makeMockServerPlayerInLevel();
		ServerPlayer downed = helper.makeMockServerPlayerInLevel();
		up.setOnGround(true);
		downed.setOnGround(true);
		PlayerStatus.setDowned(downed, true);

		helper.assertTrue(RollServer.tryStart(up), "a standing player can roll");
		helper.assertTrue(!RollServer.tryStart(downed), "a downed player can't roll");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void rollIframeTraitExtendsTheWindow(GameTestHelper helper) {
		ServerPlayer plain = helper.makeMockServerPlayerInLevel();
		ServerPlayer boosted = helper.makeMockServerPlayerInLevel();
		PlayerTraits.set(boosted, PlayerTraits.ROLL_IFRAMES, 2);
		RollServer.forceStart(plain);
		RollServer.forceStart(boosted);

		// Base i-frames end on tick 8; two levels push that to 12.
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(!RollServer.isInvulnerable(plain), "base i-frames are over");
			helper.assertTrue(RollServer.isInvulnerable(boosted), "extended i-frames still on");
			helper.succeed();
		});
	}

	@GameTest
	public void climbSpeedTraitScalesClimbing(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(MovementTraits.climbSpeedMultiplier(player) == 1.0, "no trait, normal speed");
		PlayerTraits.set(player, PlayerTraits.CLIMB_SPEED, 2);
		double multiplier = MovementTraits.climbSpeedMultiplier(player);
		helper.assertTrue(Math.abs(multiplier - 1.5) < 1e-9, "two levels climb 50% faster, got " + multiplier);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void staminaRegenTraitSpeedsUpRegen(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PlayerTraits.set(player, PlayerTraits.STAMINA_REGEN, 5);
		StaminaAttachment.set(player, new StaminaState(10, 0));

		helper.runAfterDelay(40, () -> {
			float stamina = StaminaAttachment.get(player).value();
			// +20% per level: 5 levels double the rate.
			float expected = 10 + (float) (BlockSoulsMovement.CONFIG.stamina.regenPerSecond * 2 * 2);
			helper.assertTrue(Math.abs(stamina - expected) < 3, "expected ~" + expected + " stamina after 2s, got " + stamina);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the origin capability gate

	@GameTest
	public void defaultPlayersKeepEveryV1Verb(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PlayerTraits.set(player, PlayerTraits.DOUBLE_JUMP, 1);
		helper.assertTrue(Capabilities.allGranted(player), "no set attached");
		helper.assertTrue(MovementTraits.canClimb(player) && MovementTraits.canSwing(player) && MovementTraits.canMultiJump(player), "v1 verbs");
		helper.assertTrue(ClimbWorld.isEligible(player), "climb eligible as in v1");
		helper.assertTrue(WebServer.canSwing(player), "may swing as in v1");
		helper.assertTrue(MovementTraits.airJumps(player) == 1, "the double-jump trait counts as in v1");
		helper.assertTrue(!MovementTraits.canGlide(player) && !GlideServer.canGlide(player), "no glide without an origin (v1 had none)");
		helper.assertTrue(RollServer.tryStart(stand(player)), "the roll is universal");
		helper.succeed();
	}

	@GameTest
	public void restrictedPlayersLoseTheVerbsTheyDontHold(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PlayerTraits.set(player, PlayerTraits.DOUBLE_JUMP, 1);
		Capabilities.restrictTo(player, Set.of(Capabilities.GLIDE));
		helper.assertTrue(!ClimbWorld.isEligible(player), "no climb");
		helper.assertTrue(!WebServer.canSwing(player), "no swing");
		helper.assertTrue(MovementTraits.airJumps(player) == 0, "no air jumps without MULTI_JUMP, trait or not");
		helper.assertTrue(GlideServer.canGlide(player), "glides");
		helper.assertTrue(RollServer.tryStart(stand(player)), "the roll is never gated");

		Capabilities.grant(player, Capabilities.CLIMB);
		helper.assertTrue(ClimbWorld.isEligible(player), "cross-training grants climb");
		Capabilities.grantAll(player);
		helper.assertTrue(WebServer.canSwing(player) && !GlideServer.canGlide(player), "back to v1");
		helper.succeed();
	}

	@GameTest
	public void staminaPenaltyTraitLowersTheMax(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		double full = BlockSoulsMovement.CONFIG.stamina.max;
		helper.assertTrue(MovementTraits.staminaMax(player) == full, "full max by default");
		PlayerTraits.set(player, PlayerTraits.STAMINA_MAX_PENALTY, 1);
		helper.assertTrue(MovementTraits.staminaMax(player) == full - BlockSoulsMovement.CONFIG.traits.staminaMaxPenaltyPerLevel, "one level off");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void wingsStartAGlideAndDrainStamina(GameTestHelper helper) {
		ServerPlayer gliderless = helper.makeMockServerPlayerInLevel();
		ServerPlayer zephyr = helper.makeMockServerPlayerInLevel();
		Capabilities.restrictTo(zephyr, Set.of(Capabilities.GLIDE));
		gliderless.setOnGround(false);
		zephyr.setOnGround(false);

		helper.assertTrue(!gliderless.tryToStartFallFlying(), "nothing to glide on without the verb or an elytra");
		helper.assertTrue(zephyr.tryToStartFallFlying(), "the wings are a custom elytra");
		helper.assertTrue(zephyr.isFallFlying(), "gliding");

		float before = StaminaAttachment.get(zephyr).value();
		GlideServer.tickGlide(zephyr);
		helper.assertTrue(StaminaAttachment.get(zephyr).value() < before, "gliding costs stamina");

		StaminaAttachment.set(zephyr, new StaminaState(0, 0));
		helper.assertTrue(!GlideServer.canGlide(zephyr), "the wings fold at zero stamina");
		helper.succeed();
	}

	private static ServerPlayer stand(ServerPlayer player) {
		player.setOnGround(true);
		return player;
	}
}
