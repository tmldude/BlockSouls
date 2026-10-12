package io.github.tmldude.blocksouls.vehicles.kart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.vehicles.BlockSoulsVehicles;
import io.github.tmldude.blocksouls.vehicles.VehiclesConfig;

/**
 * Shows a player who holds a wheel, engine or scrap where it goes: nearby wrecks puff a few particles (only that
 * player sees them) and the action bar names what the nearest one still needs ("Needs: 2 Wheels, Engine").
 */
public final class WreckHints {
	private static final String KEY = "message.blocksouls_vehicles.";

	private WreckHints() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WreckHints::tick);
	}

	private static void tick(MinecraftServer server) {
		VehiclesConfig.Hints cfg = BlockSoulsVehicles.CONFIG.hints;
		if (!cfg.enabled || server.getTickCount() % Math.max(1, cfg.intervalTicks) != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			hint(player);
		}
	}

	/** Server: cues the wrecks near {@code player} if they hold a repair part. Returns the wreck named on the action bar. */
	public static @Nullable KartWreckEntity hint(ServerPlayer player) {
		VehiclesConfig.Hints cfg = BlockSoulsVehicles.CONFIG.hints;
		if (player.isSpectator() || PlayerStatus.isDowned(player) || !holdsPart(player)) {
			return null;
		}
		ServerLevel level = player.level();
		List<KartWreckEntity> wrecks = level.getEntitiesOfClass(KartWreckEntity.class, player.getBoundingBox().inflate(cfg.radius),
				wreck -> wreck.isAlive() && wreck.distanceToSqr(player) <= cfg.radius * cfg.radius);
		if (wrecks.isEmpty()) {
			return null;
		}
		for (KartWreckEntity wreck : wrecks) {
			level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, wreck.getX(), wreck.getY() + 1.0, wreck.getZ(),
					cfg.particles, 0.6, 0.3, 0.6, 0);
		}
		KartWreckEntity nearest = wrecks.stream().min(Comparator.comparingDouble(wreck -> wreck.distanceToSqr(player))).orElseThrow();
		player.sendOverlayMessage(needsMessage(nearest.getProgress().remaining(BlockSoulsVehicles.CONFIG.wreck)));
		return nearest;
	}

	private static boolean holdsPart(ServerPlayer player) {
		return KartWreckEntity.partFor(player.getItemInHand(InteractionHand.MAIN_HAND)) != null
				|| KartWreckEntity.partFor(player.getItemInHand(InteractionHand.OFF_HAND)) != null;
	}

	/** "Needs: 2 Wheels, Engine, 8 Scrap" for what's still missing ({@link RepairProgress#remaining}). */
	public static Component needsMessage(RepairProgress missing) {
		List<Component> parts = new ArrayList<>();
		count(parts, missing.wheels(), "wheel");
		count(parts, missing.engines(), "engine");
		if (missing.scrap() > 0) {
			parts.add(Component.translatable(KEY + "need.scrap", missing.scrap()));
		}
		MutableComponent list = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				list.append(", ");
			}
			list.append(parts.get(i));
		}
		return Component.translatable(KEY + "wreck_needs", list);
	}

	private static void count(List<Component> parts, int count, String part) {
		if (count == 1) {
			parts.add(Component.translatable(KEY + "need." + part));
		} else if (count > 1) {
			parts.add(Component.translatable(KEY + "need." + part + "s", count));
		}
	}
}
