package io.github.tmldude.blocksouls.world;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class BlockSoulsWorld implements ModInitializer {
	public static final String MOD_ID = "blocksouls_world";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("BlockSouls World loaded");
	}
}
