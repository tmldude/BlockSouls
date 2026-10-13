package io.github.tmldude.blocksouls.game.player.downed;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.PlayersConfig;
import io.github.tmldude.blocksouls.game.run.RunSystem;

/**
 * Last Stand (solo only, once per siege): a solo player who goes down crawls for {@code lastStandSeconds} with the
 * pistol instead of ending the run; {@code lastStandKills} kills while down is a self-revive, the timer running
 * out is the run over it would have been. The team-wipe check defers to an active Last Stand.
 */
public final class LastStand {
	private static PlayersConfig.Downed config;
	private static @Nullable UUID active;
	private static LastStandRule.@Nullable Timer timer;
	private static boolean usedThisSiege;

	private LastStand() {
	}

	public static void register(PlayersConfig.Downed downedConfig) {
		config = downedConfig;
		Networking.registerClientbound(LastStandPayloads.State.TYPE, LastStandPayloads.State.CODEC);

		ServerTickEvents.END_SERVER_TICK.register(LastStand::tick);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, entity, killed, source) -> {
			if (entity instanceof ServerPlayer player && counts(killed)) {
				onKill(player);
			}
		});
		PlayerRunEvents.REVIVED.register((player, reviver) -> {
			if (isActive(player)) {
				end(player, false);
			}
		});
		RunEvents.DUSK.register((server, night) -> usedThisSiege = false);
		RunEvents.RUN_STARTED.register((server, state) -> reset(server));
		RunEvents.RUN_OVER.register((server, state) -> reset(server));
	}

	public static boolean isActive() {
		return active != null;
	}

	public static boolean isActive(ServerPlayer player) {
		return active != null && active.equals(player.getUUID());
	}

	/** Whether this siege's Last Stand is spent. */
	public static boolean usedThisSiege() {
		return usedThisSiege;
	}

	/**
	 * Starts a Last Stand for a solo player who just went down, if the rule allows. {@code countingPlayers} is how
	 * many players count toward a wipe right now. Returns whether it started (the caller then skips the run over).
	 */
	public static boolean tryStart(ServerPlayer player, int countingPlayers) {
		if (active != null || !PlayerStatus.isDowned(player)
				|| !LastStandRule.canStart(config.lastStandEnabled, LastStandRule.isSolo(countingPlayers), usedThisSiege, config.lastStandOncePerSiege)) {
			return false;
		}
		active = player.getUUID();
		timer = new LastStandRule.Timer(Math.max(1, config.lastStandSeconds) * 20, config.lastStandKills);
		usedThisSiege = true;
		BlockSoulsGame.LOGGER.info("Last Stand: {} has {} s for {} kills", player.getPlainTextName(), config.lastStandSeconds, config.lastStandKills);

		player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
		player.connection.send(new ClientboundSetTitleTextPacket(
				Component.translatable("title.blocksouls_game.last_stand").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.translatable("title.blocksouls_game.last_stand.sub", config.lastStandKills, config.lastStandSeconds).withStyle(ChatFormatting.RED)));
		player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4F, 1.6F);
		send(player);
		return true;
	}

	/** Ticks of crawl left for the active Last Stand, or -1. */
	public static int ticksLeft() {
		return timer == null ? -1 : timer.ticksLeft();
	}

	public static int kills() {
		return timer == null ? 0 : timer.kills();
	}

	private static void onKill(ServerPlayer player) {
		if (!isActive(player) || timer == null) {
			return;
		}
		boolean revives = timer.onKill();
		send(player);
		if (revives) {
			player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
			end(player, false);
			Downed.revive(player, null);
		}
	}

	private static void tick(MinecraftServer server) {
		if (active == null || timer == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(active);
		if (player == null || !PlayerStatus.isDowned(player)) {
			// Left, or stood up some other way (an admin revive): no stand to hold.
			active = null;
			timer = null;
			return;
		}
		boolean expired = timer.tick();
		if (player.tickCount % 2 == 0 || expired) {
			send(player);
		}
		if (expired) {
			end(player, true);
			BlockSoulsGame.LOGGER.info("Last Stand over: ending the run");
			RunSystem.endRun(server);
		}
	}

	private static void end(ServerPlayer player, boolean expired) {
		active = null;
		timer = null;
		Networking.sendToPlayer(player, new LastStandPayloads.State(-1, 0, 0, config.lastStandKills));
	}

	private static void reset(MinecraftServer server) {
		if (active != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(active);
			if (player != null) {
				end(player, false);
			}
		}
		active = null;
		timer = null;
		usedThisSiege = false;
	}

	private static void send(ServerPlayer player) {
		if (timer != null) {
			Networking.sendToPlayer(player, new LastStandPayloads.State(timer.ticksLeft(), Math.max(1, config.lastStandSeconds) * 20,
					timer.kills(), timer.killsNeeded()));
		}
	}

	/** Kills that count toward the self-revive: hostile mobs (wave mobs, bosses, vanilla monsters), never players. */
	static boolean counts(LivingEntity killed) {
		return killed instanceof Enemy;
	}

	/** Tests: forget any active stand and the siege flag. */
	public static void resetForTests(MinecraftServer server) {
		reset(server);
	}
}
