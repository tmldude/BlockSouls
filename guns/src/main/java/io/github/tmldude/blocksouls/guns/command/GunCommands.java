package io.github.tmldude.blocksouls.guns.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.guns.item.GunItem;
import io.github.tmldude.blocksouls.guns.item.GunRolls;
import io.github.tmldude.blocksouls.guns.item.GunsItems;
import io.github.tmldude.blocksouls.guns.logic.GunRarity;
import io.github.tmldude.blocksouls.guns.logic.Perk;
import io.github.tmldude.blocksouls.guns.weapon.GunStats;

/**
 * {@code /bs gun give <gun> [rarity] [perk...]}, {@code /bs gun ammo <player> [amount]}, {@code /bs gun stats <gun>}.
 */
public final class GunCommands {
	private static final SuggestionProvider<CommandSourceStack> GUNS = (context, builder) ->
			SharedSuggestionProvider.suggest(GunsItems.guns().stream().map(GunItem::weaponId).toList(), builder);
	private static final SuggestionProvider<CommandSourceStack> RARITIES = (context, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(GunRarity.values()).map(GunRarity::id).toList(), builder);
	private static final SuggestionProvider<CommandSourceStack> PERKS = (context, builder) -> {
		// Suggest the next perk after any already typed.
		String typed = builder.getRemaining();
		int split = typed.lastIndexOf(' ') + 1;
		return SharedSuggestionProvider.suggest(Arrays.stream(Perk.values()).map(Perk::id).toList(), builder.createOffset(builder.getStart() + split));
	};

	private GunCommands() {
	}

	public static void register() {
		DebugCommands.register((root, buildContext) -> root.then(Commands.literal("gun")
				.then(Commands.literal("give")
						.then(Commands.argument("gun", StringArgumentType.word()).suggests(GUNS)
								.executes(context -> give(context, null, ""))
								.then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
										.executes(context -> give(context, StringArgumentType.getString(context, "rarity"), ""))
										.then(Commands.argument("perks", StringArgumentType.greedyString()).suggests(PERKS)
												.executes(context -> give(context, StringArgumentType.getString(context, "rarity"),
														StringArgumentType.getString(context, "perks")))))))
				.then(Commands.literal("ammo")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(context -> ammo(context, 64))
								.then(Commands.argument("amount", IntegerArgumentType.integer(1, 2304))
										.executes(context -> ammo(context, IntegerArgumentType.getInteger(context, "amount"))))))
				.then(Commands.literal("stats")
						.then(Commands.argument("gun", StringArgumentType.word()).suggests(GUNS)
								.executes(GunCommands::stats)))));
	}

	private static Optional<GunItem> gun(String id) {
		return GunsItems.guns().stream().filter(gun -> gun.weaponId().equals(id)).findFirst();
	}

	private static int give(CommandContext<CommandSourceStack> context, String rarity, String perkList) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		String id = StringArgumentType.getString(context, "gun");
		Optional<GunItem> gun = gun(id);
		if (gun.isEmpty()) {
			source.sendFailure(Component.literal("Unknown gun '" + id + "'"));
			return 0;
		}
		if (rarity != null && !GunRarity.isValid(rarity)) {
			source.sendFailure(Component.literal("Unknown rarity '" + rarity + "' (common, rare, legendary)"));
			return 0;
		}

		List<Perk> perks = new ArrayList<>();
		for (String perkId : perkList.trim().split("\\s+")) {
			if (perkId.isEmpty()) {
				continue;
			}
			Optional<Perk> perk = Perk.byId(perkId);
			if (perk.isEmpty()) {
				source.sendFailure(Component.literal("Unknown perk '" + perkId + "'"));
				return 0;
			}
			if (!perks.contains(perk.get())) {
				perks.add(perk.get());
			}
		}

		ItemStack stack = new ItemStack(gun.get());
		if (rarity != null && gun.get().stats().perks()) {
			// Explicit perks win; otherwise roll the rarity's usual perk count.
			GunRarity chosen = GunRarity.byId(rarity);
			if (perks.isEmpty()) {
				GunRolls.roll(stack, oddsFor(chosen), player.getRandom());
			} else {
				GunRolls.set(stack, chosen, perks);
			}
		}
		giveOrDrop(player, stack);
		source.sendSuccess(() -> Component.literal("Gave ").append(stack.getHoverName()), true);
		return 1;
	}

	private static double[] oddsFor(GunRarity rarity) {
		double[] odds = new double[GunRarity.values().length];
		odds[rarity.ordinal()] = 1;
		return odds;
	}

	private static int ammo(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		for (Item ammo : List.of(GunsItems.PRIMARY_AMMO, GunsItems.SPECIAL_AMMO)) {
			int left = amount;
			while (left > 0) {
				int count = Math.min(left, new ItemStack(ammo).getMaxStackSize());
				giveOrDrop(player, new ItemStack(ammo, count));
				left -= count;
			}
		}
		context.getSource().sendSuccess(() -> Component.literal("Gave " + amount + " of each ammo to ").append(player.getDisplayName()), true);
		return 1;
	}

	private static int stats(CommandContext<CommandSourceStack> context) {
		String id = StringArgumentType.getString(context, "gun");
		Optional<GunItem> gun = gun(id);
		if (gun.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Unknown gun '" + id + "'"));
			return 0;
		}
		GunStats s = gun.get().stats();
		context.getSource().sendSuccess(() -> Component.literal(id + ": ammo " + s.ammo() + ", " + s.mode() + ", damage " + s.damage()
				+ (s.pellets() > 1 ? "×" + s.pellets() : "") + ", " + s.rpm() + " rpm, mag " + s.magSize() + ", reload " + s.reloadSeconds()
				+ "s, precision ×" + s.precisionMult() + ", range " + s.range() + ", spread " + s.hipSpread() + "°/" + s.adsSpread()
				+ "° (hip/ADS), zoom ×" + s.adsZoom() + ", falloff " + s.falloffStart() + "→×" + s.falloffMinMult()), false);
		return 1;
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		// Drops what doesn't fit.
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
