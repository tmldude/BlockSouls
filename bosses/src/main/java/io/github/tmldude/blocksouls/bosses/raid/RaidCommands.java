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
 * {@code /bs raid summon | crystals skip | perch | enrage | wipe | info}: drive the final raid without playing through
 * it. Everything but {@code summon} acts on the nearest final dragon within 256 blocks.
 */
final class RaidCommands {
	private static final double NEAR = 256;

	private RaidCommands() {
	}

	static void init() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("raid")
				.then(Commands.literal("summon").executes(RaidCommands::summon))
				.then(Commands.literal("crystals").then(Commands.literal("skip").executes(ctx -> {
					FinalDragon dragon = nearest(ctx);
					dragon.skipCrystals();
					ctx.getSource().sendSuccess(() -> Component.literal("Crystal Order complete: shield down"), true);
					return 1;
				})))
				.then(Commands.literal("perch").executes(ctx -> {
					FinalDragon dragon = nearest(ctx);
					dragon.forcePerch();
					ctx.getSource().sendSuccess(() -> Component.literal("Dragon pulled to its perch"), true);
					return 1;
				}))
				.then(Commands.literal("enrage").executes(ctx -> {
					FinalDragon dragon = nearest(ctx);
					dragon.setFightTicks(FinalRaids.CONFIG.softEnrageTicks);
					ctx.getSource().sendSuccess(() -> Component.literal("Soft enrage on"), true);
					return 1;
				}))
				.then(Commands.literal("wipe").executes(ctx -> {
					FinalDragon dragon = nearest(ctx);
					dragon.resetFight();
					ctx.getSource().sendSuccess(() -> Component.literal("Wiped: back to the start of phase " + (dragon.phase() + 1)), true);
					return 1;
				}))
				.then(Commands.literal("info").executes(RaidCommands::info))));
	}

	private static int summon(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		EnderDragonFight fight = level.getDragonFight();
		if (fight != null) {
			FinalRaidEnd.spawn(level, BlockPos.ZERO, fight);
			source.sendSuccess(() -> Component.literal("Final dragon over the main island (one per world)"), true);
			return 1;
		}
		FinalDragon dragon = FinalRaids.TYPE.create(level, EntitySpawnReason.COMMAND);
		if (dragon == null) {
			return 0;
		}
		BlockPos here = BlockPos.containing(source.getPosition());
		dragon.setArena(FinalRaidEnd.arenaAround(here), false);
		Vec3 home = Vec3.atBottomCenterOf(dragon.home());
		dragon.snapTo(home.x, home.y, home.z, 0, 0);
		level.addFreshEntity(dragon);
		source.sendSuccess(() -> Component.literal("Final dragon summoned here (floating crystals, plates around you)"), true);
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
