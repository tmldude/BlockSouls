package io.github.tmldude.blocksouls.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.game.player.PlayerSystem;
import io.github.tmldude.blocksouls.game.run.RunSystem;
import io.github.tmldude.blocksouls.game.run.director.Director;

public class BlockSoulsGame implements ModInitializer {
	public static final String MOD_ID = "blocksouls_game";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		RunSystem.init();
		PlayerSystem.init();
		Director.init();
	}
}
