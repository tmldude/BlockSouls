package io.github.tmldude.blocksouls.bosses;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class BlockSoulsBosses implements ModInitializer {
	public static final String MOD_ID = "blocksouls_bosses";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("BlockSouls Bosses loaded");
	}
}
