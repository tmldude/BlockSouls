package io.github.tmldude.blocksouls.vehicles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class BlockSoulsVehicles implements ModInitializer {
	public static final String MOD_ID = "blocksouls_vehicles";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("BlockSouls Vehicles loaded");
	}
}
