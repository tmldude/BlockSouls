package io.github.tmldude.blocksouls.guns.exotic;

import java.util.Set;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.GunsConfig;
import io.github.tmldude.blocksouls.guns.item.GunComponents;
import io.github.tmldude.blocksouls.guns.sound.GunSounds;

/**
 * Auto rifle: hits build heat, heat ramps the fire rate and sets targets alight; at full heat the gun vents and
 * can't fire for a moment. Heat lives on the item ({@link KindlingState}) so the HUD sees it.
 */
public final class KindlingEngine implements ExoticBehavior {
	@Override
	public Set<Identifier> tags() {
		return Set.of(SynergyTags.FIRE);
	}

	public static KindlingState state(ItemStack stack) {
		return stack.getOrDefault(GunComponents.KINDLING, KindlingState.COLD);
	}

	/** Heat now, 0..100. */
	public static double heat(ItemStack stack, long now) {
		return state(stack).heatAt(now, BlockSoulsGuns.CONFIG.exotics.kindlingHeatDecayPerTick);
	}

	@Override
	public boolean canFire(ServerPlayer player, ItemStack stack, long now) {
		return !state(stack).venting(now);
	}

	@Override
	public double fireInterval(ServerPlayer player, ItemStack stack, long now, double base) {
		return base / KindlingState.rateMult(heat(stack, now), BlockSoulsGuns.CONFIG.exotics.kindlingMaxRateMult);
	}

	@Override
	public void onHit(ServerPlayer player, ItemStack stack, LivingEntity target, double damage, boolean precision, boolean killed, int magAfter, long now) {
		GunsConfig.Exotics cfg = BlockSoulsGuns.CONFIG.exotics;
		KindlingState before = state(stack);
		boolean wasVenting = before.venting(now);
		KindlingState after = before.heated(now, cfg.kindlingHeatPerHit, cfg.kindlingHeatDecayPerTick, cfg.kindlingVentTicks);
		stack.set(GunComponents.KINDLING, after);
		ServerLevel level = player.level();
		if (after.heat() >= cfg.kindlingIgniteHeat && target.isAlive()) {
			target.igniteForTicks(cfg.kindlingIgniteTicks);
		}
		if (!wasVenting && after.venting(now)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), GunSounds.EXOTIC_VENT, SoundSource.PLAYERS, 1.0F, 1.0F);
			var muzzle = player.getEyePosition().add(player.getLookAngle().scale(0.8));
			level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 16, 0.2, 0.2, 0.2, 0.05);
			level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 10, 0.2, 0.2, 0.2, 0.08);
		}
	}
}
