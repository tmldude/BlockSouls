package io.github.tmldude.blocksouls.game.run;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** Sounds and titles for run moments. Show, don't tell: no lore text. */
final class RunAnnouncer {
	private RunAnnouncer() {
	}

	/** Shortly before dusk: a distant, pitched-down rumble for everyone in the overworld (the client tints the sky). */
	static void duskCue(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level() == server.overworld()) {
				RunSystem.playTo(player, RunSystem.duskSound(), 1.0F, 0.5F);
			}
		}
	}

	static void endless(MinecraftServer server) {
		Component title = Component.translatableWithFallback("title.blocksouls_game.endless", "Endless").withStyle(ChatFormatting.DARK_PURPLE);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
			player.connection.send(new ClientboundSetTitleTextPacket(title));
			RunSystem.playTo(player, SoundEvents.RAID_HORN, 1.0F, 0.6F);
		}
	}
}
