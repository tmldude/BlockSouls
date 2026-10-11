package io.github.tmldude.blocksouls.game.run;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;

/**
 * {@code /bs run start|new|state|over|endless}, {@code /bs wave start|end|clear|skip}, {@code /bs time dusk|dawn|night|day},
 * {@code /bs night modifier <id|none>}.
 */
final class RunCommands {
	private static final SuggestionProvider<CommandSourceStack> DIFFICULTIES = (c, b) -> SharedSuggestionProvider.suggest(
			RunSystem.config().difficulties.keySet(), b);
	private static final SuggestionProvider<CommandSourceStack> MODIFIERS = (c, b) -> SharedSuggestionProvider.suggest(
			List.of("none", RunIds.BLOOD_MOON_ID, RunIds.FOG_ID, RunIds.SPRINTER_ID), b);

	private RunCommands() {
	}

	static void register() {
		DebugCommands.register((root, context) -> {
			root.then(Commands.literal("run")
					.then(Commands.literal("start")
							.executes(c -> start(c, null))
							.then(Commands.argument("difficulty", StringArgumentType.word()).suggests(DIFFICULTIES)
									.executes(c -> start(c, StringArgumentType.getString(c, "difficulty")))))
					.then(Commands.literal("new")
							.executes(c -> newRun(c, null))
							.then(Commands.argument("difficulty", StringArgumentType.word()).suggests(DIFFICULTIES)
									.executes(c -> newRun(c, StringArgumentType.getString(c, "difficulty")))))
					.then(Commands.literal("state").executes(RunCommands::state))
					.then(Commands.literal("over").executes(c -> {
						RunSystem.endRun(c.getSource().getServer());
						return 1;
					}))
					.then(Commands.literal("endless").executes(c -> {
						RunSystem.startEndless(c.getSource().getServer());
						return feedback(c, "Endless: " + RunData.get(c.getSource().getServer()).endless);
					})));

			root.then(Commands.literal("wave")
					.then(Commands.literal("start")
							.executes(c -> waveStart(c, -1))
							.then(Commands.argument("n", IntegerArgumentType.integer(1))
									.executes(c -> waveStart(c, IntegerArgumentType.getInteger(c, "n")))))
					.then(Commands.literal("end").executes(c -> {
						RunDebug.endWave(c.getSource().getServer());
						return feedback(c, "Wave ended");
					}))
					.then(Commands.literal("clear").executes(c -> {
						int killed = RunDebug.clearWave(c.getSource().getServer());
						return feedback(c, "Killed " + killed + " wave mobs");
					}))
					.then(Commands.literal("skip").executes(c -> {
						MinecraftServer server = c.getSource().getServer();
						RunData data = RunData.get(server);
						data.wave++;
						data.setDirty();
						StarterKit.onWaveEnded(server, data, data.wave);
						RunSync.markDirty();
						return feedback(c, "Wave counter: " + data.wave);
					})));

			root.then(Commands.literal("time")
					.then(timeLiteral("dusk", () -> RunSystem.config().duskTime))
					.then(timeLiteral("dawn", () -> RunSystem.config().dawnTime))
					.then(timeLiteral("night", () -> 18000))
					.then(timeLiteral("day", () -> 1000)));

			root.then(Commands.literal("night")
					.then(Commands.literal("modifier")
							.then(Commands.argument("id", StringArgumentType.greedyString()).suggests(MODIFIERS)
									.executes(c -> nightModifier(c, StringArgumentType.getString(c, "id"))))));
		});
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> timeLiteral(String name,
			java.util.function.IntSupplier time) {
		return Commands.literal(name).executes(c -> {
			RunSystem.skipTo(c.getSource().getServer(), time.getAsInt());
			return feedback(c, "Time: " + name);
		});
	}

	private static int start(CommandContext<CommandSourceStack> c, String difficulty) {
		if (difficulty != null && !RunSystem.config().difficulties.containsKey(difficulty)) {
			c.getSource().sendFailure(Component.literal("Unknown difficulty " + difficulty + "; one of " + RunSystem.config().difficulties.keySet()));
			return 0;
		}
		RunSystem.startRun(c.getSource().getServer(), difficulty);
		return feedback(c, "Run started (" + RunData.get(c.getSource().getServer()).difficulty.id() + ")");
	}

	private static int newRun(CommandContext<CommandSourceStack> c, String difficulty) {
		if (difficulty != null && !RunSystem.config().difficulties.containsKey(difficulty)) {
			c.getSource().sendFailure(Component.literal("Unknown difficulty " + difficulty + "; one of " + RunSystem.config().difficulties.keySet()));
			return 0;
		}
		boolean stopping = RunSystem.requestNewRun(c.getSource().getServer(), difficulty);
		return feedback(c, stopping ? "New run: the server restarts on a fresh world"
				: "Not running in Docker: stop the server and delete the world folder by hand. The next run's difficulty is saved.");
	}

	private static int state(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		RunData data = RunData.get(server);
		RunSyncPayload sync = RunSync.build(server, data);
		String text = "phase=" + data.phase + " wave=" + data.wave + " night=" + data.night + " endless=" + data.endless
				+ " difficulty=" + data.difficulty.id() + " tonight=" + data.wavesThisNight + "/" + data.nightBudget
				+ " mobsLeft=" + sync.mobsLeft() + " next=" + sync.secondsToNext() + "s modifiers=" + data.nightModifiers
				+ " bosses=" + data.bossesKilled + " elitesHunting=" + data.hunters.size() + " pistolsBroken=" + data.pistolsBroken
				+ " dayTime=" + WaveMath.timeOfDay(server.overworld().getOverworldClockTime()) + " rate=" + RunSystem.clockRate(server);
		return feedback(c, text);
	}

	private static int waveStart(CommandContext<CommandSourceStack> c, int n) {
		MinecraftServer server = c.getSource().getServer();
		RunData data = RunData.get(server);
		if (!data.isActiveRun()) {
			c.getSource().sendFailure(Component.literal("No active run"));
			return 0;
		}
		RunDebug.startWave(server, n);
		return feedback(c, "Wave " + data.wave + " started (" + data.waveTotal + " mobs)");
	}

	private static int nightModifier(CommandContext<CommandSourceStack> c, String id) {
		RunData data = RunData.get(c.getSource().getServer());
		data.nightModifiers.clear();
		if (!id.equals("none")) {
			data.nightModifiers.add(id.contains(":") ? id : RunIds.NAMESPACE + ":" + id);
		}
		data.setDirty();
		RunSync.markDirty();
		return feedback(c, "Night modifiers: " + data.nightModifiers);
	}

	private static int feedback(CommandContext<CommandSourceStack> c, String message) {
		c.getSource().sendSuccess(() -> Component.literal(message), true);
		return 1;
	}
}
