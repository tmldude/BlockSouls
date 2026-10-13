package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;

import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * Skyrift's mobs (floor 3): the Storm Roc and Glider Matriarch mini-bosses (in the Director's Skyrift mini-boss pool)
 * and their pack gliders, and the siege twist: during a wave some wave mobs drop in from the floor's elevated siege
 * points (the shards) on slow falling instead of walking in from the ground.
 */
public final class SkyMobs {
	public static final EntityType<SkyPhantom> STORM_ROC = register("storm_roc", SkyPhantom.Role.ROC);
	public static final EntityType<SkyPhantom> GLIDER_MATRIARCH = register("glider_matriarch", SkyPhantom.Role.MATRIARCH);
	public static final EntityType<SkyPhantom> SKY_GLIDER = register("sky_glider", SkyPhantom.Role.GLIDER);
	/** Scoreboard tag: this wave mob has had its shard-drop roll. */
	static final String DROP_ROLLED = "blocksouls_world.shard_drop";
	private static final List<Mob> PENDING = new ArrayList<>();

	private SkyMobs() {
	}

	private static EntityType<SkyPhantom> register(String name, SkyPhantom.Role role) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, BlockSoulsWorld.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				FabricEntityType.Builder.<SkyPhantom>createMob((type, level) -> new SkyPhantom(type, level, role), MobCategory.MONSTER,
						mob -> mob.defaultAttributes(Monster::createMonsterAttributes))
						.sized(0.9F, 0.5F).eyeHeight(0.175F).clientTrackingRange(role == SkyPhantom.Role.GLIDER ? 8 : 12).build(key));
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.is(CommonTags.WAVE_MOBS) && level.dimension() == WorldFloors.levelKey()
					&& !mob.entityTags().contains(DROP_ROLLED)) {
				PENDING.add(mob);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SkyMobs::dropPending);
	}

	private static void dropPending(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Mob> batch = List.copyOf(PENDING);
		PENDING.clear();
		if (Runs.get(server).phase() != RunPhase.WAVE) {
			return;
		}
		for (Mob mob : batch) {
			if (!mob.isRemoved() && mob.addTag(DROP_ROLLED) && mob.level() instanceof ServerLevel level
					&& level.getRandom().nextDouble() < WorldFloors.config().shardSiegeChance) {
				dropFromShard(level, mob);
			}
		}
	}

	/** Moves a wave mob onto one of its floor's elevated siege points, with slow falling. False when there is none. */
	public static boolean dropFromShard(ServerLevel level, Mob mob) {
		PlacedFloor floor = WorldFloors.floorNear(level, mob.blockPosition());
		if (floor == null || !floor.tags().contains("skyrift")) {
			return false;
		}
		List<BlockPos> shards = elevatedSiegePoints(floor);
		if (shards.isEmpty()) {
			return false;
		}
		BlockPos at = shards.get(level.getRandom().nextInt(shards.size()));
		mob.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Math.max(20, WorldFloors.config().shardSiegeSlowFall), 0));
		level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.02);
		return true;
	}

	/** A floor's siege points on shards (raised above the island's ground). */
	public static List<BlockPos> elevatedSiegePoints(PlacedFloor floor) {
		return floor.slots().stream().filter(slot -> slot.kind() == FloorSlotKind.SIEGE_RING && slot.pos().getY() > floor.groundY() + 3)
				.map(FloorSlot::pos).toList();
	}
}
