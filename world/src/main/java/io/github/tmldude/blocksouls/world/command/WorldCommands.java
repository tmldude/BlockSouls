package io.github.tmldude.blocksouls.world.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.datafixers.util.Pair;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.gen.Blight;
import io.github.tmldude.blocksouls.world.gen.RuinStructure;
import io.github.tmldude.blocksouls.world.gen.StructurePlacer;
import io.github.tmldude.blocksouls.world.gen.WorldGen;

/** {@code /bs world place wreck|checkpoint}, {@code /bs world blight <radius>}, {@code /bs world locate wreck|checkpoint}. */
public final class WorldCommands {
	private static final int LOCATE_RADIUS_CHUNKS = 100;

	private WorldCommands() {
	}

	public static void init() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("world")
				.then(Commands.literal("place")
						.then(Commands.literal("wreck").executes(c -> place(c, RuinStructure.Kind.WRECK)))
						.then(Commands.literal("checkpoint").executes(c -> place(c, RuinStructure.Kind.CHECKPOINT))))
				.then(Commands.literal("blight")
						.then(Commands.argument("radius", IntegerArgumentType.integer(1, 16)).executes(WorldCommands::blight)))
				.then(Commands.literal("locate")
						.then(Commands.literal("wreck").executes(c -> locate(c, WorldGen.WRECK)))
						.then(Commands.literal("checkpoint").executes(c -> locate(c, WorldGen.CHECKPOINT))))));
	}

	private static BlockPos feet(CommandSourceStack source) {
		return BlockPos.containing(source.getPosition());
	}

	private static int place(CommandContext<CommandSourceStack> c, RuinStructure.Kind kind) {
		ServerLevel level = c.getSource().getLevel();
		BlockPos at = feet(c.getSource());
		StructurePlacer.Placed placed = StructurePlacer.place(level, level.getRandom(), at, kind.layout(level.getRandom().nextLong()), kind.loot);
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

	/** Same search as vanilla {@code /locate structure}: nearest, explored or not, up to 100 chunks out. */
	private static int locate(CommandContext<CommandSourceStack> c, ResourceKey<Structure> key) {
		ServerLevel level = c.getSource().getLevel();
		BlockPos from = feet(c.getSource());
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(structure), from, LOCATE_RADIUS_CHUNKS, false);
		String name = key.identifier().getPath();
		if (found == null) {
			c.getSource().sendFailure(Component.literal("No " + name + " within " + LOCATE_RADIUS_CHUNKS + " chunks"));
			return 0;
		}
		BlockPos pos = found.getFirst();
		int dist = (int) Math.sqrt(from.distToLowCornerSqr(pos.getX(), from.getY(), pos.getZ()));
		c.getSource().sendSuccess(() -> Component.literal("Nearest " + name + ": " + pos.getX() + " ~ " + pos.getZ()
				+ " (" + dist + " blocks)"), false);
		return dist;
	}
}
