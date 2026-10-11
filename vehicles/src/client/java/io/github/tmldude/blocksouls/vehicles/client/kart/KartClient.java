package io.github.tmldude.blocksouls.vehicles.client.kart;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import io.github.tmldude.blocksouls.vehicles.VehicleRegistry;
import io.github.tmldude.blocksouls.vehicles.kart.KartEntity;

/** Kart rendering, the driver's input, and engine sounds. */
public final class KartClient {
	private static final Map<Integer, KartEngineSound> ENGINES = new HashMap<>();

	private KartClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(KartModel.LAYER, KartModel::createLayer);
		EntityRendererRegistry.register(VehicleRegistry.KART, KartRenderer::new);
		EntityRendererRegistry.register(VehicleRegistry.KART_WRECK, KartRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(KartClient::tick);
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player != null && player.getVehicle() instanceof KartEntity kart && kart.getControllingPassenger() == player) {
			kart.setInput(player.input.keyPresses);
		}

		ENGINES.values().removeIf(KartEngineSound::isStopped);
		if (client.level == null || client.isPaused()) {
			return;
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof KartEntity kart && kart.isVehicle() && !ENGINES.containsKey(kart.getId())) {
				KartEngineSound sound = new KartEngineSound(kart);
				ENGINES.put(kart.getId(), sound);
				client.getSoundManager().play(sound);
			}
		}
	}
}
