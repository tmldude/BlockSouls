package io.github.tmldude.blocksouls.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class BlockSoulsGame implements ModInitializer {
	public static final String MOD_ID = "blocksouls_game";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("BlockSouls Game loaded");
	}
}
