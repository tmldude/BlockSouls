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

import io.github.tmldude.blocksouls.bosses.raid.FinalDragon;
import io.github.tmldude.blocksouls.bosses.raid.FinalRaidEnd;
import io.github.tmldude.blocksouls.bosses.raid.FinalRaidFight;

/**
 * Hands vanilla's dragon fight over to the final raid (see {@code docs/decisions/final-raid.md}). Phase 1 is vanilla's
 * fight, untouched. When its dragon is reported dead, the fight doesn't end: our dragon takes over (normally the fall
 * starts earlier, from {@code EnderDragonMixin}; this covers {@code /kill}). While ours lives, vanilla never makes a
 * new dragon and its boss bar stays hidden (ours draws its own); only our dragon's death ends the fight. Summoning a
 * new dragon with crystals is off: one final boss per run.
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

	@Override
	public BlockPos blocksouls$origin() {
		return origin;
	}

	@Inject(method = "createNewDragon", at = @At("HEAD"), cancellable = true)
	private void blocksouls$keepFinalDragon(CallbackInfoReturnable<@Nullable EnderDragon> cir) {
		if (FinalRaidEnd.enabled()) {
			FinalDragon ours = FinalRaidEnd.finalDragon(level, dragonUUID);
			if (ours != null) {
				blocksouls$setDragonUuid(ours.getUUID());
				cir.setReturnValue(null);
			}
		}
	}

	@Inject(method = "setDragonKilled", at = @At("HEAD"), cancellable = true)
	private void blocksouls$phaseTwo(EnderDragon dragon, CallbackInfo ci) {
		// Only the fight's own dragon (vanilla ignores others too).
		if (FinalRaidEnd.enabled() && !FinalRaidEnd.completing() && dragon.getUUID().equals(dragonUUID)) {
			FinalRaidEnd.vanillaDragonFell(level, dragon, (EnderDragonFight) (Object) this);
			ci.cancel();
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
		return visible && !(FinalRaidEnd.enabled() && dragonUUID != null && level.getEntity(dragonUUID) instanceof FinalDragon);
	}
}
