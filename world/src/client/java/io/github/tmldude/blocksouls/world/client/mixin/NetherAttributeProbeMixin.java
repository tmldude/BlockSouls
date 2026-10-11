package io.github.tmldude.blocksouls.world.client.mixin;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.world.AshFog;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.WorldConfig;

/**
 * Ash fog color. In 26.3 the sky and the clear color come from the {@code FOG_COLOR} / {@code SKY_COLOR} environment
 * attributes, so tinting {@code FogData} alone leaves the Nether looking plain red; this grays those too.
 */
@Mixin(EnvironmentAttributeProbe.class)
abstract class NetherAttributeProbeMixin {
	@Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
	private void blocksouls_world$ashColor(EnvironmentAttribute<?> attribute, float partialTicks, CallbackInfoReturnable<Object> cir) {
		if (attribute != EnvironmentAttributes.FOG_COLOR && attribute != EnvironmentAttributes.SKY_COLOR) {
			return;
		}
		WorldConfig.AshFog cfg = BlockSoulsWorld.CONFIG.ashFog;
		ClientLevel level = Minecraft.getInstance().level;
		if (!cfg.enabled || level == null || level.dimension() != Level.NETHER || !(cir.getReturnValue() instanceof Vector3fc color)) {
			return;
		}
		float[] rgb = AshFog.blend(color.x(), color.y(), color.z(), cfg.ashBlend);
		cir.setReturnValue(new Vector3f(rgb[0], rgb[1], rgb[2]));
	}
}
