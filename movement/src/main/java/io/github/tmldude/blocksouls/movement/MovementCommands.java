package io.github.tmldude.blocksouls.movement;

import java.util.Map;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.stamina.StaminaState;
import io.github.tmldude.blocksouls.movement.sync.ConfigSync;

/** {@code /bs move trait|stamina|config}: test movement traits and tuning without playing a run. */
public final class MovementCommands {
	private static final Map<String, Identifier> TRAITS = Map.of(
			"double_jump", PlayerTraits.DOUBLE_JUMP,
			"roll_iframes", PlayerTraits.ROLL_IFRAMES,
			"free_roll_on_kill", PlayerTraits.FREE_ROLL_ON_KILL,
			"web_reach", PlayerTraits.WEB_REACH,
			"climb_speed", PlayerTraits.CLIMB_SPEED,
			"stamina_regen", PlayerTraits.STAMINA_REGEN,
			"flight", PlayerTraits.FLIGHT);

	private MovementCommands() {
	}

	public static void register() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("move")
				.then(Commands.literal("trait")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("trait", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TRAITS.keySet(), builder))
										.then(Commands.argument("level", IntegerArgumentType.integer(0, 10))
												.executes(MovementCommands::setTrait)))))
				.then(Commands.literal("stamina")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("value", FloatArgumentType.floatArg(0))
										.executes(MovementCommands::setStamina))))
				.then(Commands.literal("config")
						.then(Commands.literal("reload").executes(ctx -> {
							String error = ConfigSync.reload();
							if (error != null) {
								ctx.getSource().sendFailure(Component.literal("Kept the current movement config: " + error));
								return 0;
							}
							ctx.getSource().sendSuccess(() -> Component.literal("Reloaded movement.json and sent it to all players"), true);
							return 1;
						})))));
	}

	private static int setTrait(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String name = StringArgumentType.getString(ctx, "trait");
		Identifier trait = TRAITS.get(name);
		if (trait == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown movement trait: " + name));
			return 0;
		}

		int level = IntegerArgumentType.getInteger(ctx, "level");
		PlayerTraits.set(player, trait, level);
		ctx.getSource().sendSuccess(() -> Component.literal("Set " + name + " to " + level + " for ").append(player.getDisplayName()), true);
		return 1;
	}

	private static int setStamina(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		float value = (float) Math.min(FloatArgumentType.getFloat(ctx, "value"), BlockSoulsMovement.CONFIG.stamina.max);
		StaminaAttachment.set(player, new StaminaState(value, 0));
		ctx.getSource().sendSuccess(() -> Component.literal("Set stamina to " + value + " for ").append(player.getDisplayName()), true);
		return 1;
	}
}
