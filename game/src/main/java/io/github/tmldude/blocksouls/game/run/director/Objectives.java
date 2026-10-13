package io.github.tmldude.blocksouls.game.run.director;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.event.DirectorEvents;
import io.github.tmldude.blocksouls.game.player.boon.BoonTier;
import io.github.tmldude.blocksouls.game.player.relic.RelicOffers;
import io.github.tmldude.blocksouls.game.run.DirectorHooks;

/**
 * Siege objectives (PLAN2 §3b): a third of sieges roll one — hold a supply drop's zone, carry a volatile core to
 * camp, run down a fleeing zombie, keep a generator alive through the charge. Success pays a relic to every
 * standing player. Live-only: a restart mid-siege cancels it.
 */
public final class Objectives {
	public static final String SUPPLY_DROP = "supply_drop";
	public static final String VOLATILE_CORE = "volatile_core";
	public static final String FLEEING_ZOMBIE = "fleeing_zombie";
	public static final String GENERATOR = "generator";
	private static final String CORE_TAG = "blocksouls_volatile_core";
	/** Players this close to a supply drop hold its zone. */
	private static final double ZONE_RADIUS = 6;
	/** Wave mobs this close to the generator hurt it. */
	private static final double GENERATOR_REACH = 3;

	/** The running objective. */
	static final class Running {
		final String id;
		final ServerLevel level;
		final BlockPos pos;
		final long startedAt;
		@Nullable UUID entity;
		int progress;
		int goal;

		Running(String id, ServerLevel level, BlockPos pos, long startedAt) {
			this.id = id;
			this.level = level;
			this.pos = pos;
			this.startedAt = startedAt;
		}
	}

	static @Nullable Running running;

	private Objectives() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Running r = running;
			if (r != null && r.id.equals(FLEEING_ZOMBIE) && entity.getUUID().equals(r.entity)) {
				end(entity.level().getServer(), true, "the zombie was run down");
			}
		});
	}

	private static DirectorConfig.Objectives config() {
		return Director.config().objectives;
	}

	public static @Nullable String current() {
		return running == null ? null : running.id;
	}

	/** Where the running objective was placed (gametests, {@code /bs director status}); null when none runs. */
	public static @Nullable BlockPos position() {
		Running r = running;
		return r == null ? null : r.pos;
	}

	/** Progress 0–1 for the HUD (time held, delivery, generator health). */
	public static float progress() {
		Running r = running;
		return r == null || r.goal <= 0 ? 0 : Math.min(1, Math.max(0, r.progress / (float) r.goal));
	}

	static Identifier idOf(String objective) {
		return CommonIds.id(CommonIds.GAME, objective);
	}

	static void onSiegeStarted(MinecraftServer server) {
		RandomSource random = server.overworld().getRandom();
		List<String> pool = config().pool;
		if (running != null || pool.isEmpty() || random.nextDouble() >= config().chance) {
			return;
		}
		start(server, pool.get(random.nextInt(pool.size())));
	}

	/** Starts {@code id} now (siege start, {@code /bs director objective}). Null on success, else why not. */
	public static @Nullable String start(MinecraftServer server, String id) {
		if (running != null) {
			return "An objective is already running: " + running.id;
		}
		ServerLevel level = DirectorHooks.level(server);
		RandomSource random = level.getRandom();
		BlockPos camp = Places.anchor(server, level);
		long now = Director.now(server);
		BlockPos pos = switch (id) {
			case GENERATOR -> Places.surfaceNear(level, camp.offset(0, 0, 5), 4, random);
			case SUPPLY_DROP, VOLATILE_CORE, FLEEING_ZOMBIE -> Places.ring(level, camp, config().distance, random);
			default -> null;
		};
		if (pos == null) {
			return "No spot for objective " + id;
		}
		Running r = new Running(id, level, pos, now);
		switch (id) {
			case SUPPLY_DROP -> {
				r.goal = Math.max(1, config().supplyDropSeconds);
				level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
			}
			case VOLATILE_CORE -> {
				r.goal = 1;
				ItemEntity core = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, core());
				core.setGlowingTag(true);
				core.setUnlimitedLifetime();
				level.addFreshEntity(core);
			}
			case FLEEING_ZOMBIE -> {
				r.goal = Math.max(1, config().fleeingSeconds);
				Zombie zombie = EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);
				if (zombie == null) {
					return "The fleeing zombie couldn't spawn";
				}
				zombie.setGlowingTag(true);
				zombie.setCustomName(Component.translatable("entity.blocksouls_game.fleeing_zombie"));
				zombie.setPersistenceRequired();
				r.entity = zombie.getUUID();
			}
			case GENERATOR -> {
				r.goal = Math.max(1, config().generatorHealth);
				r.progress = r.goal;
				level.setBlockAndUpdate(pos, Blocks.LODESTONE.defaultBlockState());
			}
			default -> {
				return "Unknown objective " + id;
			}
		}
		running = r;
		for (ServerPlayer player : DirectorHooks.activePlayers(server)) {
			player.sendSystemMessage(Component.translatable("message.blocksouls_game.director.objective." + id, pos.getX(), pos.getY(), pos.getZ()));
		}
		Director.log(server, DirectorLog.Kind.OBJECTIVE, "Objective " + id + " at " + pos.toShortString());
		Director.beat(server, "objective");
		DirectorEvents.OBJECTIVE_STARTED.invoker().onObjective(server, idOf(id), false);
		return null;
	}

	static ItemStack core() {
		ItemStack stack = new ItemStack(Items.HEART_OF_THE_SEA);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.blocksouls_game.volatile_core"));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(CORE_TAG, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	static boolean isCore(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(CORE_TAG, false);
	}

	static void second(MinecraftServer server, long now) {
		Running r = running;
		if (r == null) {
			return;
		}
		List<ServerPlayer> players = DirectorHooks.activePlayers(server);
		switch (r.id) {
			case SUPPLY_DROP -> {
				boolean held = players.stream().anyMatch(p -> p.level() == r.level && Places.distanceSq(r.pos, p.getX(), p.getZ()) <= ZONE_RADIUS * ZONE_RADIUS);
				if (held) {
					r.progress++;
				}
				r.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, r.pos.getX() + 0.5, r.pos.getY() + 1, r.pos.getZ() + 0.5, 6, ZONE_RADIUS / 2, 0.2,
						ZONE_RADIUS / 2, 0);
				if (r.progress >= r.goal) {
					end(server, true, "zone held");
				}
			}
			case VOLATILE_CORE -> {
				BlockPos camp = Places.anchor(server, r.level);
				double reach = config().deliverRadius * config().deliverRadius;
				for (ServerPlayer player : players) {
					if (player.level() == r.level && Places.distanceSq(camp, player.getX(), player.getZ()) <= reach && takeCore(player)) {
						r.progress = 1;
						end(server, true, player.getScoreboardName() + " delivered the core");
						return;
					}
				}
			}
			case FLEEING_ZOMBIE -> {
				Entity entity = r.entity == null ? null : r.level.getEntity(r.entity);
				r.progress = (int) (now - r.startedAt);
				if (entity == null || r.progress >= r.goal) {
					end(server, false, "the zombie got away");
					return;
				}
				if (entity instanceof Mob mob && mob.isAlive()) {
					flee(mob, players);
				}
			}
			case GENERATOR -> {
				for (UUID id : DirectorHooks.waveMobs(server)) {
					Entity mob = r.level.getEntity(id);
					if (mob != null && mob.isAlive() && mob.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(r.pos)) <= GENERATOR_REACH * GENERATOR_REACH) {
						r.progress--;
					}
				}
				r.level.sendParticles(ParticleTypes.END_ROD, r.pos.getX() + 0.5, r.pos.getY() + 1.2, r.pos.getZ() + 0.5, 2, 0.2, 0.3, 0.2, 0.01);
				if (r.progress <= 0) {
					r.level.removeBlock(r.pos, false);
					end(server, false, "the generator was destroyed");
				}
			}
			default -> end(server, false, "unknown");
		}
	}

	/** The zombie runs from the nearest player. */
	private static void flee(Mob mob, List<ServerPlayer> players) {
		ServerPlayer nearest = null;
		double best = Double.MAX_VALUE;
		for (ServerPlayer player : players) {
			double d = player.level() == mob.level() ? player.distanceToSqr(mob) : Double.MAX_VALUE;
			if (d < best) {
				best = d;
				nearest = player;
			}
		}
		mob.setTarget(null);
		if (nearest == null) {
			return;
		}
		double dx = mob.getX() - nearest.getX();
		double dz = mob.getZ() - nearest.getZ();
		double length = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
		mob.getNavigation().moveTo(mob.getX() + dx / length * 12, mob.getY(), mob.getZ() + dz / length * 12, 1 + Director.config().objectives.fleeingSpeed * 4);
	}

	private static boolean takeCore(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (isCore(inventory.getItem(slot))) {
				inventory.removeItemNoUpdate(slot);
				return true;
			}
		}
		return false;
	}

	/** The siege ended: the generator succeeds if it stood through a completed charge; anything else unfinished fails. */
	static void onSiegeEnded(MinecraftServer server, boolean completed) {
		Running r = running;
		if (r == null) {
			return;
		}
		if (r.id.equals(GENERATOR) && completed) {
			r.level.removeBlock(r.pos, false);
			end(server, true, "the generator held");
		} else {
			cancel(server, completed ? "siege over" : "siege abandoned");
		}
	}

	static void cancel(MinecraftServer server, String reason) {
		if (running != null) {
			end(server, false, reason);
		}
	}

	private static void end(MinecraftServer server, boolean success, String reason) {
		Running r = running;
		if (r == null) {
			return;
		}
		running = null;
		if (r.id.equals(FLEEING_ZOMBIE) && r.entity != null) {
			Entity entity = r.level.getEntity(r.entity);
			if (entity != null && entity.isAlive()) {
				entity.discard();
			}
		}
		if (success) {
			BoonTier tier = BoonTier.byId(config().relicTier);
			for (ServerPlayer player : DirectorHooks.activePlayers(server)) {
				RelicOffers.dropRelic(player, tier);
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(Component.translatable("message.blocksouls_game.director.objective_" + (success ? "done" : "failed"),
					Component.translatable("objective.blocksouls_game." + r.id)));
		}
		Director.log(server, DirectorLog.Kind.OBJECTIVE, "Objective " + r.id + (success ? " completed: " : " failed: ") + reason);
		Director.beat(server, "objective ended");
		DirectorEvents.OBJECTIVE_ENDED.invoker().onObjective(server, idOf(r.id), success);
	}
}
