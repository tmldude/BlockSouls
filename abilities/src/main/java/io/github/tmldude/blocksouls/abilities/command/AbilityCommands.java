package io.github.tmldude.blocksouls.abilities.command;

import java.util.Arrays;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.abilities.AbilityIds;
import io.github.tmldude.blocksouls.abilities.AbilityServer;
import io.github.tmldude.blocksouls.common.ability.Abilities;
import io.github.tmldude.blocksouls.common.ability.AbilityDefinition;
import io.github.tmldude.blocksouls.common.ability.AbilityRegistry;
import io.github.tmldude.blocksouls.common.ability.AbilitySlot;
import io.github.tmldude.blocksouls.common.debug.DebugCommands;

/**
 * {@code /bs ability equip <player> <slot> <ability>}, {@code ready <player>}, {@code charge <player> <0..1>},
 * {@code cast <player> <slot>}, {@code status <player>}, {@code list}.
 */
public final class AbilityCommands {
	private static final SuggestionProvider<CommandSourceStack> SLOTS = (context, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(AbilitySlot.values()).map(AbilitySlot::getSerializedName).toList(), builder);
	private static final SuggestionProvider<CommandSourceStack> ABILITIES = (context, builder) ->
			SharedSuggestionProvider.suggest(AbilityIds.ALL.stream().map(Identifier::getPath).toList(), builder);

	private AbilityCommands() {
	}

	public static void register() {
		DebugCommands.register((root, buildContext) -> root.then(Commands.literal("ability")
				.then(Commands.literal("equip")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOTS)
										.then(Commands.argument("ability", StringArgumentType.word()).suggests(ABILITIES)
												.executes(AbilityCommands::equip)))))
				.then(Commands.literal("ready")
						.then(Commands.argument("player", EntityArgument.player()).executes(AbilityCommands::ready)))
				.then(Commands.literal("charge")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("charge", DoubleArgumentType.doubleArg(0, 1)).executes(AbilityCommands::charge))))
				.then(Commands.literal("cast")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOTS).executes(AbilityCommands::cast))))
				.then(Commands.literal("status")
						.then(Commands.argument("player", EntityArgument.player()).executes(AbilityCommands::status)))
				.then(Commands.literal("list").executes(AbilityCommands::list))));
	}

	private static AbilitySlot slot(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "slot");
		AbilitySlot slot = AbilitySlot.byName(name);
		if (slot == null) {
			context.getSource().sendFailure(Component.literal("Unknown slot '" + name + "' (tactical, signature, ultimate)"));
		}
		return slot;
	}

	private static int equip(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		AbilitySlot slot = slot(context);
		if (slot == null) {
			return 0;
		}
		String name = StringArgumentType.getString(context, "ability");
		Identifier id = AbilityIds.parse(name);
		AbilityDefinition definition = AbilityRegistry.get(id);
		if (definition == null) {
			context.getSource().sendFailure(Component.literal("Unknown ability '" + name + "'"));
			return 0;
		}
		if (definition.slot() != slot) {
			context.getSource().sendFailure(Component.literal(name + " is a " + definition.slot().getSerializedName() + " ability"));
			return 0;
		}
		Abilities.equip(player, slot, id);
		context.getSource().sendSuccess(() -> Component.literal("Equipped " + id.getPath() + " in " + slot.getSerializedName() + " for ")
				.append(player.getDisplayName()), true);
		return 1;
	}

	private static int ready(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		Abilities.clearCooldowns(player);
		Abilities.setUltimateCharge(player, 1.0);
		context.getSource().sendSuccess(() -> Component.literal("Cooldowns cleared and ultimate charged for ").append(player.getDisplayName()), true);
		return 1;
	}

	private static int charge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		double charge = DoubleArgumentType.getDouble(context, "charge");
		Abilities.setUltimateCharge(player, charge);
		context.getSource().sendSuccess(() -> Component.literal("Ultimate charge " + Math.round(charge * 100) + "% for ").append(player.getDisplayName()), true);
		return 1;
	}

	private static int cast(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		AbilitySlot slot = slot(context);
		if (slot == null) {
			return 0;
		}
		boolean cast = AbilityServer.tryCast(player, slot);
		if (!cast) {
			context.getSource().sendFailure(Component.literal(slot.getSerializedName() + " didn't go off (empty, on cooldown, not charged, downed, or no target)"));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Cast " + slot.getSerializedName() + " for ").append(player.getDisplayName()), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(context, "player");
		StringBuilder text = new StringBuilder();
		for (AbilitySlot slot : AbilitySlot.values()) {
			Identifier id = Abilities.equipped(player, slot);
			text.append(slot.getSerializedName()).append(": ").append(id == null ? "-" : id.getPath());
			if (slot == AbilitySlot.ULTIMATE) {
				text.append(" (").append(Math.round(Abilities.ultimateCharge(player) * 100)).append("%)");
			} else {
				long remaining = Abilities.remainingTicks(player, slot);
				text.append(remaining > 0 ? " (" + (remaining / 20.0) + "s)" : " (ready)");
			}
			text.append("  ");
		}
		context.getSource().sendSuccess(() -> Component.literal(text.toString().trim()), false);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		StringBuilder text = new StringBuilder();
		for (AbilitySlot slot : AbilitySlot.values()) {
			text.append(slot.getSerializedName()).append(": ");
			text.append(String.join(", ", AbilityRegistry.bySlot(slot).stream().map(d -> d.id().getPath()).toList()));
			text.append("\n");
		}
		context.getSource().sendSuccess(() -> Component.literal(text.toString().trim()), false);
		return 1;
	}
}
