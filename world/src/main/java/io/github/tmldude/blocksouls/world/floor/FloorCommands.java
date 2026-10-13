package io.github.tmldude.blocksouls.world.floor;

import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * {@code /bs floor open <act> [players] [pool]} (places a floor without a run; pool e.g. {@code skyrift}, floor number = act), {@code /bs floor tp <act>}, {@code /bs floor info},
 * {@code /bs floor light <act>} (lights its exit portal), {@code /bs floor goto <slot kind>}, {@code /bs floor pop}
 * (pops the nearest node) and {@code /bs floor hazard <trigger>} (fires a floor hazard at you, see {@link FloorHazards}).
 */
public final class FloorCommands {
	private FloorCommands() {
	}

	public static void init() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("floor")
				.then(Commands.literal("open").then(Commands.argument("act", IntegerArgumentType.integer(1, 64))
						.executes(c -> open(c, 1))
						.then(Commands.argument("players", IntegerArgumentType.integer(1, 8))
								.executes(c -> open(c, IntegerArgumentType.getInteger(c, "players")))
								// v2-floor5-blight: the pool to draw from (a blocksouls_world pool name, e.g. blight).
								.then(Commands.argument("pool", com.mojang.brigadier.arguments.StringArgumentType.word())
										.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("surface", "blight"), builder))
										.executes(c -> open(c, IntegerArgumentType.getInteger(c, "players"),
												com.mojang.brigadier.arguments.StringArgumentType.getString(c, "pool")))))))
				.then(Commands.literal("tp").then(Commands.argument("act", IntegerArgumentType.integer(1, 64)).executes(FloorCommands::tp)))
				.then(Commands.literal("light").then(Commands.argument("act", IntegerArgumentType.integer(1, 64)).executes(FloorCommands::light)))
				.then(Commands.literal("info").executes(FloorCommands::info))
				.then(Commands.literal("goto").then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Arrays.stream(FloorSlotKind.values())
								.map(kind -> kind.name().toLowerCase(java.util.Locale.ROOT)), builder))
						.executes(FloorCommands::gotoSlot)))
				.then(Commands.literal("pop").executes(FloorCommands::pop))
				.then(Commands.literal("hazard").then(Commands.argument("trigger", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(FloorHazards.names(), b))
						.executes(FloorCommands::hazard)))));
		InfernalCommands.init(); // v2-floor2-infernal: /bs floor open <act> <players> <pool>; lava hazards via FloorHazards
	}

	private static int open(CommandContext<CommandSourceStack> c, int players) {
		return open(c, players, "surface");
	}

	private static int open(CommandContext<CommandSourceStack> c, int players, String pool) {
		MinecraftServer server = c.getSource().getServer();
		int act = IntegerArgumentType.getInteger(c, "act");
		Optional<PlacedFloor> floor = Floors.open(server, new FloorRequest(act, BlockSoulsWorld.id(pool.toLowerCase(java.util.Locale.ROOT)), act, 0, players));
		if (floor.isEmpty()) {
			c.getSource().sendFailure(Component.literal("No floor placed (is the floor dimension loaded and a template in the pool?)"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Floor for act " + act + ": " + describe(floor.get())), true);
		return 1;
	}

	private static int tp(CommandContext<CommandSourceStack> c) {
		Optional<PlacedFloor> floor = Floors.forAct(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "act"));
		if (floor.isEmpty() || !(c.getSource().getEntity() instanceof ServerPlayer player)) {
			c.getSource().sendFailure(Component.literal("No such floor (or not a player)"));
			return 0;
		}
		WorldFloors.sendToCamp(player, floor.get());
		return 1;
	}

	private static int light(CommandContext<CommandSourceStack> c) {
		Optional<PlacedFloor> floor = Floors.forAct(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "act"));
		if (floor.isEmpty()) {
			c.getSource().sendFailure(Component.literal("No such floor"));
			return 0;
		}
		WorldFloors.lightPortal(c.getSource().getServer(), floor.get());
		c.getSource().sendSuccess(() -> Component.literal("Portal lit"), true);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		if (Floors.all(server).isEmpty()) {
			c.getSource().sendSuccess(() -> Component.literal("No floors placed this run"), false);
		}
		for (PlacedFloor floor : Floors.all(server)) {
			c.getSource().sendSuccess(() -> Component.literal("Act " + floor.actOrdinal() + ": " + describe(floor)), false);
		}
		return 1;
	}

	private static int gotoSlot(CommandContext<CommandSourceStack> c) {
		FloorSlotKind kind;
		try {
			kind = FloorSlotKind.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "kind").toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			c.getSource().sendFailure(Component.literal("Unknown slot kind"));
			return 0;
		}
		if (!(c.getSource().getEntity() instanceof ServerPlayer player)) {
			return 0;
		}
		ServerLevel level = c.getSource().getLevel();
		Optional<PlacedFloor> floor = Floors.at(c.getSource().getServer(), level.dimension().identifier(), player.blockPosition());
		Optional<FloorSlot> slot = floor.flatMap(f -> f.slots(kind).stream().findFirst());
		if (slot.isEmpty()) {
			c.getSource().sendFailure(Component.literal("Not on a floor, or it has no " + kind + " slot"));
			return 0;
		}
		BlockPos pos = slot.get().pos();
		// caves and island secrets sit at the opening (air); the vault's secret room stands on its floor
		boolean atOpening = (kind == FloorSlotKind.CAVE || kind == FloorSlotKind.SECRET) && level.getBlockState(pos).isAir();
		player.teleportTo(pos.getX() + 0.5, pos.getY() + (atOpening ? 0 : 1), pos.getZ() + 0.5);
		c.getSource().sendSuccess(() -> Component.literal(kind + " " + slot.get().tag() + " at " + pos.toShortString()), false);
		return 1;
	}

	private static int pop(CommandContext<CommandSourceStack> c) {
		if (!(c.getSource().getEntity() instanceof ServerPlayer player)) {
			return 0;
		}
		ServerLevel level = c.getSource().getLevel();
		for (BlockPos pos : BlockPos.withinManhattan(player.blockPosition(), 8)) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof NodeBlock node) {
				BlockPos at = pos.immutable();
				for (int i = 0; i < 8 && !node.hit(level, at, player); i++) {
					// hit until it pops
				}
				c.getSource().sendSuccess(() -> Component.literal("Popped " + state.getBlock() + " at " + at.toShortString()), false);
				return 1;
			}
		}
		c.getSource().sendFailure(Component.literal("No node within 8 blocks"));
		return 0;
	}

	private static int hazard(CommandContext<CommandSourceStack> c) {
		String name = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "trigger");
		FloorHazards.Trigger trigger = FloorHazards.get(name);
		if (trigger == null) {
			c.getSource().sendFailure(Component.literal("Unknown hazard " + name + "; try " + FloorHazards.names()));
			return 0;
		}
		if (!(c.getSource().getEntity() instanceof ServerPlayer player)) {
			return 0;
		}
		Optional<PlacedFloor> floor = Floors.at(c.getSource().getServer(), c.getSource().getLevel().dimension().identifier(), player.blockPosition());
		if (floor.isEmpty()) {
			c.getSource().sendFailure(Component.literal("Not on a floor"));
			return 0;
		}
		String result = trigger.fire(player, floor.get());
		c.getSource().sendSuccess(() -> Component.literal(result), false);
		return 1;
	}

	private static String describe(PlacedFloor floor) {
		StringBuilder text = new StringBuilder(floor.template() + " camp " + floor.camp().toShortString() + " r" + floor.radius()
				+ " tags " + floor.tags() + " slots:");
		for (FloorSlotKind kind : FloorSlotKind.values()) {
			int count = floor.slots(kind).size();
			if (count > 0) {
				text.append(' ').append(kind.name().toLowerCase(java.util.Locale.ROOT)).append('=').append(count);
			}
		}
		return text.toString();
	}
}
