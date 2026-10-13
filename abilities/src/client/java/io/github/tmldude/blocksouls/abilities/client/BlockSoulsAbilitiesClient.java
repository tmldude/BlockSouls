package io.github.tmldude.blocksouls.abilities.client;

import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.abilities.BlockSoulsAbilities;
import io.github.tmldude.blocksouls.abilities.client.hud.AbilityHud;
import io.github.tmldude.blocksouls.abilities.sound.AbilitySounds;
import io.github.tmldude.blocksouls.common.ability.Abilities;
import io.github.tmldude.blocksouls.common.ability.AbilityCastPayload;
import io.github.tmldude.blocksouls.common.ability.AbilitySlot;
import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.common.client.Keybinds;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;

/** Three keys (Z / X / C by default) send cast requests; the HUD draws the kit from the synced state. */
public class BlockSoulsAbilitiesClient implements ClientModInitializer {
	private static KeyMapping tactical;
	private static KeyMapping signature;
	private static KeyMapping ultimate;
	private static boolean ultimateWasReady;

	@Override
	public void onInitializeClient() {
		tactical = Keybinds.keyboard("ability_tactical", SDLScancode.SDL_SCANCODE_Z);
		signature = Keybinds.keyboard("ability_signature", SDLScancode.SDL_SCANCODE_X);
		ultimate = Keybinds.keyboard("ability_ultimate", SDLScancode.SDL_SCANCODE_C);
		AbilityHud.register();
		ClientTickEvents.END_CLIENT_TICK.register(BlockSoulsAbilitiesClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			ultimateWasReady = false;
			return;
		}
		press(player, tactical, AbilitySlot.TACTICAL);
		press(player, signature, AbilitySlot.SIGNATURE);
		press(player, ultimate, AbilitySlot.ULTIMATE);
		AbilityHud.tick();

		boolean ready = Abilities.state(player).ultimateReady() && Abilities.definition(player, AbilitySlot.ULTIMATE) != null;
		if (ready && !ultimateWasReady && BlockSoulsAbilities.CONFIG.hud.readyChime) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AbilitySounds.READY, 0.8F, 1.0F));
			AbilityHud.onUltimateReady();
		}
		ultimateWasReady = ready;
	}

	private static void press(LocalPlayer player, KeyMapping key, AbilitySlot slot) {
		while (key.consumeClick()) {
			if (player.isSpectator() || PlayerStatus.isDowned(player)) {
				continue;
			}
			if (!Abilities.isReady(player, slot)) {
				// The server would refuse too; answer locally so the click never feels lost.
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(AbilitySounds.DENIED, 0.5F, 1.0F));
				AbilityHud.onDenied(slot);
				continue;
			}
			ClientNetworking.sendToServer(new AbilityCastPayload(slot));
		}
	}
}
