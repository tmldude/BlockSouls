package io.github.tmldude.blocksouls.common.debug;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.event.ConfigEvents;

/**
 * {@code /bs config reload [name]} (op only, like all of {@code /bs}): re-reads {@code config/blocksouls/<name>.json}
 * (or every loaded file) into the live config objects, so tuning takes effect without a restart. A file that fails to
 * parse keeps its current values and the error is reported in chat.
 */
public final class ConfigCommands {
	private ConfigCommands() {
	}

	/** Called once by common's initializer. */
	public static void init() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("config")
				.then(Commands.literal("reload")
						.executes(ctx -> reload(ctx.getSource(), Config.loadedNames()))
						.then(Commands.argument("name", StringArgumentType.string())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Config.loadedNames(), builder))
								.executes(ctx -> reload(ctx.getSource(), List.of(StringArgumentType.getString(ctx, "name"))))))));
	}

	private static int reload(CommandSourceStack source, List<String> names) {
		int reloaded = 0;
		for (String name : names) {
			String error = ConfigEvents.reload(name);
			if (error == null) {
				reloaded++;
				source.sendSuccess(() -> Component.literal("Reloaded " + name + ".json"), true);
			} else {
				source.sendFailure(Component.literal("Kept the current " + name + " config: " + error));
			}
		}
		return reloaded;
	}
}
