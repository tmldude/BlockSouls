package io.github.tmldude.blocksouls.game.player.classes;

import java.util.stream.Collectors;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.player.Capabilities;

/** {@code /bs class list|set|clear|select|sigil ...}. */
public final class ClassCommands {
	private ClassCommands() {
	}

	public static void register() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("class")
				.then(Commands.literal("list").executes(ClassCommands::list))
				.then(Commands.literal("set")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("id", IdentifierArgument.id())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
												ClassCatalog.all().stream().map(playerClass -> Identifier.parse(playerClass.id())), builder))
										.executes(ClassCommands::set))))
				.then(Commands.literal("clear")
						.then(Commands.argument("player", EntityArgument.player()).executes(ClassCommands::clear)))
				.then(Commands.literal("select")
						.then(Commands.argument("player", EntityArgument.player()).executes(ClassCommands::select)))
				.then(Commands.literal("sigil")
						.then(Commands.argument("player", EntityArgument.player()).executes(ClassCommands::sigil)))
				.then(Commands.literal("show")
						.then(Commands.argument("player", EntityArgument.player()).executes(ClassCommands::show)))));
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		String all = ClassCatalog.all().stream()
				.map(playerClass -> playerClass.id() + " [" + (playerClass.capabilities().isEmpty() ? "baseline" : String.join(", ", playerClass.capabilities())) + "]")
				.collect(Collectors.joining(", "));
		ctx.getSource().sendSuccess(() -> Component.literal(ClassCatalog.all().size() + " classes: " + all), false);
		return ClassCatalog.all().size();
	}

	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Identifier id = IdentifierArgument.getId(ctx, "id");
		if (!PlayerClasses.choose(player, id)) {
			ctx.getSource().sendFailure(Component.literal("Unknown class " + id));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Set class " + id + " for " + player.getPlainTextName()), true);
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PlayerClasses.clear(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Cleared the class of " + player.getPlainTextName() + " (all v1 verbs back)"), true);
		return 1;
	}

	private static int select(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PlayerClasses.openSelect(player);
		return 1;
	}

	private static int sigil(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PlayerClasses.giveSigil(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Gave a Class Sigil to " + player.getPlainTextName()), true);
		return 1;
	}

	private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PlayerClass playerClass = PlayerClasses.get(player);
		var caps = Capabilities.granted(player);
		String verbs = caps == null ? "all v1 verbs" : caps.isEmpty() ? "no verbs" : caps.stream().map(Identifier::toString).sorted().collect(Collectors.joining(", "));
		ctx.getSource().sendSuccess(() -> Component.literal(player.getPlainTextName() + ": class " + (playerClass == null ? "none" : playerClass.id()) + ", verbs: " + verbs), false);
		return 1;
	}
}
