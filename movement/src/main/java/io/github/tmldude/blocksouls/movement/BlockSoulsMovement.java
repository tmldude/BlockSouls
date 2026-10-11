package io.github.tmldude.blocksouls.movement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.movement.roll.RollServer;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;

public class BlockSoulsMovement implements ModInitializer {
	public static final String MOD_ID = "blocksouls_movement";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static MovementConfig CONFIG = new MovementConfig();

	@Override
	public void onInitialize() {
		CONFIG = Config.load("movement", MovementConfig.class, MovementConfig::new);
		StaminaAttachment.register(CONFIG.stamina);
		RollServer.register(CONFIG);
	}
}
