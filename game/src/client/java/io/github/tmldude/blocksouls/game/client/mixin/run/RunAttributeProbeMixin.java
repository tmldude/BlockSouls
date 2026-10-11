package io.github.tmldude.blocksouls.game.client.mixin.run;

import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;

import io.github.tmldude.blocksouls.game.client.run.RunSkyEffects;

/** The camera's and sky's view of environment attributes: tint sky/fog at night and pull the fog in on fog nights. */
@Mixin(EnvironmentAttributeProbe.class)
public abstract class RunAttributeProbeMixin {
	@Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
	private void blocksouls$runSky(EnvironmentAttribute<?> attribute, float partialTicks, CallbackInfoReturnable<Object> cir) {
		Object value = cir.getReturnValue();
		if (attribute == EnvironmentAttributes.SKY_COLOR || attribute == EnvironmentAttributes.FOG_COLOR) {
			cir.setReturnValue(RunSkyEffects.tintColor((Vector3fc) value));
		} else if (attribute == EnvironmentAttributes.FOG_END_DISTANCE || attribute == EnvironmentAttributes.SKY_FOG_END_DISTANCE) {
			cir.setReturnValue(RunSkyEffects.fogEnd((Float) value));
		} else if (attribute == EnvironmentAttributes.FOG_START_DISTANCE) {
			cir.setReturnValue(RunSkyEffects.fogStart((Float) value));
		}
	}
}
