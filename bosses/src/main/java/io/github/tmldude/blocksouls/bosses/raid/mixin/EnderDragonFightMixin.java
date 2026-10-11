package io.github.tmldude.blocksouls.bosses.raid.mixin;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;

import io.github.tmldude.blocksouls.bosses.raid.FinalRaidEnd;
import io.github.tmldude.blocksouls.bosses.raid.FinalRaidFight;

/**
 * Swaps the Ender Dragon for the final raid's dragon in vanilla's own fight bookkeeping (see
 * {@code docs/decisions/final-raid.md}): wherever vanilla would create its dragon, ours is spawned instead; the vanilla
 * boss bar stays hidden (ours draws its own); summoning a new dragon with crystals is off (one final boss per run).
 * Everything else (exit portal, egg, gateways) still runs vanilla's code when our dragon dies.
 */
@Mixin(EnderDragonFight.class)
abstract class EnderDragonFightMixin implements FinalRaidFight {
	@Shadow
	private ServerLevel level;
	@Shadow
	private BlockPos origin;
	@Shadow
	private @Nullable UUID dragonUUID;
	@Shadow
	private int ticksSinceDragonSeen;

	@Override
	public void blocksouls$setDragonUuid(UUID uuid) {
		this.dragonUUID = uuid;
		this.ticksSinceDragonSeen = 0;
		((EnderDragonFight) (Object) this).setDirty();
	}

	@Override
	public void blocksouls$dragonSeen() {
		this.ticksSinceDragonSeen = 0;
	}

	@Inject(method = "createNewDragon", at = @At("HEAD"), cancellable = true)
	private void blocksouls$spawnFinalDragon(CallbackInfoReturnable<@Nullable EnderDragon> cir) {
		if (FinalRaidEnd.enabled()) {
			FinalRaidEnd.spawn(level, origin, (EnderDragonFight) (Object) this);
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "tryRespawn", at = @At("HEAD"), cancellable = true)
	private void blocksouls$noResummon(CallbackInfo ci) {
		if (FinalRaidEnd.enabled()) {
			ci.cancel();
		}
	}

	@ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerBossEvent;setVisible(Z)V"))
	private boolean blocksouls$hideVanillaBar(boolean visible) {
		return visible && !FinalRaidEnd.enabled();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void blocksouls$replaceVanillaDragon(CallbackInfo ci) {
		if (FinalRaidEnd.enabled() && level.getGameTime() % 20 == 0) {
			FinalRaidEnd.replaceVanillaDragons(level, origin, (EnderDragonFight) (Object) this);
		}
	}
}
