package io.github.tmldude.blocksouls.guns.exotic;

import java.util.List;
import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.GunsConfig;
import io.github.tmldude.blocksouls.guns.shoot.Hitscan;

/** Shotgun: every blast launches the shooter backward — damage and movement tech in one trigger pull. */
public final class Backdraft implements ExoticBehavior {
	@Override
	public Set<Identifier> tags() {
		return Set.of(SynergyTags.KINETIC);
	}

	@Override
	public void onShot(ServerPlayer player, ItemStack stack, List<Hitscan.Trace> traces, int magAfter, long now) {
		GunsConfig.Exotics cfg = BlockSoulsGuns.CONFIG.exotics;
		Vec3 look = player.getLookAngle();
		Vec3 push = look.scale(-cfg.backdraftLaunch).add(0, cfg.backdraftLift, 0);
		Vec3 current = player.getDeltaMovement();
		player.setDeltaMovement(current.x * 0.3 + push.x, Math.max(current.y, push.y), current.z * 0.3 + push.z);
		player.syncVelocity = true;
		player.fallDistance = 0;
		Vec3 at = player.position().add(look.scale(0.6));
		player.level().sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.8, at.z, 8, 0.2, 0.2, 0.2, 0.08);
	}
}
