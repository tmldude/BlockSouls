package io.github.tmldude.blocksouls.world.enchant;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/** Arrow / trident halves of Bouncy and Grapple, called from {@code AbstractArrowMixin} on the server. */
public final class ArrowEnchantments {
	/** Below this speed a ricochet would just dribble; let the arrow stick instead. */
	private static final double MIN_BOUNCE_SPEED = 0.2;

	private ArrowEnchantments() {
	}

	/** Ricochets the arrow if its weapon has Bouncy levels left. Returns whether it bounced. */
	public static boolean tryBounce(AbstractArrow arrow, ItemStack weapon, BlockHitResult hit, int bouncesSoFar) {
		int level = WackyEnchantments.level(arrow.level(), WackyEnchantments.BOUNCY, weapon);
		Vec3 v = arrow.getDeltaMovement();
		if (bouncesSoFar >= level || v.length() < MIN_BOUNCE_SPEED) {
			return false;
		}
		Direction face = hit.getDirection();
		double[] out = WackyMath.reflect(new double[] {v.x, v.y, v.z}, face.getAxis().ordinal(), BlockSoulsWorld.CONFIG.enchants.bounceDamping);
		Vec3 at = hit.getLocation().add(face.getStepX() * 0.05, face.getStepY() * 0.05, face.getStepZ() * 0.05);
		arrow.setPos(at.x, at.y, at.z);
		arrow.setDeltaMovement(out[0], out[1], out[2]);
		arrow.syncVelocity = true;
		return true;
	}

	/** Pulls the shooter toward where the arrow landed if the weapon has Grapple. */
	public static boolean tryGrapple(AbstractArrow arrow, LivingEntity owner, ItemStack weapon, BlockHitResult hit) {
		if (WackyEnchantments.level(arrow.level(), WackyEnchantments.GRAPPLE, weapon) <= 0) {
			return false;
		}
		return WackyEnchantments.grapple(owner, hit.getLocation());
	}
}
