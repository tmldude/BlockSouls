package io.github.tmldude.blocksouls.guns.client;

import java.lang.reflect.Type;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.common.client.Keybinds;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.client.hud.GunHud;
import io.github.tmldude.blocksouls.guns.net.HitMarkerPayload;
import io.github.tmldude.blocksouls.guns.net.ReloadStatePayload;
import io.github.tmldude.blocksouls.guns.net.ShotFxPayload;
import io.github.tmldude.blocksouls.guns.net.StatsSyncPayload;
import io.github.tmldude.blocksouls.guns.weapon.GunStats;
import io.github.tmldude.blocksouls.guns.weapon.Weapons;

public class BlockSoulsGunsClient implements ClientModInitializer {
	private static final Gson GSON = new Gson();
	private static final Type STATS_TYPE = new TypeToken<Map<String, GunStats>>() { }.getType();

	@Override
	public void onInitializeClient() {
		KeyMapping reload = Keybinds.keyboard("reload", SDLScancode.SDL_SCANCODE_R);
		GunClient.register(reload);
		GunHud.register();

		ClientNetworking.receive(HitMarkerPayload.TYPE, (payload, context) -> GunHud.onHitMarker(payload.kind()));
		ClientNetworking.receive(ReloadStatePayload.TYPE, (payload, context) -> GunClient.onReloadState(payload.ticks()));
		ClientNetworking.receive(ShotFxPayload.TYPE, (payload, context) -> GunFx.onShot(payload));
		ClientNetworking.receive(StatsSyncPayload.TYPE, (payload, context) -> {
			try {
				Map<String, GunStats> stats = GSON.fromJson(payload.json(), STATS_TYPE);
				Weapons.apply(stats);
			} catch (JsonParseException e) {
				BlockSoulsGuns.LOGGER.warn("Ignoring malformed weapon stats from the server", e);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			GunClient.tick(client);
			GunHud.tick();
		});
	}
}
