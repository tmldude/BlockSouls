package io.github.tmldude.blocksouls.common.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;

import io.github.tmldude.blocksouls.common.VersionCheck;

public class BlockSoulsCommonClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientNetworking.receive(VersionCheck.VersionPayload.TYPE, (payload, context) -> {
			String mine = VersionCheck.version();
			if (!mine.equals(payload.version())) {
				context.client().gui.hud.getChat().addClientSystemMessage(Component.translatable(
						"message.blocksouls_common.version_mismatch", mine, payload.version()).withStyle(ChatFormatting.YELLOW));
			}
		});
	}
}
