package io.github.tmldude.blocksouls.game.client;

import net.fabricmc.api.ClientModInitializer;

import io.github.tmldude.blocksouls.game.client.hud.GameHud;

public class BlockSoulsGameClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GameHud.init();
	}
}
