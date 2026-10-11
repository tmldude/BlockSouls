package io.github.tmldude.blocksouls.mobs.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.mob.WaveContext;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.common.run.Difficulty;
import io.github.tmldude.blocksouls.mobs.dread.Dread;
import io.github.tmldude.blocksouls.mobs.registry.MobEntities;

/** {@code /bs spawn <mob> [count] [wave] [breaker]}, {@code /bs dread get|set <player> [value]}, {@code /bs mobs list}. */
public final class MobCommands {
	private MobCommands() {
	}

	/** Every spawnable mob by short name. Wave mobs go through their registered factory. */
	private static Map<String, EntityType<? extends Mob>> mobs() {
		Map<String, EntityType<? extends Mob>> mobs = new LinkedHashMap<>();
		for (EntityType<? extends Mob> type : List.<EntityType<? extends Mob>>of(MobEntities.SHAMBLER, MobEntities.CRAWLER, MobEntities.SCREAMER,
				MobEntities.BLOATER, MobEntities.BRUTE, MobEntities.MUTANT_BRUTE, MobEntities.INFECTED_PIGLIN, MobEntities.ASH_HOUND)) {
			mobs.put(EntityType.getKey(type).getPath(), type);
		}
		return mobs;
	}

	public static void init() {
		DebugCommands.register((root, context) -> {
			root.then(Commands.literal("spawn")
					.then(Commands.argument("mob", StringArgumentType.word())
							.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(mobs().keySet(), builder))
							.executes(ctx -> spawn(ctx, 1, 1, false))
							.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
									.executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), 1, false))
									.then(Commands.argument("wave", IntegerArgumentType.integer(1, 1000))
											.executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "count"), IntegerArgumentType.getInteger(ctx, "wave"), false))
											.then(Commands.argument("breaker", BoolArgumentType.bool())
													.executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "count"),
															IntegerArgumentType.getInteger(ctx, "wave"), BoolArgumentType.getBool(ctx, "breaker"))))))));

			root.then(Commands.literal("dread")
					.then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
						ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
						double dread = Dread.get(player);
						ctx.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + " dread: " + Math.round(dread)), false);
						return (int) dread;
					})))
					.then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
							.then(Commands.argument("value", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
								ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
								double value = DoubleArgumentType.getDouble(ctx, "value");
								Dread.set(player, value);
								ctx.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + " dread set to " + value), true);
								return 1;
							})))));

			root.then(Commands.literal("mobs").then(Commands.literal("list").executes(MobCommands::list)));
		});
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, int count, int wave, boolean breaker) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "mob");
		EntityType<? extends Mob> type = mobs().get(name);
		if (type == null) {
			source.sendFailure(Component.literal("Unknown mob: " + name + " (try /bs mobs list)"));
			return 0;
		}
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		WaveContext context = new WaveContext(wave, Difficulty.NORMAL, Set.of(), breaker);
		WaveMobSpec spec = WaveMobs.get(EntityType.getKey(type)).orElse(null);

		int spawned = 0;
		for (int i = 0; i < count; i++) {
			Mob mob;
			if (spec != null) {
				mob = spec.factory().create(level, pos, context);
			} else {
				mob = type.create(level, EntitySpawnReason.COMMAND);
				if (mob != null) {
					mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
					mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
				}
			}
			if (mob != null && level.addFreshEntity(mob)) {
				spawned++;
			}
		}
		int total = spawned;
		source.sendSuccess(() -> Component.literal("Spawned " + total + " " + name + (spec != null ? " (wave " + wave + (breaker ? ", breaker)" : ")") : "")), true);
		return spawned;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		StringBuilder out = new StringBuilder("Wave roster:");
		for (WaveMobSpec spec : WaveMobs.all()) {
			out.append("\n  ").append(spec.id().getPath()).append(": wave ").append(spec.minWave()).append("+, weight ").append(spec.weight())
					.append(spec.elite() ? ", elite" : "").append(spec.id().getNamespace().equals(CommonIds.MOBS) ? "" : " (" + spec.id().getNamespace() + ")");
		}
		out.append("\nNether: infected_piglin (natural), ash_hound (dread packs)");
		String text = out.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return WaveMobs.all().size();
	}
}
