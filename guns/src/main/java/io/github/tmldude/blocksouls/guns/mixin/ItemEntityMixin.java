package io.github.tmldude.blocksouls.guns.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import io.github.tmldude.blocksouls.guns.item.GunsItems;
import io.github.tmldude.blocksouls.guns.sound.GunSounds;

/** Ammo picked up off the ground clinks (on top of vanilla's pickup pop). Sound only. */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
	@Inject(method = "playerTouch", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;onItemPickup(Lnet/minecraft/world/entity/item/ItemEntity;)V"))
	private void blocksouls_guns$ammoPickup(Player player, CallbackInfo ci) {
		Item item = ((ItemEntity) (Object) this).getItem().getItem();
		if (item == GunsItems.PRIMARY_AMMO || item == GunsItems.SPECIAL_AMMO) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), GunSounds.AMMO_PICKUP, SoundSource.PLAYERS, 0.6F,
					0.95F + player.getRandom().nextFloat() * 0.1F);
		}
	}
}
