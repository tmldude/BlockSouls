package io.github.tmldude.blocksouls.movement.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.stamina.StaminaState;

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
}
