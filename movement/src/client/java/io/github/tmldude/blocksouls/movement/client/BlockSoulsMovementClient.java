package io.github.tmldude.blocksouls.movement.client;

import net.fabricmc.api.ClientModInitializer;

import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.client.climb.ClimbHandler;
import io.github.tmldude.blocksouls.movement.client.glide.GlideHandler;
import io.github.tmldude.blocksouls.movement.client.hud.StaminaHud;
import io.github.tmldude.blocksouls.movement.client.jump.JumpHandler;
import io.github.tmldude.blocksouls.movement.client.roll.RollHandler;
import io.github.tmldude.blocksouls.movement.client.sync.ClientConfigSync;
import io.github.tmldude.blocksouls.movement.client.web.RopeRenderer;
import io.github.tmldude.blocksouls.movement.client.web.WebHandler;

public class BlockSoulsMovementClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfigSync.register();
		JumpHandler.register(BlockSoulsMovement.CONFIG);
		RollHandler.register(BlockSoulsMovement.CONFIG);
		ClimbHandler.register(BlockSoulsMovement.CONFIG.climb);
		// After jump and climb, so the swing has the final say over velocity each tick.
		WebHandler.register(BlockSoulsMovement.CONFIG);
		RopeRenderer.register();
		GlideHandler.register(BlockSoulsMovement.CONFIG.glide);
		StaminaHud.register(BlockSoulsMovement.CONFIG.stamina);
	}
}
