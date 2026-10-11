package io.github.tmldude.blocksouls.vehicles;

import java.util.Locale;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.vehicles.kart.KartEntity;
import io.github.tmldude.blocksouls.vehicles.kart.KartWreckEntity;
import io.github.tmldude.blocksouls.vehicles.kart.RepairProgress;

/** {@code /bs vehicle spawn kart|wreck}, {@code /bs vehicle repair}, {@code /bs vehicle stats}. */
public final class VehicleCommands {
	private static final double LOOK_REACH = 16;

	private VehicleCommands() {
	}

	public static void register() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("vehicle")
				.then(Commands.literal("spawn")
						.then(Commands.literal("kart").executes(c -> spawn(c, VehicleRegistry.KART)))
						.then(Commands.literal("wreck").executes(c -> spawn(c, VehicleRegistry.KART_WRECK))))
				.then(Commands.literal("repair").executes(VehicleCommands::repair))
				.then(Commands.literal("stats").executes(VehicleCommands::stats))));
	}

	private static int spawn(CommandContext<CommandSourceStack> context, EntityType<?> type) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerLevel level = context.getSource().getLevel();
		Entity entity = type.create(level, EntitySpawnReason.COMMAND);
		if (entity == null) {
			return 0;
		}
		Vec3 ahead = player.position().add(Vec3.directionFromRotation(0, player.getYRot()).scale(3));
		entity.snapTo(ahead.x, player.getY(), ahead.z, player.getYRot(), 0);
		level.addFreshEntity(entity);
		context.getSource().sendSuccess(() -> Component.literal("Spawned " + type.getDescription().getString()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int repair(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Entity target = lookedAt(player);
		if (target instanceof KartWreckEntity wreck) {
			wreck.repair(context.getSource().getLevel());
			context.getSource().sendSuccess(() -> Component.literal("Wreck repaired into a kart"), false);
			return Command.SINGLE_SUCCESS;
		}
		if (target instanceof KartEntity kart) {
			kart.setHealth(BlockSoulsVehicles.CONFIG.kart.maxHealth);
			context.getSource().sendSuccess(() -> Component.literal("Kart repaired to full health"), false);
			return Command.SINGLE_SUCCESS;
		}
		context.getSource().sendFailure(Component.literal("Look at a kart or a kart wreck"));
		return 0;
	}

	private static int stats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Entity target = lookedAt(player);
		if (player.getVehicle() instanceof KartEntity riding) {
			target = riding;
		}
		VehiclesConfig cfg = BlockSoulsVehicles.CONFIG;
		String text;
		if (target instanceof KartEntity kart) {
			text = String.format(Locale.ROOT, "Kart: health %.1f/%.0f, speed %.2f b/t (max %.2f), riders %d/%d, driver %s",
					kart.getHealth(), cfg.kart.maxHealth, kart.getMeasuredSpeed(), cfg.kart.maxSpeed, kart.getPassengers().size(),
					KartEntity.MAX_PASSENGERS, kart.getControllingPassenger() == null ? "none" : kart.getControllingPassenger().getPlainTextName());
		} else if (target instanceof KartWreckEntity wreck) {
			RepairProgress progress = wreck.getProgress();
			text = String.format(Locale.ROOT, "Wreck: wheels %d/%d, engine %d/%d, scrap %d/%d",
					progress.wheels(), cfg.wreck.wheels, progress.engines(), cfg.wreck.engines, progress.scrap(), cfg.wreck.scrap);
		} else {
			text = String.format(Locale.ROOT, "Kart config: health %.0f, max speed %.2f, accel %.3f, ram above %.2f (x%.0f damage), drift boost after %d ticks",
					cfg.kart.maxHealth, cfg.kart.maxSpeed, cfg.kart.acceleration, cfg.kart.ramMinSpeed, cfg.kart.ramDamagePerSpeed,
					cfg.kart.driftBoostTicks.length > 0 ? cfg.kart.driftBoostTicks[0] : 0);
		}
		context.getSource().sendSuccess(() -> Component.literal(text), false);
		return Command.SINGLE_SUCCESS;
	}

	private static @Nullable Entity lookedAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0f).scale(LOOK_REACH));
		AABB area = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
				entity -> entity instanceof KartEntity || entity instanceof KartWreckEntity, LOOK_REACH * LOOK_REACH);
		return hit == null ? null : hit.getEntity();
	}
}
