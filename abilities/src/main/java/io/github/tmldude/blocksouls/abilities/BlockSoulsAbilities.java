package io.github.tmldude.blocksouls.abilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.abilities.command.AbilityCommands;
import io.github.tmldude.blocksouls.abilities.sound.AbilitySounds;
import io.github.tmldude.blocksouls.common.Config;

public class BlockSoulsAbilities implements ModInitializer {
	public static final String MOD_ID = "blocksouls_abilities";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** The live config. Read it at use time (a config reload copies new values into this same instance). */
	public static AbilitiesConfig CONFIG = new AbilitiesConfig();

	@Override
	public void onInitialize() {
		CONFIG = Config.load("abilities", AbilitiesConfig.class, AbilitiesConfig::new);
		if (CONFIG.migrate()) {
			Config.save("abilities", CONFIG);
		}
		AbilitySounds.init();
		AbilityIds.registerAll();
		AbilityServer.register();
		AbilityCommands.register();
	}
}
