package io.github.tmldude.blocksouls.zombies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class BlockSoulsZombies implements ModInitializer {
	public static final String MOD_ID = "blocksouls_zombies";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("BlockSouls Zombies loaded");
	}
}
