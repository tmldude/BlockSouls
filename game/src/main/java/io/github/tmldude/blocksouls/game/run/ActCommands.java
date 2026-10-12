package io.github.tmldude.blocksouls.game.run;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.tmldude.blocksouls.common.act.Act;
import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.game.run.clock.SiegeClock;

/** {@code /bs act status|advance|charge}, {@code /bs threat get|set <n>}, {@code /bs clock phase [dim]}. */
final class ActCommands {
	private ActCommands() {
	}

	static void register() {
		DebugCommands.register((root, context) -> {
			root.then(Commands.literal("act")
					.then(Commands.literal("status").executes(ActCommands::status))
					.then(Commands.literal("advance").executes(c -> result(c, ActMachine.advance(c.getSource().getServer(),
							RunData.get(c.getSource().getServer())), "Act advanced: " + describeAct(c.getSource().getServer()))))
					.then(Commands.literal("charge").executes(c -> result(c, ActMachine.startCharge(c.getSource().getServer(),
							RunData.get(c.getSource().getServer())), "Portal charge started"))));

			root.then(Commands.literal("threat")
					.then(Commands.literal("get").executes(c -> feedback(c, "Threat: " + RunData.get(c.getSource().getServer()).wave)))
					.then(Commands.literal("set")
							.then(Commands.argument("n", IntegerArgumentType.integer(0)).executes(c -> {
								RunData data = RunData.get(c.getSource().getServer());
								data.wave = IntegerArgumentType.getInteger(c, "n");
								data.setDirty();
								RunSync.markDirty();
								return feedback(c, "Threat: " + data.wave);
							}))));

			root.then(Commands.literal("clock")
					.then(Commands.literal("phase")
							.executes(c -> clockPhase(c, null))
							.then(Commands.argument("dim", DimensionArgument.dimension())
									.executes(c -> clockPhase(c, DimensionArgument.getDimension(c, "dim"))))));
		});
	}

	private static int status(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		RunData data = RunData.get(server);
		SiegeClock clock = RunSystem.activeClock(server);
		String charge = data.chargingAct > 0
				? "charging act " + data.chargingAct + " (" + Math.max(0, data.chargeEndsAt - server.overworld().getGameTime()) / 20 + "s left)"
				: "no charge";
		String text = "shape=" + RunSystem.config().runShape + " act=" + describeAct(server) + "/" + ActMachine.acts().size()
				+ " open=" + data.highestOpenAct + " threat=" + data.wave + " " + charge
				+ " activeDim=" + RunSystem.activeLevel(server).dimension().identifier()
				+ " clock=" + (clock.synthetic() ? "synthetic" : "sun") + "@" + clock.timeOfDay();
		return feedback(c, text);
	}

	private static String describeAct(MinecraftServer server) {
		Act act = ActMachine.current(server);
		return act.ordinal() + " (" + act.displayName() + ")";
	}

	private static int clockPhase(CommandContext<CommandSourceStack> c, ServerLevel dimension) {
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = dimension != null ? dimension : RunSystem.activeLevel(server);
		RunData data = RunData.get(server);
		boolean sun = level == server.overworld();
		int phase = sun ? WaveMath.timeOfDay(server.overworld().getOverworldClockTime())
				: data.synthetic.started() ? data.synthetic.timeOfDay() : -1;
		return feedback(c, level.dimension().identifier() + ": " + (sun ? "sun" : "synthetic") + " phase " + phase);
	}

	private static int result(CommandContext<CommandSourceStack> c, String error, String success) {
		if (error != null) {
			c.getSource().sendFailure(Component.literal(error));
			return 0;
		}
		return feedback(c, success);
	}

	private static int feedback(CommandContext<CommandSourceStack> c, String message) {
		c.getSource().sendSuccess(() -> Component.literal(message), true);
		return 1;
	}
}
