package io.github.tmldude.blocksouls.bosses.riftwarden;

import java.util.Optional;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;

/**
 * {@code /bs boss final <p1|p2|p3>}: the final arena's boss in phase 1, 2 or 3 (the damage rush). Uses the arena the
 * player stands in (spawning the boss on its plate if it isn't out yet), or spawns one beside the player off a floor.
 * Health is set just under the phase's threshold so the phase holds.
 */
final class RiftwardenCommands {
	private RiftwardenCommands() {
	}

	static void init() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("boss").then(Commands.literal("final")
				.then(Commands.literal("p1").executes(c -> phase(c, 0)))
				.then(Commands.literal("p2").executes(c -> phase(c, 1)))
				.then(Commands.literal("p3").executes(c -> phase(c, 2))))));
	}

	private static int phase(CommandContext<CommandSourceStack> c, int phase) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = c.getSource().getLevel();
		Riftwarden boss = bossFor(level, player);
		if (boss == null) {
			c.getSource().sendFailure(Component.literal("Could not spawn the final boss"));
			return 0;
		}
		if (!boss.isEngaged()) {
			boss.engage(player);
		}
		force(boss, phase);
		c.getSource().sendSuccess(() -> Component.literal(boss.getDisplayName().getString() + " → phase " + (boss.phase() + 1) + ", health "
				+ Math.round(boss.getHealth()) + "/" + Math.round(boss.getMaxHealth())), true);
		return 1;
	}

	/** Sets health to the top of the phase's band and enters it. */
	static void force(Riftwarden boss, int phase) {
		RiftwardenConfig c = Riftwarden.config();
		double share = switch (phase) {
			case 0 -> 1.0;
			case 1 -> c.phase2Threshold - 0.01;
			default -> c.phase3Threshold - 0.01;
		};
		boss.setHealth((float) Math.max(1, boss.getMaxHealth() * share));
		boss.forcePhase(phase);
	}

	private static Riftwarden bossFor(ServerLevel level, ServerPlayer player) {
		Optional<PlacedFloor> floor = Floors.at(level.getServer(), level.dimension().identifier(), player.blockPosition());
		Optional<FloorSlot> slot = floor.flatMap(RiftArena::arenaSlot);
		if (slot.isPresent()) {
			return RiftArena.find(level, slot.get().pos(), slot.get().radius()).orElseGet(() -> RiftArena.spawn(level, slot.get()));
		}
		Optional<Riftwarden> near = RiftArena.find(level, player.blockPosition(), 48);
		if (near.isPresent()) {
			return near.get();
		}
		FloorSlot beside = new FloorSlot(FloorSlotKind.STRUCTURE, "", player.blockPosition().offset(0, 0, 8),
				Riftwarden.config().arenaRadius - 1);
		return RiftArena.spawn(level, beside);
	}
}
