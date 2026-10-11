package io.github.tmldude.blocksouls.world.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import io.github.tmldude.blocksouls.world.enchant.ArrowEnchantments;

/** Bouncy (ricochet off blocks) and Grapple (pull the shooter to where it lands) for arrows and tridents. */
@Mixin(AbstractArrow.class)
abstract class AbstractArrowMixin extends Projectile {
	@Unique
	private int blocksouls_world$bounces;

	@Unique
	private boolean blocksouls_world$grappled;

	protected AbstractArrowMixin(EntityType<? extends Projectile> type, Level level) {
		super(type, level);
	}

	@Shadow
	public abstract ItemStack getWeaponItem();

	@Shadow
	protected abstract void setInGround(boolean inGround);

	@Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
	private void blocksouls_world$onHitBlock(BlockHitResult hit, CallbackInfo ci) {
		if (this.level().isClientSide()) {
			return;
		}
		ItemStack weapon = this.getWeaponItem();
		AbstractArrow self = (AbstractArrow) (Object) this;
		if (ArrowEnchantments.tryBounce(self, weapon, hit, this.blocksouls_world$bounces)) {
			this.blocksouls_world$bounces++;
			// The client may already think the arrow stuck; flip the synced flag so it hears "not in ground".
			this.setInGround(true);
			this.setInGround(false);
			ci.cancel();
			return;
		}
		if (!this.blocksouls_world$grappled && this.getOwner() instanceof LivingEntity owner) {
			this.blocksouls_world$grappled = ArrowEnchantments.tryGrapple(self, owner, weapon, hit);
		}
	}
}
