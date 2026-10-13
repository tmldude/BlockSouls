package io.github.tmldude.blocksouls.game.player.rune;

import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import io.github.tmldude.blocksouls.game.player.classes.ClassCatalog;
import io.github.tmldude.blocksouls.game.player.classes.PlayerClass;
import io.github.tmldude.blocksouls.game.player.classes.PlayerClasses;

/** {@code /bs rune list|show|unlock|souls|reset|tree ...}. */
public final class RuneCommands {
	private RuneCommands() {
	}

	public static void register() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("rune")
				.then(Commands.literal("list")
						.executes(ctx -> list(ctx, null))
						.then(Commands.argument("class", IdentifierArgument.id())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(ClassCatalog.all().stream().map(c -> Identifier.parse(c.id())), builder))
								.executes(ctx -> list(ctx, IdentifierArgument.getId(ctx, "class")))))
				.then(Commands.literal("show")
						.then(Commands.argument("player", EntityArgument.player()).executes(RuneCommands::show)))
				.then(Commands.literal("unlock")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("id", IdentifierArgument.id())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(RuneCatalog.all().stream().map(r -> Identifier.parse(r.id())), builder))
										.executes(RuneCommands::unlock))))
				.then(Commands.literal("souls")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("amount", IntegerArgumentType.integer(-100000, 100000)).executes(RuneCommands::souls))))
				.then(Commands.literal("reset")
						.then(Commands.argument("player", EntityArgument.player()).executes(RuneCommands::reset)))
				.then(Commands.literal("tree")
						.then(Commands.argument("player", EntityArgument.player()).executes(RuneCommands::tree)))));
	}

	private static int list(CommandContext<CommandSourceStack> ctx, Identifier classId) {
		var runes = classId == null ? RuneCatalog.all() : RuneCatalog.forClass(classId.toString());
		String all = runes.stream().map(r -> r.id() + " (" + PlayerRunes.cost(r) + (r.requires().isEmpty() ? "" : ", after " + String.join("+", r.requires())) + ")")
				.collect(Collectors.joining(", "));
		ctx.getSource().sendSuccess(() -> Component.literal(runes.size() + " runes: " + all), false);
		return runes.size();
	}

	private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		RuneProfile profile = PlayerRunes.profile(player);
		ctx.getSource().sendSuccess(() -> Component.literal(player.getPlainTextName() + ": " + profile.souls + " rune souls, " + profile.unlocked.size()
				+ " unlocked: " + String.join(", ", profile.unlocked) + "; active now: " + PlayerRunes.active(player).size()), false);
		return 1;
	}

	private static int unlock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Identifier id = IdentifierArgument.getId(ctx, "id");
		Rune rune = RuneCatalog.get(id);
		if (rune == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown rune " + id));
			return 0;
		}
		var blocker = PlayerRunes.blocker(player, rune);
		if (blocker.isPresent()) {
			ctx.getSource().sendFailure(Component.literal("Can't unlock " + id + ": " + blocker.get()));
			return 0;
		}
		PlayerRunes.unlock(player, rune);
		ctx.getSource().sendSuccess(() -> Component.literal("Unlocked " + id + " for " + player.getPlainTextName()), true);
		return 1;
	}

	private static int souls(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int total = PlayerRunes.addSouls(player, IntegerArgumentType.getInteger(ctx, "amount"));
		ctx.getSource().sendSuccess(() -> Component.literal(player.getPlainTextName() + " now has " + total + " rune souls"), true);
		return total;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PlayerRunes.reset(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Reset the rune profile of " + player.getPlainTextName()), true);
		return 1;
	}

	private static int tree(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PlayerClass playerClass = PlayerClasses.get(player);
		Identifier classId = playerClass != null ? Identifier.parse(playerClass.id()) : Identifier.parse(PlayerClasses.config().soloDefault);
		PlayerRunes.sendTree(player, classId);
		return 1;
	}
}
