package io.github.tmldude.blocksouls.movement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.common.Config;

public class BlockSoulsMovement implements ModInitializer {
	public static final String MOD_ID = "blocksouls_movement";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static MovementConfig CONFIG = new MovementConfig();

	@Override
	public void onInitialize() {
		CONFIG = Config.load("movement", MovementConfig.class, MovementConfig::new);
	}
}
