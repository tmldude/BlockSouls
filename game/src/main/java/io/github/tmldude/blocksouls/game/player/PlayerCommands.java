package io.github.tmldude.blocksouls.game.player;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.StringArgumentType;
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
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.player.boon.Boon;
import io.github.tmldude.blocksouls.game.player.boon.BoonCatalog;
import io.github.tmldude.blocksouls.game.player.boon.BoonTier;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.player.downed.Downed;
import io.github.tmldude.blocksouls.game.player.relic.RelicOffers;

/** {@code /bs down|revive|boon|relic ...}. */
final class PlayerCommands {
	private PlayerCommands() {
	}

	static void register() {
		DebugCommands.register((root, context) -> root
				.then(Commands.literal("down")
						.then(Commands.argument("player", EntityArgument.player()).executes(PlayerCommands::down)))
				.then(Commands.literal("revive")
						.then(Commands.argument("player", EntityArgument.player()).executes(PlayerCommands::revive)))
				.then(Commands.literal("boon")
						.then(Commands.literal("give")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("id", IdentifierArgument.id())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
														BoonCatalog.all().stream().map(boon -> Identifier.parse(boon.id())), builder))
												.executes(PlayerCommands::boonGive))))
						.then(Commands.literal("clear")
								.then(Commands.argument("player", EntityArgument.player()).executes(PlayerCommands::boonClear)))
						.then(Commands.literal("list")
								.executes(ctx -> boonList(ctx, null))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> boonList(ctx, EntityArgument.getPlayer(ctx, "player"))))))
				.then(Commands.literal("relic")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> relic(ctx, null))
								.then(Commands.argument("tier", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
												Arrays.stream(BoonTier.values()).map(BoonTier::id), builder))
										.executes(ctx -> relic(ctx, StringArgumentType.getString(ctx, "tier")))))));
	}

	private static int down(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		if (PlayerStatus.isDowned(player)) {
			ctx.getSource().sendFailure(Component.literal(player.getPlainTextName() + " is already down"));
			return 0;
		}
		Downed.goDown(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Downed " + player.getPlainTextName()), true);
		return 1;
	}

	private static int revive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		if (!PlayerStatus.isDowned(player)) {
			ctx.getSource().sendFailure(Component.literal(player.getPlainTextName() + " isn't down"));
			return 0;
		}
		Downed.revive(player, null);
		ctx.getSource().sendSuccess(() -> Component.literal("Revived " + player.getPlainTextName()), true);
		return 1;
	}

	private static int boonGive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Identifier id = IdentifierArgument.getId(ctx, "id");
		Boon boon = BoonCatalog.get(id);
		if (boon == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown boon " + id));
			return 0;
		}
		if (!PlayerBoons.grant(player, boon)) {
			ctx.getSource().sendFailure(Component.literal(player.getPlainTextName() + " already has " + id + " at max level"));
			return 0;
		}
		int level = PlayerBoons.level(player, id);
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + id + " (level " + level + ") to " + player.getPlainTextName()), true);
		return 1;
	}

	private static int boonClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PlayerBoons.clearAll(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Cleared boons of " + player.getPlainTextName()), true);
		return 1;
	}

	private static int boonList(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (player == null) {
			String all = BoonCatalog.all().stream()
					.map(boon -> boon.id() + " [" + boon.tier().id() + (boon.isCombo() ? ", combo" : "") + "]")
					.collect(Collectors.joining(", "));
			ctx.getSource().sendSuccess(() -> Component.literal(BoonCatalog.all().size() + " boons: " + all), false);
			return BoonCatalog.all().size();
		}

		Map<Identifier, Integer> owned = PlayerBoons.owned(player);
		String list = owned.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
		ctx.getSource().sendSuccess(() -> Component.literal(player.getPlainTextName() + " has " + owned.size() + " boons: " + list), false);
		return owned.size();
	}

	private static int relic(CommandContext<CommandSourceStack> ctx, String tierName) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		BoonTier tier = tierName == null ? BoonTier.COMMON : BoonTier.byId(tierName);
		if (tier == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown tier " + tierName + " (common, rare, legendary)"));
			return 0;
		}
		RelicOffers.dropRelic(player, tier);
		ctx.getSource().sendSuccess(() -> Component.literal("Dropped a " + tier.id() + " relic for " + player.getPlainTextName()), true);
		return 1;
	}
}
