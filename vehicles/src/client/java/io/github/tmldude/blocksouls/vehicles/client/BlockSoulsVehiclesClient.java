package io.github.tmldude.blocksouls.vehicles.client;

import net.fabricmc.api.ClientModInitializer;

import io.github.tmldude.blocksouls.vehicles.client.kart.KartClient;

public class BlockSoulsVehiclesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		KartClient.register();
	}
}
