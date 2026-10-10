package io.github.tmldude.blocksouls.guns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class BlockSoulsGuns implements ModInitializer {
	public static final String MOD_ID = "blocksouls_guns";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("BlockSouls Guns loaded");
	}
}
