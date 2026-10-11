package io.github.tmldude.blocksouls.common.debug;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * The shared admin/debug command {@code /bs}. Each module adds its subcommands, e.g.
 * {@code DebugCommands.register((root, context) -> root.then(Commands.literal("wave")...))}.
 * Every feature should expose enough here to be tested without playing for an hour.
 */
public final class DebugCommands {
	private static final List<Contributor> CONTRIBUTORS = new ArrayList<>();

	private DebugCommands() {
	}

	@FunctionalInterface
	public interface Contributor {
		void contribute(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context);
	}

	public static void register(Contributor contributor) {
		CONTRIBUTORS.add(contributor);
	}

	/** Called once by common's initializer. */
	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bs")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
			for (Contributor contributor : CONTRIBUTORS) {
				contributor.contribute(root, context);
			}
			dispatcher.register(root);
		});
	}
}
