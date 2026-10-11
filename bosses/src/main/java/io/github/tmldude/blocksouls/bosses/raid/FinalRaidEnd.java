package io.github.tmldude.blocksouls.bosses.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import io.github.tmldude.blocksouls.bosses.BlockSoulsBosses;

/**
 * The final raid inside vanilla's End: spawning our dragon where vanilla would spawn its own (called from
 * {@code EnderDragonFightMixin}), telling vanilla's fight that "the dragon" died (exit portal, egg, gateway), and
 * keeping Endermen out of the fight.
 */
public final class FinalRaidEnd {
	private FinalRaidEnd() {
	}

	public static boolean enabled() {
		return FinalRaids.CONFIG.replaceVanillaDragon && FinalRaids.TYPE != null;
	}

	static void init() {
		// Endermen are pacified while the raid runs: they never hurt a player near an engaged final dragon.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(source.getEntity() instanceof Enderman) || !(entity instanceof Player) || !(entity.level() instanceof ServerLevel level)) {
				return true;
			}
			return level.getEntitiesOfClass(FinalDragon.class, entity.getBoundingBox().inflate(160), FinalDragon::isEngaged).isEmpty();
		});
	}

	/** Instead of a vanilla dragon: our dragon over the island (or nothing, if ours is already there). */
	public static void spawn(ServerLevel level, BlockPos origin, EnderDragonFight fight) {
		FinalRaidFight access = (FinalRaidFight) fight;
		UUID known = fight.dragonUUID();
		if (known != null && level.getEntity(known) instanceof FinalDragon existing && existing.isAlive()) {
			access.blocksouls$dragonSeen();
			return;
		}
		List<? extends FinalDragon> loaded = level.getEntities(FinalRaids.TYPE, Entity::isAlive);
		if (!loaded.isEmpty()) {
			access.blocksouls$setDragonUuid(loaded.getFirst().getUUID());
			return;
		}
		FinalDragon dragon = FinalRaids.TYPE.create(level, EntitySpawnReason.EVENT);
		if (dragon == null) {
			return;
		}
		dragon.setArena(RaidArena.fromEnd(level, origin), true);
		Vec3 home = Vec3.atBottomCenterOf(dragon.home());
		dragon.snapTo(home.x, home.y + 10, home.z, level.getRandom().nextFloat() * 360, 0);
		level.addFreshEntity(dragon);
		access.blocksouls$setDragonUuid(dragon.getUUID());
		BlockSoulsBosses.LOGGER.info("Final raid: spawned the final dragon over the main island");
	}

	/** Old worlds (or another mod) may still have a vanilla dragon: it gives way to ours. */
	public static void replaceVanillaDragons(ServerLevel level, BlockPos origin, EnderDragonFight fight) {
		List<? extends EnderDragon> dragons = level.getDragons();
		if (dragons.isEmpty()) {
			return;
		}
		for (EnderDragon dragon : new ArrayList<>(dragons)) {
			dragon.discard();
		}
		spawn(level, origin, fight);
	}

	/** Our dragon kept ticking: vanilla's "haven't seen the dragon" timer restarts. */
	static void seen(ServerLevel level, FinalDragon dragon) {
		EnderDragonFight fight = level.getDragonFight();
		if (fight != null && dragon.getUUID().equals(fight.dragonUUID())) {
			((FinalRaidFight) fight).blocksouls$dragonSeen();
		}
	}

	/** Our dragon died: vanilla's fight ends exactly as if its dragon had (exit portal, egg on first kill, gateway). */
	static void onKilled(ServerLevel level, FinalDragon dragon) {
		EnderDragonFight fight = level.getDragonFight();
		if (fight == null) {
			return;
		}
		((FinalRaidFight) fight).blocksouls$setDragonUuid(dragon.getUUID());
		// setDragonKilled only reads the UUID: a stand-in that never enters the world carries ours.
		EnderDragon standIn = EntityTypes.ENDER_DRAGON.create(level, EntitySpawnReason.EVENT);
		if (standIn != null) {
			standIn.setUUID(dragon.getUUID());
			fight.setDragonKilled(standIn);
		}
	}

	/** Endermen in the arena forget whoever they were angry at. */
	static void pacifyEndermen(ServerLevel level, FinalDragon dragon) {
		RaidArena arena = dragon.arena();
		double r = FinalRaids.CONFIG.arenaRadius;
		AABB box = new AABB(arena.center().getX() - r, arena.floorY() - FinalRaids.CONFIG.arenaBelow, arena.center().getZ() - r,
				arena.center().getX() + r, arena.floorY() + FinalRaids.CONFIG.arenaAbove, arena.center().getZ() + r);
		for (Enderman enderman : level.getEntitiesOfClass(Enderman.class, box)) {
			if (enderman.getTarget() instanceof Player || enderman.isAngry()) {
				enderman.setTarget(null);
				enderman.stopBeingAngry();
			}
		}
	}

	/** An arena around {@code home} for a dragon spawned outside the End (spawn egg, {@code /bs boss spawn}). */
	static RaidArena arenaAround(BlockPos home) {
		List<Vec3> crystals = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI * 2 / 8;
			crystals.add(new Vec3(home.getX() + 0.5 + Math.cos(a) * 14, home.getY() + 6, home.getZ() + 0.5 + Math.sin(a) * 14));
		}
		List<BlockPos> plates = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double a = Math.PI / 4 + (i % 2) * Math.PI + (i / 2) * Math.PI / 2;
			plates.add(home.offset((int) Math.round(Math.cos(a) * 9), -1, (int) Math.round(Math.sin(a) * 9)));
		}
		Vec3 center = Vec3.atBottomCenterOf(home);
		return RaidArena.of(home, crystals, center.add(0, 3, 0), center, plates);
	}
}
