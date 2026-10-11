package io.github.tmldude.blocksouls.vehicles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.vehicles.kart.KartHorn;

public class BlockSoulsVehicles implements ModInitializer {
	public static final String MOD_ID = "blocksouls_vehicles";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static VehiclesConfig CONFIG = new VehiclesConfig();

	@Override
	public void onInitialize() {
		CONFIG = Config.load("vehicles", VehiclesConfig.class, VehiclesConfig::new);
		VehicleRegistry.init();
		VehicleCommands.register();
		KartHorn.register();
	}
}
