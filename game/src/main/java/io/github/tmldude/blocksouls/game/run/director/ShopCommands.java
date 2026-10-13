package io.github.tmldude.blocksouls.game.run.director;

import java.util.Arrays;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;

/** {@code /bs shop open <trade|forge|brew>} and {@code /bs shop give-ingredients <recipe>} (docs/decisions/v2-boards.md). */
final class ShopCommands {
	private ShopCommands() {
	}

	static void register() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("shop")
				.then(Commands.literal("open")
						.then(Commands.argument("tab", com.mojang.brigadier.arguments.StringArgumentType.word())
								.suggests((c, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Shop.Tab.values()).map(Shop.Tab::id), builder))
								.executes(ShopCommands::open)))
				.then(Commands.literal("give-ingredients")
						.then(Commands.argument("recipe", IdentifierArgument.id())
								.suggests((c, builder) -> SharedSuggestionProvider.suggest(
										ShopRecipes.all(c.getSource().getServer()).stream().map(built -> built.entry().recipe()), builder))
								.executes(ShopCommands::giveIngredients)))));
	}

	private static int open(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		String name = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "tab");
		Shop.Tab tab = Shop.Tab.parse(name);
		if (tab == null) {
			c.getSource().sendFailure(Component.literal("No tab " + name + " (trade, forge, brew)"));
			return 0;
		}
		Shop.open(player, tab);
		return 1;
	}

	private static int giveIngredients(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		String recipe = IdentifierArgument.getId(c, "recipe").toString();
		ShopRecipes.Built built = ShopRecipes.find(c.getSource().getServer(), recipe);
		if (built == null) {
			c.getSource().sendFailure(Component.literal("No board recipe " + recipe));
			return 0;
		}
		for (ItemStack stack : ShopRecipes.ingredients(built)) {
			ShopRecipes.give(player, stack);
		}
		c.getSource().sendSuccess(() -> Component.literal("Gave the ingredients of " + recipe + " (" + built.entry().board().id() + " board)"), true);
		return 1;
	}
}
