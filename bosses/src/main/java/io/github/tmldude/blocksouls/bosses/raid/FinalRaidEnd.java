package io.github.tmldude.blocksouls.bosses.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import io.github.tmldude.blocksouls.bosses.BlockSoulsBosses;

/**
 * The final raid inside vanilla's End. Phase 1 is vanilla's own fight. When its dragon dies, instead of rising in
 * light it falls, and our dragon takes its place ({@link #vanillaDragonFell}, from {@code EnderDragonMixin}); vanilla's
 * fight then tracks ours, and only our dragon's death completes it (exit portal, egg, gateway). Endermen are kept out
 * of phases 2 and 3.
 */
public final class FinalRaidEnd {
	/** Set while we hand vanilla our dragon's death (the mixin lets that one through). */
	private static boolean completing;

	private FinalRaidEnd() {
	}

	public static boolean enabled() {
		return FinalRaids.CONFIG.replaceVanillaDragon && FinalRaids.TYPE != null;
	}

	/** True while {@link #onKilled} reports our dragon's death to vanilla. */
	public static boolean completing() {
		return completing;
	}

	static void init() {
		// Endermen are pacified while our dragon fights: they never hurt a player near an engaged final dragon.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(source.getEntity() instanceof Enderman) || !(entity instanceof Player) || !(entity.level() instanceof ServerLevel level)) {
				return true;
			}
			return level.getEntitiesOfClass(FinalDragon.class, entity.getBoundingBox().inflate(160), FinalDragon::isEngaged).isEmpty();
		});
	}

	/** Our dragon in this level, alive (the one vanilla's fight points at, else any loaded one). */
	public static @Nullable FinalDragon finalDragon(ServerLevel level, @Nullable UUID hint) {
		if (hint != null && level.getEntity(hint) instanceof FinalDragon dragon && dragon.isAlive()) {
			return dragon;
		}
		List<? extends FinalDragon> loaded = level.getEntities(FinalRaids.TYPE, Entity::isAlive);
		return loaded.isEmpty() ? null : loaded.getFirst();
	}

	/**
	 * The vanilla dragon just died (end of phase 1): it doesn't rise and burst, it falls. Our dragon tears out of it
	 * where it was and drops to the island; vanilla's fight now tracks ours.
	 */
	public static void vanillaDragonFell(ServerLevel level, EnderDragon vanilla, EnderDragonFight fight) {
		FinalRaidFight access = (FinalRaidFight) fight;
		FinalDragon existing = finalDragon(level, fight.dragonUUID());
		if (existing != null) {
			access.blocksouls$setDragonUuid(existing.getUUID());
			vanilla.discard();
			return;
		}
		FinalDragon dragon = FinalRaids.TYPE.create(level, EntitySpawnReason.EVENT);
		if (dragon == null) {
			return;
		}
		RaidArena arena = RaidArena.fromEnd(level, access.blocksouls$origin());
		dragon.setArena(arena, true);
		Vec3 at = vanilla.position();
		dragon.snapTo(at.x, at.y, at.z, vanilla.getYRot() + 180, 0);
		dragon.startFall();
		level.addFreshEntity(dragon);
		access.blocksouls$setDragonUuid(dragon.getUUID());
		// The leftover crystals shatter with the towers; vanilla's crystals would explode for 6.
		for (Vec3 top : arena.crystals()) {
			for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, new AABB(top, top).inflate(3))) {
				crystal.discard();
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 20f, 0.6f);
		vanilla.discard();
		BlockSoulsBosses.LOGGER.info("Final raid: the Ender Dragon fell; phase 2 begins");
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
			completing = true;
			try {
				fight.setDragonKilled(standIn);
			} finally {
				completing = false;
			}
		}
	}

	/** Endermen in the arena forget whoever they were angry at. */
	static void pacifyEndermen(ServerLevel level, FinalDragon dragon) {
		RaidArena arena = dragon.arena();
		double r = arena.scaled(FinalRaids.CONFIG.arenaRadius);
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
			crystals.add(new Vec3(home.getX() + 0.5 + Math.cos(a) * 14, home.getY(), home.getZ() + 0.5 + Math.sin(a) * 14));
		}
		List<BlockPos> plates = new ArrayList<>();
		int spots = RaidArena.plateSpotCount();
		for (int i = 0; i < spots; i++) {
			double a = RaidArena.plateAngle(i, spots);
			plates.add(home.offset((int) Math.round(Math.cos(a) * 9), -1, (int) Math.round(Math.sin(a) * 9)));
		}
		Vec3 center = Vec3.atBottomCenterOf(home);
		return RaidArena.of(home, crystals, center.add(0, 3, 0), center, plates, 0.5);
	}
}
