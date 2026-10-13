package io.github.tmldude.blocksouls.world.floor;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;

/**
 * Floor 3, Skyrift, at runtime: the rim wind that nudges players off the open edge, and its debug hazard triggers
 * ({@code /bs floor hazard gust | fall}). The shards, the open edge and the look are built by {@link FloorBuilder}.
 */
public final class SkyriftFloor {
	private SkyriftFloor() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 2 == 0) {
				rimWind(server);
			}
		});
		FloorHazards.register("gust", (player, floor) -> {
			double wind = windOf(floor);
			if (wind <= 0) {
				return "No rim wind on this floor";
			}
			push(player, floor, WorldFloors.config().rimWindStrength * wind * WorldFloors.config().gustFactor);
			return "Gust";
		});
		FloorHazards.register("fall", (player, floor) -> {
			WorldFloors.fellOff(player, floor);
			return "Fell off: back to camp, -" + WorldFloors.config().fallPenalty + " health";
		});
	}

	/** The template's rim wind multiplier for a placed floor (0 when the template is gone or has none). */
	static double windOf(PlacedFloor floor) {
		FloorTemplate template = FloorTemplateCatalog.get(floor.template().toString());
		return template == null ? 0 : template.rimWind;
	}

	private static void rimWind(MinecraftServer server) {
		ServerLevel level = server.getLevel(WorldFloors.levelKey());
		if (level == null || level.players().isEmpty()) {
			return;
		}
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			PlacedFloor floor = Floors.at(server, level.dimension().identifier(), player.blockPosition()).orElse(null);
			if (floor == null) {
				continue;
			}
			double wind = windOf(floor);
			if (wind <= 0) {
				continue;
			}
			double dx = player.getX() - (floor.camp().getX() + 0.5);
			double dz = player.getZ() - (floor.camp().getZ() + 0.5);
			double strength = RimWind.strength(WorldFloors.config(), wind, Math.sqrt(dx * dx + dz * dz), floor.radius(),
					player.getY() - floor.groundY(), player.isShiftKeyDown());
			if (strength > 0) {
				push(player, floor, strength);
				if (player.getRandom().nextInt(4) == 0) {
					level.sendParticles(player, ParticleTypes.CLOUD, false, false, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.6, 0.4,
							0.6, 0.02);
				}
			}
		}
	}

	/** Pushes a player straight outward from the floor's centre. */
	private static void push(ServerPlayer player, PlacedFloor floor, double strength) {
		double dx = player.getX() - (floor.camp().getX() + 0.5);
		double dz = player.getZ() - (floor.camp().getZ() + 0.5);
		double d = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
		player.push(dx / d * strength, 0, dz / d * strength);
	}
}
