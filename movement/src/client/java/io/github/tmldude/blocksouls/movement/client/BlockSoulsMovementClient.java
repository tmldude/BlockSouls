package io.github.tmldude.blocksouls.movement.client;

import net.fabricmc.api.ClientModInitializer;

import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.client.climb.ClimbHandler;
import io.github.tmldude.blocksouls.movement.client.hud.StaminaHud;
import io.github.tmldude.blocksouls.movement.client.jump.JumpHandler;
import io.github.tmldude.blocksouls.movement.client.roll.RollHandler;

public class BlockSoulsMovementClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		JumpHandler.register(BlockSoulsMovement.CONFIG.jump);
		RollHandler.register(BlockSoulsMovement.CONFIG);
		ClimbHandler.register(BlockSoulsMovement.CONFIG.climb);
		StaminaHud.register(BlockSoulsMovement.CONFIG.stamina);
	}
}
