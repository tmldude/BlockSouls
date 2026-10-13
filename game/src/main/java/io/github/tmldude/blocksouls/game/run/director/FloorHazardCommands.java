package io.github.tmldude.blocksouls.game.run.director;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;

/**
 * {@code /bs director hazard <trigger>}: fires one of the current floor's zone problems now ({@code blight_storm},
 * {@code spawn_surge}, {@code dead_camp}, by id or type), or {@code bosses} to send out its roaming field bosses.
 * Lives under {@code /bs director} because the world module's {@code /bs floor hazard} already names its own floor triggers
 * (Skyrift); feature modules can't share one argument node.
 */
final class FloorHazardCommands {
	private FloorHazardCommands() {
	}

	static void register() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("director")
				.then(Commands.literal("hazard")
						.then(Commands.argument("trigger", StringArgumentType.word())
								.suggests((c, builder) -> SharedSuggestionProvider.suggest(FloorHazards.triggers(), builder))
								.executes(FloorHazardCommands::hazard)))));
	}

	private static int hazard(CommandContext<CommandSourceStack> c) {
		String trigger = StringArgumentType.getString(c, "trigger");
		String error = FloorHazards.trigger(c.getSource().getServer(), trigger);
		if (error != null) {
			c.getSource().sendFailure(Component.literal(error));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Hazard " + trigger + " fired"), true);
		return 1;
	}
}
