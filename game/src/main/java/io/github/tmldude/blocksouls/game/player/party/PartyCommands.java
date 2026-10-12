package io.github.tmldude.blocksouls.game.player.party;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.game.player.ping.Pings;

/** {@code /bs party vote <id> <yes|no>}, {@code /bs party open <id> [seconds]} (a test vote), {@code /bs ping}. */
public final class PartyCommands {
	private PartyCommands() {
	}

	public static void register() {
		DebugCommands.register((root, context) -> root
				.then(Commands.literal("party")
						.then(Commands.literal("vote")
								.then(Commands.argument("id", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(PartyVotes.openVotes(), builder))
										.then(Commands.argument("yes", BoolArgumentType.bool()).executes(PartyCommands::vote))))
						.then(Commands.literal("open")
								.then(Commands.argument("id", StringArgumentType.word())
										.executes(ctx -> open(ctx, 30))
										.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
												.executes(ctx -> open(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))))
				.then(Commands.literal("ping").executes(PartyCommands::ping)));
	}

	private static int vote(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "id");
		boolean yes = BoolArgumentType.getBool(ctx, "yes");
		if (!PartyVotes.vote(player, id, yes)) {
			ctx.getSource().sendFailure(Component.literal("No open vote '" + id + "' you can answer"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Voted " + (yes ? "yes" : "no") + " on " + id), false);
		return 1;
	}

	private static int open(CommandContext<CommandSourceStack> ctx, int seconds) {
		String id = StringArgumentType.getString(ctx, "id");
		var server = ctx.getSource().getServer();
		boolean opened = PartyVotes.open(server, id, server.getPlayerList().getPlayers(),
				Component.literal("Test vote '" + id + "'"), seconds * 20,
				passed -> server.getPlayerList().broadcastSystemMessage(Component.literal("Vote " + id + ": " + (passed ? "passed" : "failed")), false));
		if (!opened) {
			ctx.getSource().sendFailure(Component.literal("A vote '" + id + "' is already open (or nobody is online)"));
			return 0;
		}
		return 1;
	}

	private static int ping(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		return Pings.ping(player) ? 1 : 0;
	}
}
