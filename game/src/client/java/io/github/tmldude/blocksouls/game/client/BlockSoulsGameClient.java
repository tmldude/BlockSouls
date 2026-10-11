package io.github.tmldude.blocksouls.game.client;

import net.fabricmc.api.ClientModInitializer;

import io.github.tmldude.blocksouls.game.client.hud.GameHud;
import io.github.tmldude.blocksouls.game.client.player.PlayerClient;
import io.github.tmldude.blocksouls.game.client.run.ClientRunSync;

public class BlockSoulsGameClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientRunSync.init();
		GameHud.init();
		PlayerClient.init();
	}
}
