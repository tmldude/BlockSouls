package io.github.tmldude.blocksouls.world.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.gen.Blight;
import io.github.tmldude.blocksouls.world.gen.StructureLog;
import io.github.tmldude.blocksouls.world.gen.StructurePlacer;
import io.github.tmldude.blocksouls.world.gen.layout.CheckpointLayout;
import io.github.tmldude.blocksouls.world.gen.layout.WreckLayout;

/** {@code /bs world place wreck|checkpoint}, {@code /bs world blight <radius>}, {@code /bs world locate wreck|checkpoint}. */
public final class WorldCommands {
	private WorldCommands() {
	}

	public static void init() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("world")
				.then(Commands.literal("place")
						.then(Commands.literal("wreck").executes(c -> place(c, StructureLog.Kind.WRECK)))
						.then(Commands.literal("checkpoint").executes(c -> place(c, StructureLog.Kind.CHECKPOINT))))
				.then(Commands.literal("blight")
						.then(Commands.argument("radius", IntegerArgumentType.integer(1, 16)).executes(WorldCommands::blight)))
				.then(Commands.literal("locate")
						.then(Commands.literal("wreck").executes(c -> locate(c, StructureLog.Kind.WRECK)))
						.then(Commands.literal("checkpoint").executes(c -> locate(c, StructureLog.Kind.CHECKPOINT))))));
	}

	private static BlockPos feet(CommandSourceStack source) {
		return BlockPos.containing(source.getPosition());
	}

	private static int place(CommandContext<CommandSourceStack> c, StructureLog.Kind kind) {
		ServerLevel level = c.getSource().getLevel();
		BlockPos at = feet(c.getSource());
		long seed = level.getRandom().nextLong();
		StructurePlacer.Placed placed = kind == StructureLog.Kind.WRECK
				? StructurePlacer.place(level, level.getRandom(), at, WreckLayout.generate(seed), StructurePlacer.WRECK_LOOT)
				: StructurePlacer.place(level, level.getRandom(), at, CheckpointLayout.generate(seed), StructurePlacer.CHECKPOINT_LOOT);
		StructureLog.record(level, kind, at);
		c.getSource().sendSuccess(() -> Component.literal("Placed " + kind.getSerializedName() + " at " + at.toShortString()
				+ " (" + placed.chests().size() + " chest(s), " + placed.scrapPiles().size() + " scrap pile(s), kart wreck: "
				+ (placed.kart() != null ? "yes" : "no") + ")"), true);
		return 1;
	}

	private static int blight(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		int radius = IntegerArgumentType.getInteger(c, "radius");
		Blight.Result result = Blight.infect(level, feet(c.getSource()), radius, level.getRandom(), BlockSoulsWorld.CONFIG.gen.blightHeartChance);
		c.getSource().sendSuccess(() -> Component.literal("Blighted " + result.converted() + " blocks (" + result.growths()
				+ " growths, " + result.veins() + " veins, heart: " + (result.heart() != null ? result.heart().toShortString() : "none") + ")"), true);
		return result.converted();
	}

	private static int locate(CommandContext<CommandSourceStack> c, StructureLog.Kind kind) {
		ServerLevel level = c.getSource().getLevel();
		BlockPos from = feet(c.getSource());
		return StructureLog.get(level).nearest(kind, from).map(entry -> {
			int dist = (int) Math.sqrt(entry.pos().distSqr(from));
			c.getSource().sendSuccess(() -> Component.literal("Nearest " + kind.getSerializedName() + ": "
					+ entry.pos().toShortString() + " (" + dist + " blocks)"), false);
			return 1;
		}).orElseGet(() -> {
			c.getSource().sendFailure(Component.literal("No " + kind.getSerializedName() + " generated yet (only explored chunks are known)"));
			return 0;
		});
	}
}
