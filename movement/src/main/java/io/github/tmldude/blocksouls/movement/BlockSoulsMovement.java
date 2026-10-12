package io.github.tmldude.blocksouls.movement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.movement.climb.ClimbServer;
import io.github.tmldude.blocksouls.movement.flight.FlightServer;
import io.github.tmldude.blocksouls.movement.glide.GlideServer;
import io.github.tmldude.blocksouls.movement.jump.AirJumpServer;
import io.github.tmldude.blocksouls.movement.roll.RollServer;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.sync.ConfigSync;
import io.github.tmldude.blocksouls.movement.web.WebServer;

public class BlockSoulsMovement implements ModInitializer {
	public static final String MOD_ID = "blocksouls_movement";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** The live config: this side's file, or the server's values while connected to one (see {@link ConfigSync}). */
	public static final MovementConfig CONFIG = new MovementConfig();

	@Override
	public void onInitialize() {
		CONFIG.copyFrom(Config.load(ConfigSync.FILE, MovementConfig.class, MovementConfig::new));
		StaminaAttachment.register(CONFIG.stamina);
		StaminaAttachment.installProvider();
		RollServer.register(CONFIG);
		ClimbServer.register(CONFIG);
		WebServer.register(CONFIG);
		FlightServer.register(CONFIG);
		GlideServer.register(CONFIG);
		AirJumpServer.register();
		ConfigSync.register();
		MovementCommands.register();
	}
}
