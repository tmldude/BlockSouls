package io.github.tmldude.blocksouls.guns.exotic;

import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.GunsConfig;
import io.github.tmldude.blocksouls.guns.sound.GunSounds;

/** Sniper: precision kills mark every hostile nearby; marked targets glow and take bonus damage from any gun. */
public final class LanternEye implements ExoticBehavior {
	@Override
	public Set<Identifier> tags() {
		return Set.of(SynergyTags.ON_PRECISION, SynergyTags.ON_KILL);
	}

	@Override
	public void onHit(ServerPlayer player, ItemStack stack, LivingEntity target, double damage, boolean precision, boolean killed, int magAfter, long now) {
		if (!precision || !killed) {
			return;
		}
		GunsConfig.Exotics cfg = BlockSoulsGuns.CONFIG.exotics;
		ServerLevel level = player.level();
		Vec3 at = target.position();
		int marked = 0;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(cfg.lanternEyeMarkRadius),
				e -> e != target && e != player && e.isAlive() && !(e instanceof ServerPlayer) && e.isPickable())) {
			if (other.getBoundingBox().distanceToSqr(at) <= cfg.lanternEyeMarkRadius * cfg.lanternEyeMarkRadius) {
				Exotics.mark(other, now + cfg.lanternEyeMarkTicks);
				Vec3 c = other.getBoundingBox().getCenter();
				level.sendParticles(ParticleTypes.GLOW, c.x, c.y + 0.5, c.z, 6, 0.3, 0.4, 0.3, 0.02);
				marked++;
			}
		}
		if (marked > 0) {
			level.playSound(null, at.x, at.y, at.z, GunSounds.EXOTIC_MARK, SoundSource.PLAYERS, 0.9F, 1.0F);
		}
	}
}
