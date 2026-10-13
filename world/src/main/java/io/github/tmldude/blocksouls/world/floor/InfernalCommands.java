package io.github.tmldude.blocksouls.world.floor;

import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import io.github.tmldude.blocksouls.common.act.Acts;
import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;

/**
 * Infernal debug commands (v2-floor2-infernal), merged into {@code /bs floor} next to {@link FloorCommands}:
 * <ul>
 * <li>{@code /bs floor open <act> <players> <pool>}: open a floor from any template pool (floor number = act).</li>
 * <li>{@code /bs floor hazard <warn|rise|schedule>}: the floor you stand on (else the current act's): play the warning,
 * raise the lava now, or start the siege clock as if the portal charge started.</li>
 * </ul>
 */
public final class InfernalCommands {
	private InfernalCommands() {
	}

	public static void init() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("floor")
				.then(Commands.literal("open").then(Commands.argument("act", IntegerArgumentType.integer(1, 64))
						.then(Commands.argument("players", IntegerArgumentType.integer(1, 8))
								.then(Commands.argument("pool", IdentifierArgument.id()).executes(InfernalCommands::open)))))
				.then(Commands.literal("hazard").then(Commands.argument("trigger", StringArgumentType.word())
						.suggests((c, builder) -> {
							builder.suggest("warn");
							builder.suggest("rise");
							builder.suggest("schedule");
							return builder.buildFuture();
						})
						.executes(InfernalCommands::hazard)))));
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

	private static int hazard(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		String trigger = StringArgumentType.getString(c, "trigger");
		Optional<PlacedFloor> floor = Floors.at(server, c.getSource().getLevel().dimension().identifier(),
				net.minecraft.core.BlockPos.containing(c.getSource().getPosition()));
		if (floor.isEmpty()) {
			floor = Floors.forAct(server, Acts.current(server).ordinal());
		}
		if (floor.isEmpty() || !floor.get().tags().contains(InfernalHazard.TAG)) {
			c.getSource().sendFailure(Component.literal("Not on (and no current) floor with a hazard"));
			return 0;
		}
		PlacedFloor placed = floor.get();
		switch (trigger) {
			case "warn" -> InfernalHazard.warn(server, placed);
			case "rise" -> {
				int changed = InfernalHazard.rise(server, placed);
				c.getSource().sendSuccess(() -> Component.literal("Lava rose (" + changed + " blocks)"), true);
				return 1;
			}
			case "schedule" -> InfernalHazard.schedule(server, placed);
			default -> {
				c.getSource().sendFailure(Component.literal("Unknown hazard trigger (warn, rise, schedule)"));
				return 0;
			}
		}
		c.getSource().sendSuccess(() -> Component.literal("Hazard " + trigger + " on act " + placed.actOrdinal()), true);
		return 1;
	}
}
