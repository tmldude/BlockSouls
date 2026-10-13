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
 * {@code /bs floor open <act> [players]} (places a floor without a run), {@code /bs floor tp <act>}, {@code /bs floor info},
 * {@code /bs floor light <act>} (lights its exit portal), {@code /bs floor goto <slot kind>} and {@code /bs floor pop}
 * (pops the nearest node).
 */
public final class FloorCommands {
	private FloorCommands() {
	}

	public static void init() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("floor")
				.then(Commands.literal("open").then(Commands.argument("act", IntegerArgumentType.integer(1, 64))
						.executes(c -> open(c, 1))
						.then(Commands.argument("players", IntegerArgumentType.integer(1, 8))
								.executes(c -> open(c, IntegerArgumentType.getInteger(c, "players"))))))
				.then(Commands.literal("tp").then(Commands.argument("act", IntegerArgumentType.integer(1, 64)).executes(FloorCommands::tp)))
				.then(Commands.literal("light").then(Commands.argument("act", IntegerArgumentType.integer(1, 64)).executes(FloorCommands::light)))
				.then(Commands.literal("info").executes(FloorCommands::info))
				.then(Commands.literal("goto").then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
						.executes(FloorCommands::gotoSlot)))
				.then(Commands.literal("pop").executes(FloorCommands::pop))));
	}

	private static int open(CommandContext<CommandSourceStack> c, int players) {
		MinecraftServer server = c.getSource().getServer();
		int act = IntegerArgumentType.getInteger(c, "act");
		Optional<PlacedFloor> floor = Floors.open(server, new FloorRequest(act, BlockSoulsWorld.id("surface"), act, 0, players));
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
		player.teleportTo(pos.getX() + 0.5, pos.getY() + (kind == FloorSlotKind.CAVE || kind == FloorSlotKind.SECRET ? 0 : 1), pos.getZ() + 0.5);
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
