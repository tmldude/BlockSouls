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
import io.github.tmldude.blocksouls.game.player.boon.rule.BoonRules;
import io.github.tmldude.blocksouls.game.player.downed.Downed;
import io.github.tmldude.blocksouls.game.player.relic.RelicOffers;
import io.github.tmldude.blocksouls.game.player.soul.Souls;
import io.github.tmldude.blocksouls.game.player.trinket.TrinketOffers;
import io.github.tmldude.blocksouls.common.synergy.BuildHooks;
import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import com.mojang.brigadier.arguments.IntegerArgumentType;

/** {@code /bs down|revive|boon|relic|curse|trinket|souls|build ...}. */
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
										.executes(ctx -> relic(ctx, StringArgumentType.getString(ctx, "tier"))))))
				.then(Commands.literal("curse")
						.then(Commands.literal("give")
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> curseGive(ctx, null))
										.then(Commands.argument("id", IdentifierArgument.id())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
														BoonCatalog.curses().stream().map(curse -> Identifier.parse(curse.id())), builder))
												.executes(ctx -> curseGive(ctx, IdentifierArgument.getId(ctx, "id"))))))
						.then(Commands.literal("purge")
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> cursePurge(ctx, null))
										.then(Commands.argument("id", IdentifierArgument.id())
												.executes(ctx -> cursePurge(ctx, IdentifierArgument.getId(ctx, "id"))))))
						.then(Commands.literal("list")
								.then(Commands.argument("player", EntityArgument.player()).executes(PlayerCommands::curseList))))
				.then(Commands.literal("trinket")
						.then(Commands.literal("give")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("id", IdentifierArgument.id())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
														BoonCatalog.trinkets().stream().map(trinket -> Identifier.parse(trinket.id())), builder))
												.executes(PlayerCommands::trinketGive))))
						.then(Commands.literal("offer")
								.then(Commands.argument("player", EntityArgument.player()).executes(PlayerCommands::trinketOffer)))
						.then(Commands.literal("clear")
								.then(Commands.argument("player", EntityArgument.player()).executes(PlayerCommands::trinketClear)))
						.then(Commands.literal("list")
								.then(Commands.argument("player", EntityArgument.player()).executes(PlayerCommands::trinketList))))
				.then(Commands.literal("souls")
						.then(Commands.literal("give")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(PlayerCommands::soulsGive))))
						.then(Commands.literal("count")
								.then(Commands.argument("player", EntityArgument.player()).executes(PlayerCommands::soulsCount))))
				.then(Commands.literal("build")
						.then(Commands.argument("player", EntityArgument.player()).executes(PlayerCommands::build))));
	}

	private static int curseGive(CommandContext<CommandSourceStack> ctx, Identifier id) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Identifier applied = BuildHooks.applyCurse(player, id);
		if (applied == null) {
			ctx.getSource().sendFailure(Component.literal("No curse applied to " + player.getPlainTextName()
					+ (id == null ? " (none left, or at the maximum)" : " (unknown, already owned, or at the maximum)")));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Cursed " + player.getPlainTextName() + " with " + applied), true);
		return 1;
	}

	private static int cursePurge(CommandContext<CommandSourceStack> ctx, Identifier id) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		if (!BuildHooks.purgeCurse(player, id)) {
			ctx.getSource().sendFailure(Component.literal(player.getPlainTextName() + " has no such curse"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Purged a curse from " + player.getPlainTextName()), true);
		return 1;
	}

	private static int curseList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		var curses = PlayerBoons.curses(player);
		ctx.getSource().sendSuccess(() -> Component.literal(player.getPlainTextName() + " has " + curses.size() + " curses: "
				+ curses.stream().map(Identifier::toString).collect(Collectors.joining(", "))), false);
		return curses.size();
	}

	private static int trinketGive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Identifier id = IdentifierArgument.getId(ctx, "id");
		Boon trinket = BoonCatalog.get(id);
		if (trinket == null || !trinket.isTrinket()) {
			ctx.getSource().sendFailure(Component.literal("Unknown trinket " + id));
			return 0;
		}
		if (!PlayerBoons.grantTrinket(player, trinket)) {
			ctx.getSource().sendFailure(Component.literal(player.getPlainTextName() + " already has " + id));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Gave trinket " + id + " to " + player.getPlainTextName()), true);
		return 1;
	}

	private static int trinketOffer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		if (!TrinketOffers.giveCase(player)) {
			ctx.getSource().sendFailure(Component.literal(player.getPlainTextName() + " already owns every trinket"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Gave a Trinket Case to " + player.getPlainTextName()), true);
		return 1;
	}

	private static int trinketClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PlayerBoons.clearTrinkets(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Cleared trinkets of " + player.getPlainTextName()), true);
		return 1;
	}

	private static int trinketList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		var trinkets = PlayerBoons.trinkets(player);
		ctx.getSource().sendSuccess(() -> Component.literal(player.getPlainTextName() + " has " + trinkets.size() + " trinkets: "
				+ trinkets.stream().map(Identifier::toString).collect(Collectors.joining(", "))), false);
		return trinkets.size();
	}

	private static int soulsGive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int amount = IntegerArgumentType.getInteger(ctx, "amount");
		Souls.give(player, amount);
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + amount + " souls to " + player.getPlainTextName()), true);
		return amount;
	}

	private static int soulsCount(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int count = Souls.count(player);
		ctx.getSource().sendSuccess(() -> Component.literal(player.getPlainTextName() + " carries " + count + " souls"), false);
		return count;
	}

	/** The build's tags by stacks, plus the rules currently in effect. */
	private static int build(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String tags = SynergyTags.of(player).entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
				.map(e -> e.getKey().getPath() + " ×" + e.getValue())
				.collect(Collectors.joining(", "));
		java.util.List<String> rules = new java.util.ArrayList<>();
		BoonRules.forEach(player, (rule, level) -> rules.add(rule.getClass().getSimpleName() + (level > 1 ? " L" + level : "")));
		ctx.getSource().sendSuccess(() -> Component.literal(player.getPlainTextName() + " build: " + (tags.isEmpty() ? "(no tags)" : tags)
				+ "; rules: " + (rules.isEmpty() ? "(none)" : String.join(", ", rules))), false);
		return 1;
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
