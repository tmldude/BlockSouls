package io.github.tmldude.blocksouls.game.run.director;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.game.run.DirectorHooks;

/**
 * {@code /bs director status | log [n] [kind] | horn [overclock] | event <id> | objective <id> | lull <s> | stress <0-1>}.
 * The log reads oldest-first, one decision per line: {@code [   2:03] MOOD        BUILD -> LULL for 12s (stress 0.81)} (minutes:seconds into the run).
 */
final class DirectorCommands {
	private DirectorCommands() {
	}

	static void register() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("director")
				.then(Commands.literal("status").executes(DirectorCommands::status))
				.then(Commands.literal("log")
						.executes(c -> log(c, 20, null))
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 500))
								.executes(c -> log(c, IntegerArgumentType.getInteger(c, "n"), null))
								.then(Commands.argument("kind", StringArgumentType.word())
										.suggests((c, builder) -> SharedSuggestionProvider.suggest(
												Arrays.stream(DirectorLog.Kind.values()).map(k -> k.name().toLowerCase(Locale.ROOT)), builder))
										.executes(c -> log(c, IntegerArgumentType.getInteger(c, "n"), StringArgumentType.getString(c, "kind"))))))
				.then(Commands.literal("horn")
						.executes(c -> horn(c, false))
						.then(Commands.literal("overclock").executes(c -> horn(c, true))))
				.then(Commands.literal("event")
						.then(Commands.argument("id", StringArgumentType.word())
								.suggests((c, builder) -> SharedSuggestionProvider.suggest(
										Director.config().events.pool.stream().map(e -> e.id), builder))
								.executes(DirectorCommands::event)))
				.then(Commands.literal("objective")
						.then(Commands.argument("id", StringArgumentType.word())
								.suggests((c, builder) -> SharedSuggestionProvider.suggest(Director.config().objectives.pool, builder))
								.executes(c -> result(c, Objectives.start(c.getSource().getServer(), StringArgumentType.getString(c, "id")),
										"Objective started"))))
				.then(Commands.literal("lull")
						.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600)).executes(c -> {
							Director.forceLull(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "seconds"));
							return feedback(c, "Mood: " + Director.mood());
						})))));
	}

	private static int status(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		DirectorData data = DirectorData.get(server);
		String objective = Objectives.current();
		String text = String.format(Locale.ROOT,
				"Director %s | stress %.2f mood %s %s%s | day %s | budget %ds quiet (ceiling %ds) | hunters %d | objective %s | violations %d",
				Director.active(server) ? "on" : "off", Director.stress(), Director.mood(), Director.composition(),
				Director.camping ? " (camping)" : "", data.dayRunning() ? data.daySecondsLeft() + "/" + data.daySeconds() + "s" : "-",
				Director.budget.quietSeconds(Director.now(server)), Director.config().pacing.ceilingSeconds, DirectorHooks.hunters(server),
				objective == null ? "-" : objective + String.format(Locale.ROOT, " %.0f%%", Objectives.progress() * 100), Director.LOG.violations());
		return feedback(c, text);
	}

	private static int log(CommandContext<CommandSourceStack> c, int count, @Nullable String kindName) {
		DirectorLog.Kind kind = null;
		if (kindName != null) {
			try {
				kind = DirectorLog.Kind.valueOf(kindName.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				c.getSource().sendFailure(Component.literal("Unknown kind " + kindName + "; one of " + Arrays.toString(DirectorLog.Kind.values())));
				return 0;
			}
		}
		List<DirectorLog.Entry> entries = Director.LOG.tail(count, kind);
		if (entries.isEmpty()) {
			return feedback(c, "Director log is empty");
		}
		c.getSource().sendSuccess(() -> Component.literal("Director log (" + entries.size() + " of " + Director.LOG.size() + "):"), false);
		for (DirectorLog.Entry entry : entries) {
			c.getSource().sendSuccess(() -> Component.literal(entry.line()), false);
		}
		return entries.size();
	}

	private static int horn(CommandContext<CommandSourceStack> c, boolean overclock) {
		ServerPlayer player = c.getSource().getPlayer();
		if (player == null) {
			c.getSource().sendFailure(Component.literal("Run as a player"));
			return 0;
		}
		return result(c, DayLoop.blowHorn(player, overclock), "Horn blown");
	}

	private static int event(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		String id = StringArgumentType.getString(c, "id");
		String placed = DayLoop.placeEvent(server, DirectorHooks.level(server), Math.max(1, DirectorHooks.actOrdinal(server)), id);
		return result(c, placed == null ? "Event " + id + " couldn't be placed (see /bs director log)" : null, "Event " + id + " placed");
	}

	private static int result(CommandContext<CommandSourceStack> c, @Nullable String error, String success) {
		if (error != null) {
			c.getSource().sendFailure(Component.literal(error));
			return 0;
		}
		return feedback(c, success);
	}

	private static int feedback(CommandContext<CommandSourceStack> c, String message) {
		c.getSource().sendSuccess(() -> Component.literal(message), true);
		return 1;
	}
}
