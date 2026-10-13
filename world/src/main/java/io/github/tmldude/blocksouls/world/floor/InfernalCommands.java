package io.github.tmldude.blocksouls.world.floor;

import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;

/**
 * Infernal debug commands (v2-floor2-infernal), merged into {@code /bs floor} next to {@link FloorCommands}:
 * <ul>
 * <li>{@code /bs floor open <act> <players> <pool>}: open a floor from any template pool (floor number = act).</li>
 * <li>{@code /bs floor hazard lava_warn|lava_rise|lava_schedule} ({@link FloorHazards}): on the Infernal floor you stand
 * on, play the warning, raise the lava now, or start the siege clock as if the portal charge started.</li>
 * </ul>
 */
public final class InfernalCommands {
	private InfernalCommands() {
	}

	public static void init() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("floor")
				.then(Commands.literal("open").then(Commands.argument("act", IntegerArgumentType.integer(1, 64))
						.then(Commands.argument("players", IntegerArgumentType.integer(1, 8))
								.then(Commands.argument("pool", IdentifierArgument.id()).executes(InfernalCommands::open)))))));
		FloorHazards.register("lava_warn", (player, floor) -> infernal(floor, () -> InfernalHazard.warn(player.level().getServer(), floor),
				"Lava rumble"));
		FloorHazards.register("lava_rise", (player, floor) -> infernal(floor, null, "Lava rose (" + rise(player, floor) + " blocks)"));
		FloorHazards.register("lava_schedule", (player, floor) -> infernal(floor, () -> InfernalHazard.schedule(player.level().getServer(), floor),
				"Lava rises in " + WorldFloors.config().infernal.riseSeconds + " s"));
	}

	private static int open(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		int act = IntegerArgumentType.getInteger(c, "act");
		int players = IntegerArgumentType.getInteger(c, "players");
		Identifier pool = IdentifierArgument.getId(c, "pool");
		Optional<PlacedFloor> floor = Floors.open(server, new FloorRequest(act, pool, act, 0, players));
		if (floor.isEmpty()) {
			c.getSource().sendFailure(Component.literal("No floor placed (is a template in pool " + pool + " for floor " + act + "?)"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Floor for act " + act + ": " + floor.get().template() + " at "
				+ floor.get().camp().toShortString()), true);
		return 1;
	}

	private static int rise(net.minecraft.server.level.ServerPlayer player, PlacedFloor floor) {
		return floor.tags().contains(InfernalHazard.TAG) ? InfernalHazard.rise(player.level().getServer(), floor) : 0;
	}

	private static String infernal(PlacedFloor floor, Runnable action, String done) {
		if (!floor.tags().contains(InfernalHazard.TAG)) {
			return "Not an Infernal floor";
		}
		if (action != null) {
			action.run();
		}
		return done;
	}
}
