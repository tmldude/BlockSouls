package io.github.tmldude.blocksouls.movement;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import io.github.tmldude.blocksouls.common.player.Capabilities;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.stamina.StaminaState;
import io.github.tmldude.blocksouls.movement.sync.ConfigSync;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;

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

	private static final Map<String, Identifier> CAPABILITIES = Map.of(
			"climb", Capabilities.CLIMB,
			"swing", Capabilities.SWING,
			"multi_jump", Capabilities.MULTI_JUMP,
			"glide", Capabilities.GLIDE);

	private MovementCommands() {
	}

	private static int showCaps(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Set<Identifier> set = Capabilities.granted(player);
		String text = set == null ? "all v1 verbs (no set attached)" : set.isEmpty() ? "none" : set.stream().map(Identifier::toString).sorted().collect(Collectors.joining(", "));
		ctx.getSource().sendSuccess(() -> Component.literal("Capabilities of ").append(player.getDisplayName()).append(": " + text), false);
		return 1;
	}

	/** {@code /bs move caps <player> all|none|<verb verb ...>}: restrict a player's movement verbs without an origin. */
	private static int setCaps(CommandContext<CommandSourceStack> ctx, String verbs) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		if (verbs == null) {
			Capabilities.grantAll(player);
			ctx.getSource().sendSuccess(() -> Component.literal("Restored the default (v1) verbs for ").append(player.getDisplayName()), true);
			return 1;
		}
		Set<Identifier> set = new HashSet<>();
		for (String verb : verbs.trim().split("\\s+")) {
			if (verb.isEmpty()) {
				continue;
			}
			Identifier id = CAPABILITIES.get(verb);
			if (id == null) {
				ctx.getSource().sendFailure(Component.literal("Unknown verb: " + verb + " (" + String.join(", ", CAPABILITIES.keySet()) + ")"));
				return 0;
			}
			set.add(id);
		}
		Capabilities.restrictTo(player, set);
		ctx.getSource().sendSuccess(() -> Component.literal("Restricted ").append(player.getDisplayName()).append(" to " + (set.isEmpty() ? "no verbs" : verbs.trim())), true);
		return 1;
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
				.then(Commands.literal("caps")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(MovementCommands::showCaps)
								.then(Commands.literal("all").executes(ctx -> setCaps(ctx, null)))
								.then(Commands.literal("none").executes(ctx -> setCaps(ctx, "")))
								.then(Commands.argument("verbs", StringArgumentType.greedyString())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CAPABILITIES.keySet(), builder))
										.executes(ctx -> setCaps(ctx, StringArgumentType.getString(ctx, "verbs"))))))
				.then(Commands.literal("config")
						.then(Commands.literal("reload").executes(ctx -> {
							ConfigSync.reload(ctx.getSource().getServer());
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
		float value = (float) Math.min(FloatArgumentType.getFloat(ctx, "value"), MovementTraits.staminaMax(player));
		StaminaAttachment.set(player, new StaminaState(value, 0));
		ctx.getSource().sendSuccess(() -> Component.literal("Set stamina to " + value + " for ").append(player.getDisplayName()), true);
		return 1;
	}
}
