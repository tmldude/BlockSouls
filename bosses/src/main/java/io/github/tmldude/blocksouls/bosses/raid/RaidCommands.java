package io.github.tmldude.blocksouls.bosses.raid;

import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;

/**
 * {@code /bs raid summon | wave | herald | stun | crystals skip | enrage | wipe | info}: drive the final raid without
 * playing through it. Everything but {@code summon} acts on the nearest final dragon within 256 blocks. Phase 3:
 * {@code /bs boss health 30}.
 */
final class RaidCommands {
	private static final double NEAR = 256;

	private RaidCommands() {
	}

	static void init() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("raid")
				.then(Commands.literal("summon").executes(RaidCommands::summon))
				.then(Commands.literal("wave").executes(ctx -> act(ctx, "Zombie wave called", d -> d.beginWave((ServerLevel) d.level()))))
				.then(Commands.literal("herald").executes(ctx -> act(ctx, "Herald called", d -> d.beginHerald((ServerLevel) d.level()))))
				.then(Commands.literal("stun").executes(ctx -> act(ctx, "Crashing down stunned", FinalDragon::forceStun)))
				.then(Commands.literal("crystals").then(Commands.literal("skip").executes(ctx -> act(ctx, "Crystal Order complete",
						FinalDragon::skipCrystals))))
				.then(Commands.literal("enrage").executes(ctx -> act(ctx, "Soft enrage on",
						d -> d.setFightTicks(FinalRaids.CONFIG.softEnrageTicks))))
				.then(Commands.literal("wipe").executes(ctx -> act(ctx, "Wiped: back to the last checkpoint", FinalDragon::resetFight)))
				.then(Commands.literal("info").executes(RaidCommands::info))));
	}

	private static int act(CommandContext<CommandSourceStack> ctx, String done, java.util.function.Consumer<FinalDragon> action)
			throws CommandSyntaxException {
		FinalDragon dragon = nearest(ctx);
		action.accept(dragon);
		ctx.getSource().sendSuccess(() -> Component.literal(done), true);
		return 1;
	}

	/** Spawns the final dragon falling where you stand (the End island in the End, a small arena elsewhere). */
	private static int summon(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		FinalDragon dragon = FinalRaids.TYPE.create(level, EntitySpawnReason.COMMAND);
		if (dragon == null) {
			return 0;
		}
		EnderDragonFight fight = level.getDragonFight();
		BlockPos here = BlockPos.containing(source.getPosition());
		dragon.setArena(fight != null ? RaidArena.fromEnd(level, ((FinalRaidFight) fight).blocksouls$origin()) : FinalRaidEnd.arenaAround(here), fight != null);
		Vec3 at = source.getPosition().add(0, 12, 0);
		dragon.snapTo(at.x, at.y, at.z, 0, 0);
		dragon.startFall();
		level.addFreshEntity(dragon);
		if (fight != null) {
			((FinalRaidFight) fight).blocksouls$setDragonUuid(dragon.getUUID());
		}
		source.sendSuccess(() -> Component.literal("The final dragon falls (phase 2 starts when it rises)"), true);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		FinalDragon dragon = nearest(ctx);
		String text = "Final dragon: " + dragon.debugInfo();
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static FinalDragon nearest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		Vec3 pos = source.getPosition();
		List<FinalDragon> dragons = source.getLevel().getEntitiesOfClass(FinalDragon.class, new AABB(pos, pos).inflate(NEAR), FinalDragon::isAlive);
		return dragons.stream().min(Comparator.comparingDouble(d -> d.distanceToSqr(pos)))
				.orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("No final dragon within " + (int) NEAR + " blocks")).create());
	}
}
