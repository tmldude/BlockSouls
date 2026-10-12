package io.github.tmldude.blocksouls.guns.exotic;

import java.util.List;
import java.util.Set;

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
import io.github.tmldude.blocksouls.guns.item.GunItem;
import io.github.tmldude.blocksouls.guns.item.GunRolls;
import io.github.tmldude.blocksouls.guns.shoot.GunServer;
import io.github.tmldude.blocksouls.guns.shoot.Hitscan;
import io.github.tmldude.blocksouls.guns.sound.GunSounds;

/** Hand cannon: the last round in the mag always ricochets to a second target near where it landed. */
public final class StrayVerdict implements ExoticBehavior {
	@Override
	public Set<Identifier> tags() {
		return Set.of(SynergyTags.RICOCHET, SynergyTags.KINETIC);
	}

	@Override
	public void onShot(ServerPlayer player, ItemStack stack, List<Hitscan.Trace> traces, int magAfter, long now) {
		if (magAfter != 0 || traces.isEmpty()) {
			return;
		}
		GunsConfig.Exotics cfg = BlockSoulsGuns.CONFIG.exotics;
		GunItem gun = GunItem.of(stack);
		if (gun == null) {
			return;
		}
		Hitscan.Trace first = traces.getFirst();
		Vec3 from = first.path().getLast();
		Entity exclude = first.hit() != null ? first.hit().getEntity() : null;
		ServerLevel level = player.level();
		LivingEntity second = nearest(level, player, from, exclude, cfg.strayVerdictRange);
		if (second == null) {
			return;
		}
		Vec3 to = second.getBoundingBox().getCenter();
		double damage = gun.stats().damage() * GunRolls.damageMult(GunRolls.rarity(stack)) * cfg.strayVerdictDamageMult;
		second.hurtServer(level, GunServer.bullet(player), (float) damage);
		GunServer.sendTracer(level, player, List.of(from, to));
		level.playSound(null, from.x, from.y, from.z, GunSounds.RICOCHET, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	static LivingEntity nearest(ServerLevel level, ServerPlayer shooter, Vec3 from, Entity exclude, double range) {
		LivingEntity best = null;
		double bestDistance = range * range;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range),
				e -> e != shooter && e != exclude && e.isAlive() && !(e instanceof ServerPlayer) && e.isPickable())) {
			double d = entity.getBoundingBox().getCenter().distanceToSqr(from);
			if (d < bestDistance) {
				bestDistance = d;
				best = entity;
			}
		}
		return best;
	}
}
