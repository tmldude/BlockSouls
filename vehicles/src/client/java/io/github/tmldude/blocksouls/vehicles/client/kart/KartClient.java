package io.github.tmldude.blocksouls.vehicles.client.kart;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.common.client.Keybinds;

import io.github.tmldude.blocksouls.vehicles.VehicleRegistry;
import io.github.tmldude.blocksouls.vehicles.kart.KartEntity;
import io.github.tmldude.blocksouls.vehicles.kart.KartHorn;

/** Kart rendering, the driver's input and horn key, the fuel gauge, and engine sounds. */
public final class KartClient {
	private static final Map<Integer, KartEngineSound> ENGINES = new HashMap<>();
	private static KeyMapping horn;

	private KartClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(KartModel.LAYER, KartModel::createLayer);
		EntityRendererRegistry.register(VehicleRegistry.KART, KartRenderer::new);
		EntityRendererRegistry.register(VehicleRegistry.KART_WRECK, KartRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(KartClient::tick);
		horn = Keybinds.keyboard("kart_horn", SDLScancode.SDL_SCANCODE_H);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VehicleRegistry.id("fuel_gauge"), KartFuelHud::extract);
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		KartEntity driven = player != null && player.getVehicle() instanceof KartEntity kart && kart.getControllingPassenger() == player ? kart : null;
		if (driven != null) {
			driven.setInput(player.input.keyPresses);
		}
		while (horn.consumeClick()) {
			// The server rate-limits it; sending every press keeps the key responsive.
			if (driven != null) {
				ClientNetworking.sendToServer(KartHorn.Payload.INSTANCE);
			}
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
