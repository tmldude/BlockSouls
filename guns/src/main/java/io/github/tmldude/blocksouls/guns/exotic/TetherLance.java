package io.github.tmldude.blocksouls.guns.exotic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.GunsConfig;
import io.github.tmldude.blocksouls.guns.shoot.GunServer;
import io.github.tmldude.blocksouls.guns.shoot.Hitscan;
import io.github.tmldude.blocksouls.guns.sound.GunSounds;

/**
 * Rocket launcher: the rocket bursts where it lands into a blast and a web tether that drags the two nearest mobs
 * together for a while. The rocket itself is the gun's hitscan round (a thick tracer).
 */
public final class TetherLance implements ExoticBehavior {
	@Override
	public Set<Identifier> tags() {
		return Set.of(SynergyTags.PULL, SynergyTags.EXPLOSION);
	}

	@Override
	public void onShot(ServerPlayer player, ItemStack stack, List<Hitscan.Trace> traces, int magAfter, long now) {
		if (traces.isEmpty()) {
			return;
		}
		GunsConfig.Exotics cfg = BlockSoulsGuns.CONFIG.exotics;
		ServerLevel level = player.level();
		Hitscan.Trace trace = traces.getFirst();
		Vec3 at = trace.path().getLast();
		Entity direct = trace.hit() != null ? trace.hit().getEntity() : null;

		List<LivingEntity> near = new ArrayList<>();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(cfg.tetherBlastRadius),
				e -> e != player && e.isAlive() && !(e instanceof ServerPlayer) && e.isPickable())) {
			if (entity.getBoundingBox().distanceToSqr(at) <= cfg.tetherBlastRadius * cfg.tetherBlastRadius) {
				if (entity != direct) {
					entity.hurtServer(level, GunServer.bullet(player), (float) cfg.tetherBlastDamage);
				}
				if (entity.isAlive()) {
					near.add(entity);
				}
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y + 0.3, at.z, 20, 1.0, 0.6, 1.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, GunSounds.EXOTIC_TETHER, SoundSource.PLAYERS, 1.0F, 1.0F);

		near.sort((a, b) -> Double.compare(a.getBoundingBox().distanceToSqr(at), b.getBoundingBox().distanceToSqr(at)));
		if (near.size() >= 2) {
			Exotics.tether(near.get(0), near.get(1), now + cfg.tetherTicks);
		}
	}
}
