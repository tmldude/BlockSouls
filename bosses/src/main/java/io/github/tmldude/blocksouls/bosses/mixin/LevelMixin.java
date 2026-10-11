package io.github.tmldude.blocksouls.bosses.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import io.github.tmldude.blocksouls.bosses.api.BossParts;

/** Boss weak-point parts show up in area queries, like vanilla's dragon parts (crosshair picking, bullets, sweeps). */
@Mixin(Level.class)
abstract class LevelMixin {
	@Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
			at = @At("RETURN"))
	private void blocksouls_bosses$addParts(@Nullable Entity except, AABB box, Predicate<? super Entity> selector,
			CallbackInfoReturnable<List<Entity>> cir) {
		BossParts.collect((Level) (Object) this, except, box, selector, cir.getReturnValue());
	}
}
