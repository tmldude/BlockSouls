package io.github.tmldude.blocksouls.movement.client;

import net.fabricmc.api.ClientModInitializer;

import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.client.jump.JumpHandler;

public class BlockSoulsMovementClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		JumpHandler.register(BlockSoulsMovement.CONFIG.jump);
	}
}
