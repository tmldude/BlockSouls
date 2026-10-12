package io.github.tmldude.blocksouls.game.player.origin;

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

/** {@code /bs origin list|set|clear|select|sigil ...}. */
public final class OriginCommands {
	private OriginCommands() {
	}

	public static void register() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("origin")
				.then(Commands.literal("list").executes(OriginCommands::list))
				.then(Commands.literal("set")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("id", IdentifierArgument.id())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
												OriginCatalog.all().stream().map(origin -> Identifier.parse(origin.id())), builder))
										.executes(OriginCommands::set))))
				.then(Commands.literal("clear")
						.then(Commands.argument("player", EntityArgument.player()).executes(OriginCommands::clear)))
				.then(Commands.literal("select")
						.then(Commands.argument("player", EntityArgument.player()).executes(OriginCommands::select)))
				.then(Commands.literal("sigil")
						.then(Commands.argument("player", EntityArgument.player()).executes(OriginCommands::sigil)))
				.then(Commands.literal("show")
						.then(Commands.argument("player", EntityArgument.player()).executes(OriginCommands::show)))));
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		String all = OriginCatalog.all().stream()
				.map(origin -> origin.id() + " [" + (origin.capabilities().isEmpty() ? "baseline" : String.join(", ", origin.capabilities())) + "]")
				.collect(Collectors.joining(", "));
		ctx.getSource().sendSuccess(() -> Component.literal(OriginCatalog.all().size() + " origins: " + all), false);
		return OriginCatalog.all().size();
	}

	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Identifier id = IdentifierArgument.getId(ctx, "id");
		if (!PlayerOrigins.choose(player, id)) {
			ctx.getSource().sendFailure(Component.literal("Unknown origin " + id));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Set origin " + id + " for " + player.getPlainTextName()), true);
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PlayerOrigins.clear(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Cleared the origin of " + player.getPlainTextName() + " (all v1 verbs back)"), true);
		return 1;
	}

	private static int select(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PlayerOrigins.openSelect(player);
		return 1;
	}

	private static int sigil(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PlayerOrigins.giveSigil(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Gave an Origin Sigil to " + player.getPlainTextName()), true);
		return 1;
	}

	private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Origin origin = PlayerOrigins.get(player);
		var caps = Capabilities.granted(player);
		String verbs = caps == null ? "all v1 verbs" : caps.isEmpty() ? "no verbs" : caps.stream().map(Identifier::toString).sorted().collect(Collectors.joining(", "));
		ctx.getSource().sendSuccess(() -> Component.literal(player.getPlainTextName() + ": origin " + (origin == null ? "none" : origin.id()) + ", verbs: " + verbs), false);
		return 1;
	}
}
